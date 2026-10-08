#!/usr/bin/env python3
"""Verify real PKCE login and tenant/roles for every seeded human. Prints no tokens.
Uses the existing browser-flow helper without running its business-operation tests.
"""
import base64
import json
from pathlib import Path
import runpy

helper = runpy.run_path(str(Path(__file__).with_name('security-smoke-test.py')))
seed = helper['seed']
for user in seed['users']:
    if user.get('serviceAccountClientId'):
        continue
    token = helper['login'](user['username'])['access_token']
    part = token.split('.')[1]
    claims = json.loads(base64.urlsafe_b64decode(part + '=' * (-len(part) % 4)))
    expected_tenant = user['attributes']['tenant'][0]
    if (claims.get('preferred_username') != user['username']
            or claims.get('tenant') != expected_tenant
            or not set(user['realmRoles']).issubset(claims.get('realm_access', {}).get('roles', []))):
        raise RuntimeError('Incorrect user claims: ' + user['username'])
    print('Verified user login, roles and tenant: ' + user['username'], flush=True)
print('All four demo users and the public browser client verified.')
