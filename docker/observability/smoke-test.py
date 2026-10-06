#!/usr/bin/env python3
"""Send isolated synthetic OTLP signals and prove they can be queried. No business writes."""
import json
import secrets
import time
import urllib.parse
import urllib.request


def request(url, payload=None):
    data = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(url, data=data, headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=10) as response:
        body = response.read()
        return json.loads(body) if body and body[:1] in (b'{', b'[') else body.decode()


def eventually(label, check, timeout=90):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        try:
            result = check()
            if result:
                print('PASS:', label)
                return result
        except Exception as exc:
            last = type(exc).__name__ + ': ' + str(exc)
        time.sleep(2)
    raise AssertionError(f'{label} failed; last error: {last}')


def prom(query):
    return request('http://localhost:9090/api/v1/query?' + urllib.parse.urlencode({'query': query}))['data']['result']


def main():
    for name, url in [('Collector', 'http://localhost:13133/'), ('Prometheus', 'http://localhost:9090/-/ready'),
                      ('Loki', 'http://localhost:3100/ready'), ('Tempo', 'http://localhost:3200/ready'),
                      ('Grafana', 'http://localhost:3000/api/health'), ('Alertmanager', 'http://localhost:9093/-/ready')]:
        eventually(name + ' ready', lambda url=url: request(url))
    trace, span, run = secrets.token_hex(16), secrets.token_hex(8), secrets.token_hex(8)
    now = time.time_ns()
    resource = {'attributes': [{'key': 'service.name', 'value': {'stringValue': 'observability-smoke'}},
                               {'key': 'deployment.environment.name', 'value': {'stringValue': 'test'}}]}
    scope = {'name': 'commerce.observability.smoke'}
    payloads = {
        'traces': {'resourceSpans': [{'resource': resource, 'scopeSpans': [{'scope': scope, 'spans': [{
            'traceId': trace, 'spanId': span, 'name': 'observability-smoke', 'kind': 1,
            'startTimeUnixNano': str(now), 'endTimeUnixNano': str(now + 1000000)}]}]}]},
        'logs': {'resourceLogs': [{'resource': resource, 'scopeLogs': [{'scope': scope, 'logRecords': [{
            'timeUnixNano': str(now), 'observedTimeUnixNano': str(now), 'severityNumber': 9,
            'severityText': 'INFO', 'body': {'stringValue': 'observability smoke ' + run},
            'traceId': trace, 'spanId': span}]}]}]},
        'metrics': {'resourceMetrics': [{'resource': resource, 'scopeMetrics': [{'scope': scope, 'metrics': [{
            'name': 'observability.smoke', 'gauge': {'dataPoints': [{'timeUnixNano': str(now), 'asInt': '1',
            'attributes': [{'key': 'run', 'value': {'stringValue': run}}]}]}}]}]}]}}
    for signal, payload in payloads.items():
        result = request('http://localhost:4318/v1/' + signal, payload)
        assert not result.get('partialSuccess'), result
    eventually('metric query', lambda: prom('observability_smoke{run="' + run + '"}'))
    eventually('trace query', lambda: request('http://localhost:3200/api/traces/' + trace))
    query = '{service_name="observability-smoke"} |= "' + run + '"'
    logs = eventually('log query', lambda: request('http://localhost:3100/loki/api/v1/query_range?' + urllib.parse.urlencode({'query': query}))['data']['result'])
    assert trace in json.dumps(logs), 'Loki log lost its trace correlation'
    print('PASS: log/trace correlation; trace ID:', trace)


if __name__ == '__main__':
    main()
