#!/usr/bin/env python3
"""Cold backup/restore of this stack's volumes. Stop apps and Compose first."""
import argparse
import json
from pathlib import Path
import subprocess


def run(*args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)


def output(*args):
    return subprocess.check_output(args, text=True)


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('mode', choices=['backup', 'restore'])
parser.add_argument('directory', type=Path)
args = parser.parse_args()
folder = args.directory.resolve()
root = Path(__file__).resolve().parents[2]
if args.mode == 'backup':
    config = json.loads(output('docker', 'compose', '-f', str(root / 'docker-compose.yml'),
                               'config', '--format', 'json'))
    names = [v['name'] for v in config['volumes'].values()]
    # Refuse partial or live backups.
    for name in names:
        run('docker', 'volume', 'inspect', name, stdout=subprocess.DEVNULL)
        if output('docker', 'ps', '-q', '--filter', 'volume=' + name).strip():
            raise SystemExit('Stop all containers using volume ' + name + ' first.')
    folder.mkdir(mode=0o700, parents=True, exist_ok=False)
else:
    names = json.loads((folder / 'volumes.json').read_text())
    if not isinstance(names, list) or not names or len(names) != len(set(names)):
        raise SystemExit('Invalid volume manifest')
    import re
    for name in names:
        if not isinstance(name, str) or not re.fullmatch(r'[a-zA-Z0-9][a-zA-Z0-9_.-]+', name):
            raise SystemExit('Invalid volume name')
        if not (folder / (name + '.tar.gz')).is_file():
            raise SystemExit('Missing archive: ' + name)
    existing = set(output('docker', 'volume', 'ls', '-q').splitlines())
    if existing.intersection(names):
        raise SystemExit('Restore requires a fresh host with none of the destination volumes present.')
run('docker', 'pull', 'alpine:3.21')
for name in names:
    if args.mode == 'restore':
        run('docker', 'volume', 'create', name, stdout=subprocess.DEVNULL)
    run('docker', 'run', '--rm', '--network', 'none',
        '-v', name + ':/volume' + (':ro' if args.mode == 'backup' else ''),
        '-v', str(folder) + ':/backup' + (':ro' if args.mode == 'restore' else ''),
        'alpine:3.21', 'tar', '-C', '/volume',
        '-czpf' if args.mode == 'backup' else '-xzpf',
        '/backup/' + name + '.tar.gz', *(['.'] if args.mode == 'backup' else []))
    print(args.mode + ': ' + name, flush=True)
if args.mode == 'backup':
    (folder / 'volumes.json').write_text(json.dumps(names, indent=2) + '\n')
print('Complete. Archives contain credentials and application data; keep them private.')
