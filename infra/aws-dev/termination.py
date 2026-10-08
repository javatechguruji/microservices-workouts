#!/usr/bin/env python3
"""Shared cleanup/report helper; use 03-terminate/01-terminate.sh [--verify-only].
Targets the VM/root disks and, for older deployments, any recorded Elastic IP.
Keeps a private local cleanup record so retries still know which disks to verify.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time

BASE = Path(__file__).resolve().parent
TF = BASE / 'terraform'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--verify-only', action='store_true', help='Read-only verification; no prompt or deletion')
args = parser.parse_args()


def command(*argv):
    result = subprocess.run(argv, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip() or 'Command failed')
    return result.stdout


def terraform(*argv):
    return command('terraform', '-chdir=' + str(TF), *argv)


state = json.loads(terraform('show', '-json'))
values = state.get('values', {})
resources = {r['address']: r['values'] for r in values.get('root_module', {}).get('resources', [])}
outputs = values.get('outputs', {})
record_path = BASE / 'cleanup-record.json'
saved = json.loads(record_path.read_text()) if record_path.exists() else {}
instance = resources.get('aws_instance.dev', {}).get('id') or outputs.get('instance_id', {}).get('value') or saved.get('instance')
region = outputs.get('aws_region', {}).get('value') or saved.get('region')
if not instance or not region:
    sys.exit('No deployment identity found. Keep your Terraform state and cleanup-record.json.')
matching = saved if saved.get('instance') == instance and saved.get('region') == region else {}
eip = resources.get('aws_eip.dev', {})
allocation = eip.get('allocation_id') or eip.get('id') or matching.get('allocation')
volumes = sorted({v['volume_id'] for v in resources.get('aws_instance.dev', {}).get('root_block_device', []) if v.get('volume_id')} | set(matching.get('volumes', [])))
if (allocation and not allocation.startswith('eipalloc-')) or not volumes:
    sys.exit('Cannot identify the exact managed Elastic IP/root disks. Cleanup is NOT verified; preserve Terraform state and inspect the deployment.')


def aws(service, action, *argv):
    return json.loads(command('aws', service, action, *argv, '--region', region, '--output', 'json') or '{}')


identity = aws('sts', 'get-caller-identity')
if matching.get('account') and identity['Account'] != matching['account']:
    sys.exit('AWS account differs from the cleanup record; refusing cleanup/report.')
# Resource ARNs in state prevent a wrong-account empty result from looking like successful cleanup.
for address in ('aws_instance.dev', 'aws_eip.dev', 'aws_vpc.dev'):
    arn = resources.get(address, {}).get('arn', '')
    if arn and arn.split(':')[4] and arn.split(':')[4] != identity['Account']:
        sys.exit('AWS account differs from Terraform resource account; select the original AWS_PROFILE.')


def vm_state():
    data = aws('ec2', 'describe-instances', '--filters', 'Name=instance-id,Values=' + instance)
    entries = [i for r in data['Reservations'] for i in r['Instances']]
    return entries[0]['State']['Name'] if entries else 'absent'


def addresses():
    if not allocation:
        return []
    return aws('ec2', 'describe-addresses', '--filters', 'Name=allocation-id,Values=' + allocation)['Addresses']


def disk(volume):
    items = aws('ec2', 'describe-volumes', '--filters', 'Name=volume-id,Values=' + volume)['Volumes']
    return items[0] if items else None


def poll(check, label):
    for attempt in range(24):
        if check():
            return
        if attempt == 0 or attempt % 6 == 0:
            print('Waiting for ' + label + '...', flush=True)
        time.sleep(5)
    raise RuntimeError('Timed out waiting for ' + label + '; rerun to retry.')


def release_ip():
    for attempt in range(24):
        entries = addresses()
        if not entries:
            return
        address = entries[0]
        if address.get('AssociationId') or address.get('NetworkInterfaceId') or address.get('InstanceId'):
            if address.get('InstanceId') != instance:
                raise RuntimeError('Elastic IP is attached to another resource; refusing release.')
            time.sleep(5)
            continue
        aws('ec2', 'release-address', '--allocation-id', allocation)
        poll(lambda: not addresses(), 'Elastic IP release')
        return
    raise RuntimeError('Elastic IP still attached after termination; rerun to retry release.')


errors = []
if not args.verify_only:
    print(f"Account: {identity['Account']} | Region: {region} | VM: {instance}")
    print('Permanently delete this VM/root disks and release any recorded legacy Elastic IP. No backup will be taken.')
    print('Root disks: ' + ', '.join(volumes) + '; legacy Elastic IP allocation: ' + (allocation or 'none'))
    if input('Type ' + instance + ' to confirm cleanup: ').strip() != instance:
        sys.exit('Cancelled. No changes made.')
    # Save IDs BEFORE deletion. This is cleanup metadata, not a database backup.
    os.umask(0o077)
    record = dict(instance=instance, region=region, account=identity['Account'], allocation=allocation, volumes=volumes)
    temp = record_path.with_suffix('.tmp')
    temp.write_text(json.dumps(record, indent=2) + '\n')
    temp.replace(record_path)
    try:
        entries = addresses()
        if entries and (entries[0].get('AssociationId') or entries[0].get('NetworkInterfaceId')) and entries[0].get('InstanceId') != instance:
            raise RuntimeError('Managed Elastic IP moved to another resource; refusing cleanup.')
        current = vm_state()
        if current in ('running', 'stopped', 'stopping'):
            subprocess.run(['bash', str(BASE / 'daily.sh'), 'stop'], check=True)
            if terraform('output', '-raw', 'instance_id').strip() != instance:
                raise RuntimeError('Terraform target changed during shutdown; refusing termination.')
            aws('ec2', 'terminate-instances', '--instance-ids', instance)
        elif current not in ('terminated', 'absent', 'shutting-down'):
            raise RuntimeError('Cannot cleanly terminate VM in state ' + current)
        poll(lambda: vm_state() in ('terminated', 'absent'), 'VM termination')
    except Exception as error:
        errors.append('VM cleanup: ' + str(error))
    # Only remove disks/IP after independently confirming VM termination.
    try:
        gone = vm_state() in ('terminated', 'absent')
    except Exception as error:
        gone = False
        errors.append('VM verification: ' + str(error))
    if gone:
        for volume in volumes:
            try:
                info = disk(volume)
                if info:
                    if info.get('Attachments'):
                        raise RuntimeError('Disk still attached; refusing deletion.')
                    if info['State'] == 'available':
                        aws('ec2', 'delete-volume', '--volume-id', volume)
                    elif info['State'] != 'deleting':
                        raise RuntimeError('Unexpected disk state: ' + info['State'])
                    poll(lambda: disk(volume) is None, 'root disk deletion ' + volume)
            except Exception as error:
                errors.append(volume + ': ' + str(error))
        try:
            release_ip()
        except Exception as error:
            errors.append('Elastic IP cleanup: ' + str(error))

print('\nCLEANUP VERIFICATION — this Terraform deployment only')
passed = True
checks = [('EC2 ' + instance, lambda: vm_state() in ('terminated', 'absent'), 'TERMINATED — no ongoing instance compute charge'),
          *[('Root EBS ' + v, lambda v=v: disk(v) is None, 'DELETED — no ongoing volume storage charge') for v in volumes],
          ('Automatic public IPv4', lambda: vm_state() in ('terminated', 'absent'), 'RELEASED with VM — no ongoing automatic IP charge')]
if allocation:
    checks.append(('Legacy Elastic IP ' + allocation, lambda: not addresses(), 'RELEASED — no ongoing charge for this IP'))
for label, check, success in checks:
    try:
        ok = check()
        print(('SUCCESS | ' if ok else 'INCOMPLETE | ') + label + ' | ' + (success if ok else 'Still exists; charges may continue'))
        passed = passed and ok
    except Exception as error:
        passed = False
        print('UNVERIFIED | ' + label + ' | ' + str(error))
print('\nRetained by design (tracked in Terraform, not deleted by this script):')
for label in ['VPC', 'Subnet', 'Internet gateway', 'Route table and association', 'Security group', 'SSH key-pair registration']:
    print('RETAINED | ' + label + ' | No standalone hourly charge in this configuration')
print('LOCAL | Terraform state, SSH private key, settings and cleanup record | No AWS charge')
print('Scope excludes other deployments, manually added disks/snapshots, commitments and previously incurred charges.')
for error in errors:
    print('DETAIL | ' + error)
if passed:
    print('\nCleanup verified! No ongoing EC2, root EBS or public IPv4 charges for these verified resources.')
    print('Next: bash infra/aws-dev/04-recreate-after-termination/01-recreate-fresh.sh')
else:
    print('\nCleanup incomplete/unverified. Fix the reported issue and rerun the SAME termination script.')
    print('It skips deleted resources and retries remaining steps. Keep Terraform state and cleanup-record.json.')
    sys.exit(1)
