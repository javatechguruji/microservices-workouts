"""Set existing local human passwords to usernames; leave service secrets unchanged.
Run from the workspace root. KEYCLOAK_ADMIN_PASSWORD supplies the current console
password (default: admin). Does not weaken or replace realm password policies.
"""
import json
import os
from urllib.request import Request, urlopen
from urllib.parse import urlencode

base = 'http://localhost:8180'
admin_user = os.getenv('KEYCLOAK_ADMIN_USER', 'admin')

def token(password):
    body = urlencode({'grant_type': 'password', 'client_id': 'admin-cli',
                      'username': admin_user, 'password': password}).encode()
    with urlopen(base + '/realms/master/protocol/openid-connect/token', data=body, timeout=15) as response:
        return json.load(response)['access_token']

access = token(os.getenv('KEYCLOAK_ADMIN_PASSWORD', 'admin'))

def call(method, path, body=None):
    request = Request(base + '/admin/realms/' + path, method=method,
                      headers={'Authorization': 'Bearer ' + access, 'Content-Type': 'application/json'},
                      data=json.dumps(body).encode() if body is not None else None)
    with urlopen(request, timeout=20) as response:
        raw = response.read()
        return json.loads(raw) if raw else None

first = 0
while True:
    users = call('GET', 'ecommerce/users?' + urlencode({'first': first, 'max': 100}))
    if not users:
        break
    for user in users:
        if user.get('serviceAccountClientId') or user['username'].startswith('service-account-'):
            continue
        call('PUT', 'ecommerce/users/' + user['id'] + '/reset-password',
             {'type': 'password', 'value': user['username'], 'temporary': False})
        print('Updated application user:', user['username'], flush=True)
    first += len(users)

admins = call('GET', 'master/users?' + urlencode({'username': admin_user, 'exact': 'true'}))
if len(admins) != 1:
    raise RuntimeError('Expected one matching Keycloak console administrator')
call('PUT', 'master/users/' + admins[0]['id'] + '/reset-password',
     {'type': 'password', 'value': admin_user, 'temporary': False})
token(admin_user)
print('Console administrator updated; new console login verified.')
