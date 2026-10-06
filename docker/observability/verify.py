#!/usr/bin/env python3
"""Live acceptance: temporarily start nine built JARs, exercise flows, verify telemetry, stop owned processes.
Requires shared-infra and `mvn test package`. Creates learning commerce records with --commerce.
Logs/results go to /tmp/commerce-observability-validation. Never stops pre-existing applications.
"""
import argparse
import base64
import importlib.util
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import sys
import time
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
OUT = Path('/tmp/commerce-observability-validation')
SERVICES = ['gateway-service', 'order-service', 'payment-service', 'notification-service',
            'product-aggregator-service', 'inventory-service', 'customer-service',
            'product-discount-service', 'rating-service']
spec = importlib.util.spec_from_file_location('smoke', Path(__file__).with_name('smoke-test.py'))
smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(smoke)


def get(url, headers=None):
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers or {}), timeout=15) as response:
        return response.read()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--commerce', action='store_true', help='Also run the existing shopping integration checks (creates learning records).')
    args = parser.parse_args()
    OUT.mkdir(exist_ok=True)
    processes = []
    try:
        subprocess.run(['docker', 'compose', 'exec', '-T', 'prometheus', 'promtool', 'check', 'config', '/etc/prometheus/prometheus.yml'], cwd=ROOT, check=True)
        subprocess.run(['docker', 'compose', 'run', '--rm', '--no-deps', '-v', str(ROOT / 'docker/observability/rules-test.yml') + ':/checks/rules-test.yml:ro', '--entrypoint', 'promtool', 'prometheus', 'test', 'rules', '/checks/rules-test.yml'], cwd=ROOT, check=True)
        smoke.main()
        for job in ['postgres', 'redis', 'kafka', 'otel-collector']:
            smoke.eventually(job + ' exporter up', lambda job=job: smoke.prom('up{job="' + job + '"} == 1'))
        for expr in ['pg_up == 1', 'redis_up == 1', 'kafka_brokers > 0']:
            smoke.eventually(expr, lambda expr=expr: smoke.prom(expr))
        for i, service in enumerate(SERVICES):
            with socket.socket() as sock:
                if sock.connect_ex(('127.0.0.1', 9100 + i)) == 0:
                    raise RuntimeError(f'{service} port is already occupied; stop it first. No existing process was stopped.')
        for service in SERVICES:
            module = ROOT / service
            jar = module / 'target' / f'{service}-0.0.1-SNAPSHOT.jar'
            agent = module / 'target/otel/opentelemetry-javaagent.jar'
            assert jar.exists() and agent.exists(), 'Run mvn test package first: ' + service
            log = (OUT / (service + '.log')).open('w')
            cmd = ['java', '-Xms64m', '-Xmx256m', '-javaagent:' + str(agent),
                   '-Dotel.javaagent.configuration-file=' + str(module / 'src/main/resources/otel.properties'),
                   '-jar', str(jar)]
            proc = subprocess.Popen(cmd, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
            log.close()
            processes.append(proc)
        for i, service in enumerate(SERVICES):
            def ready(i=i):
                if processes[i].poll() is not None:
                    raise RuntimeError('Application exited; inspect ' + str(OUT))
                return json.loads(get(f'http://localhost:{9100+i}/actuator/health'))['status'] == 'UP'
            smoke.eventually(service + ' healthy with agent', ready, timeout=150)
        trace = secrets.token_hex(16)
        get('http://localhost:9100/api/products/images/BOOK-1.svg',
            {'traceparent': '00-' + trace + '-' + secrets.token_hex(8) + '-01'})
        data = smoke.eventually('gateway → PGS shared trace', lambda: smoke.request('http://localhost:3200/api/traces/' + trace))
        def names(data):
            found = set()
            for batch in data.get('batches', data.get('resourceSpans', [])):
                for attr in batch['resource']['attributes']:
                    if attr['key'] == 'service.name':
                        found.add(attr['value']['stringValue'])
            return found
        smoke.eventually('both gateway and PGS spans', lambda: {'gateway-service', 'product-aggregator-service'} <= names(smoke.request('http://localhost:3200/api/traces/' + trace)))
        if args.commerce:
            result = subprocess.run([sys.executable, 'docker/keycloak/commerce-smoke-test.py'], cwd=ROOT,
                                    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=180)
            (OUT / 'commerce-test.log').write_text(result.stdout)
            if result.returncode:
                raise AssertionError('Commerce smoke failed; inspect ' + str(OUT / 'commerce-test.log'))
            print('PASS: checkout, idempotency, inventory, payment and Kafka integration', flush=True)
        for service in SERVICES:
            smoke.eventually(service + ' JVM metrics', lambda service=service: smoke.prom('jvm_memory_used_bytes{service_name="' + service + '"}'))
            query = '{service_name="' + service + '"}'
            smoke.eventually(service + ' centralized logs', lambda query=query: smoke.request('http://localhost:3100/loki/api/v1/query_range?' + urllib.parse.urlencode({'query': query}))['data']['result'])
        for service in ['order-service', 'payment-service']:
            smoke.eventually(service + ' backlog metrics', lambda service=service: smoke.prom('commerce_outbox_pending{service_name="' + service + '"}'))
        smoke.eventually('HTTP RED histogram', lambda: smoke.prom('http_server_request_duration_seconds_bucket{service_name="gateway-service"}'))
        smoke.eventually('Snapshot freshness metric', lambda: smoke.prom('commerce_metrics_last_success_seconds{service_name="order-service"} > 0'))
        if args.commerce:
            def kafka_trace():
                query = '{ resource.service.name = "order-service" && span.messaging.system = "kafka" }'
                search = smoke.request('http://localhost:3200/api/search?' + urllib.parse.urlencode({'q': query, 'limit': '20'}))
                for match in search.get('traces', []):
                    data = smoke.request('http://localhost:3200/api/traces/' + match['traceID'])
                    services = names(data)
                    if 'order-service' in services and ('notification-service' in services or 'customer-service' in services):
                        return match['traceID']
                return False
            kafka_id = smoke.eventually('Kafka producer/consumer share a trace', kafka_trace)
            print('Kafka trace ID:', kafka_id, flush=True)
            query = '{service_name="order-service"} |= "Checkout attempt failed"'
            logs = smoke.eventually('Real business log trace context', lambda: smoke.request('http://localhost:3100/loki/api/v1/query_range?' + urllib.parse.urlencode({'query': query}))['data']['result'])
            assert 'trace_id' in json.dumps(logs)
        # Verify provisioned UI artifacts over authenticated Grafana API.
        password = os.environ.get('GRAFANA_ADMIN_PASSWORD', 'WorkoutsGrafana-Local-2026!')
        headers = {'Authorization': 'Basic ' + base64.b64encode(('admin:' + password).encode()).decode()}
        dashboard = json.loads(get('http://localhost:3000/api/dashboards/uid/commerce-overview', headers))
        assert len(dashboard['dashboard']['panels']) >= 10
        for panel in dashboard['dashboard']['panels']:
            for target in panel['targets']:
                query = target['expr'].replace('$service', '.*').replace('$environment', '.*')
                # A successful query may legitimately be empty (e.g. no 5xx).
                smoke.prom(query)
        print('PASS: every dashboard PromQL query executes', flush=True)
        sources = json.loads(get('http://localhost:3000/api/datasources', headers))
        assert {'prometheus', 'loki', 'tempo'} <= {s['uid'] for s in sources}
        for source in ['prometheus', 'loki', 'tempo']:
            health = json.loads(get('http://localhost:3000/api/datasources/uid/' + source + '/health', headers))
            assert health['status'] == 'OK', health
        print('PASS: Grafana dashboard and all datasource health checks', flush=True)
        (OUT / 'result.json').write_text(json.dumps({'status': 'passed', 'services': SERVICES, 'http_trace_id': trace, 'commerce_test': args.commerce}, indent=2))
    finally:
        for proc in processes:
            if proc.poll() is None:
                proc.terminate()
        for proc in processes:
            try:
                proc.wait(timeout=20)
            except subprocess.TimeoutExpired:
                proc.kill()
                proc.wait()
        print('Stopped only the application processes started by this check; shared-infra stays running.', flush=True)


if __name__ == '__main__':
    main()
