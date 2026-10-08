#!/usr/bin/env python3
"""Resolve the existing stack, then restrict every published port to loopback."""
import json
import os
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[2]
config = json.loads(subprocess.check_output(
    ['docker', 'compose', '-f', str(root / 'docker-compose.yml'), 'config', '--format', 'json'],
    cwd=root, text=True))
for service in config['services'].values():
    for port in service.get('ports', []):
        port['host_ip'] = '127.0.0.1'
    service['logging'] = {'driver': 'json-file', 'options': {'max-size': '10m', 'max-file': '3'}}
# Kafka still advertises localhost for laptop clients; siblings use kafka:29092.
config['services']['kafka']['environment']['KAFKA_INTER_BROKER_LISTENER_NAME'] = 'PLAINTEXT_DOCKER'
config['services']['keycloak']['environment']['KC_BOOTSTRAP_ADMIN_PASSWORD'] = os.environ['KEYCLOAK_ADMIN_PASSWORD'].replace('$', '$$')
config['services']['grafana']['environment']['GF_SECURITY_ADMIN_PASSWORD'] = os.environ['GRAFANA_ADMIN_PASSWORD'].replace('$', '$$')
path = root / 'infra/aws-dev/compose.generated.json'
# Generated configuration includes credentials; only root should read it on EC2.
os.umask(0o077)
path.write_text(json.dumps(config, indent=2) + '\n')
path.chmod(0o600)
print('Generated loopback-only Compose configuration.')
