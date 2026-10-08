#!/usr/bin/env python3
"""Administrative CLI helper; see 01-create-sso-user.sh and the linked SSO guide."""
import json
import os
import subprocess
import sys
import time


def required(name):
    value = os.environ.get(name, '').strip()
    if not value:
        sys.exit('Set ' + name + ' first; see 01-create-sso-user.sh.')
    return value


region = required('WORKOUTS_SSO_REGION')
ec2_region = required('WORKOUTS_EC2_REGION')
email = required('WORKOUTS_SSO_EMAIL')
username = required('WORKOUTS_SSO_USERNAME')
if '@' not in email or email.endswith('@example.com'):
    sys.exit('Supply your real email address for password setup.')
name = 'WorkoutsDevInfra'
owner_tag = {'Key': 'ManagedBy', 'Value': 'workouts-dev-sso-bootstrap'}


def aws(service, action, *args):
    result = subprocess.run(['aws', service, action, *args, '--region', region,
        '--output', 'json', '--no-cli-pager'], capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(result.stderr.strip())
    return json.loads(result.stdout or '{}')


def wait(action, request_id, response_key):
    for _ in range(90):
        result = aws('sso-admin', action, '--instance-arn', instance,
                     ('--account-assignment-creation-request-id' if action == 'describe-account-assignment-creation-status'
                      else '--provision-permission-set-request-id'), request_id)[response_key]
        if result['Status'] == 'SUCCEEDED':
            return
        if result['Status'] == 'FAILED':
            raise RuntimeError(result.get('FailureReason', 'SSO provisioning failed'))
        time.sleep(5)
    raise RuntimeError('Provisioning timed out; check Identity Center status before retrying.')


account = aws('sts', 'get-caller-identity')['Account']
instances = aws('sso-admin', 'list-instances')['Instances']
instances = [i for i in instances if '/ssoins-' in i['InstanceArn'] and i.get('Status', 'ACTIVE') == 'ACTIVE']
if len(instances) != 1:
    sys.exit('Expected one active ORGANIZATION Identity Center instance in ' + region
             + '. Enable it in the Console first or correct WORKOUTS_SSO_REGION. See the guide.')
instance = instances[0]['InstanceArn']
store = instances[0]['IdentityStoreId']
print(f'Account: {account}; Identity Center region: {region}; EC2 region: {ec2_region}')
print(f'User: {username}; permission set: {name}; session duration: 1 hour')
print('Grants the listed EC2/VPC actions region-wide; this is not a single-project security boundary.')
if input('Type the AWS account ID to create/update this access: ').strip() != account:
    sys.exit('Cancelled. No changes made.')

# ListUsers filters accept JSON, avoiding shell interpolation of names/email.
users = aws('identitystore', 'list-users', '--identity-store-id', store,
    '--filters', json.dumps([{'AttributePath': 'UserName', 'AttributeValue': username}]))['Users']
if len(users) > 1:
    raise RuntimeError('Multiple matching users; refusing to guess.')
if users:
    if email not in [e['Value'] for e in users[0].get('Emails', [])]:
        raise RuntimeError('Existing username belongs to another email; choose another username.')
    user_id = users[0]['UserId']
else:
    user_id = aws('identitystore', 'create-user', '--identity-store-id', store,
        '--user-name', username, '--display-name', username,
        '--name', json.dumps({'GivenName': 'TechGuru', 'FamilyName': 'Developer'}),
        '--emails', json.dumps([{'Value': email, 'Type': 'work', 'Primary': True}]))['UserId']
print('User ready: ' + username)

permission = None
for arn in aws('sso-admin', 'list-permission-sets', '--instance-arn', instance)['PermissionSets']:
    item = aws('sso-admin', 'describe-permission-set', '--instance-arn', instance,
               '--permission-set-arn', arn)['PermissionSet']
    if item['Name'] == name:
        tags = aws('sso-admin', 'list-tags-for-resource', '--instance-arn', instance,
                   '--resource-arn', arn)['Tags']
        if owner_tag not in tags:
            raise RuntimeError('Permission-set name already exists outside this script; refusing to overwrite.')
        permission = arn
        break
if not permission:
    permission = aws('sso-admin', 'create-permission-set', '--instance-arn', instance,
        '--name', name, '--description', 'Regional EC2 and VPC access for the workouts development stack',
        '--session-duration', 'PT1H', '--tags', json.dumps([owner_tag]))['PermissionSet']['PermissionSetArn']
# Refuse silently keeping broader managed policies that someone attached separately.
for action, key in [('list-managed-policies-in-permission-set', 'AttachedManagedPolicies'),
                    ('list-customer-managed-policy-references-in-permission-set', 'CustomerManagedPolicyReferences')]:
    if aws('sso-admin', action, '--instance-arn', instance, '--permission-set-arn', permission)[key]:
        raise RuntimeError('Unexpected managed policies on permission set; review them before continuing.')
# Explicit operations used by this repository; no ec2:* or AdministratorAccess grant.
actions = '''RunInstances StartInstances StopInstances RebootInstances TerminateInstances
CreateVpc ModifyVpcAttribute DeleteVpc CreateSubnet ModifySubnetAttribute DeleteSubnet
CreateInternetGateway AttachInternetGateway DetachInternetGateway DeleteInternetGateway
CreateRouteTable DeleteRouteTable CreateRoute ReplaceRoute DeleteRoute
AssociateRouteTable DisassociateRouteTable ReplaceRouteTableAssociation
CreateSecurityGroup DeleteSecurityGroup AuthorizeSecurityGroupIngress AuthorizeSecurityGroupEgress
RevokeSecurityGroupIngress RevokeSecurityGroupEgress ModifySecurityGroupRules
ImportKeyPair DeleteKeyPair AllocateAddress AssociateAddress DisassociateAddress ReleaseAddress
CreateTags DeleteTags CreateVolume DeleteVolume ModifyVolume
ModifyInstanceAttribute ModifyInstanceMetadataOptions ModifyInstanceCreditSpecification'''.split()
policy = {'Version': '2012-10-17', 'Statement': [{
    'Sid': 'DevelopmentInfrastructureInOneRegion', 'Effect': 'Allow',
    'Action': ['ec2:Describe*'] + ['ec2:' + a for a in actions], 'Resource': '*',
    'Condition': {'StringEquals': {'aws:RequestedRegion': ec2_region}},
}]}
aws('sso-admin', 'put-inline-policy-to-permission-set', '--instance-arn', instance,
    '--permission-set-arn', permission, '--inline-policy', json.dumps(policy))
aws('sso-admin', 'update-permission-set', '--instance-arn', instance,
    '--permission-set-arn', permission, '--session-duration', 'PT1H')
assignments = aws('sso-admin', 'list-account-assignments', '--instance-arn', instance,
    '--permission-set-arn', permission, '--account-id', account)['AccountAssignments']
if not any(a['PrincipalType'] == 'USER' and a['PrincipalId'] == user_id for a in assignments):
    result = aws('sso-admin', 'create-account-assignment', '--instance-arn', instance,
        '--permission-set-arn', permission, '--principal-type', 'USER', '--principal-id', user_id,
        '--target-type', 'AWS_ACCOUNT', '--target-id', account)['AccountAssignmentCreationStatus']
    wait('describe-account-assignment-creation-status', result['RequestId'], 'AccountAssignmentCreationStatus')
# Reprovision updates to this account only (also needed on repeat runs).
result = aws('sso-admin', 'provision-permission-set', '--instance-arn', instance,
    '--permission-set-arn', permission, '--target-type', 'AWS_ACCOUNT',
    '--target-id', account)['PermissionSetProvisioningStatus']
wait('describe-permission-set-provisioning-status', result['RequestId'], 'PermissionSetProvisioningStatus')
print('User, permission set and AWS account assignment are ready. No access keys created.')
print('NEXT: Console password setup + MFA, then aws configure sso --profile workouts-dev.')
print('Choose role WorkoutsDevInfra and verify that your ARN is assumed-role, not root.')
