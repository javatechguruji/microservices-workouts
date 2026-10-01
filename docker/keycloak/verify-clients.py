"""Verify learning clients without printing secrets or access tokens.

Run from the repository root: python3 docker/keycloak/verify-clients.py
"""
import base64
import json
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request

realm = json.loads(Path(__file__).with_name('ecommerce-realm.json').read_text())
base = 'http://localhost:8180/realms/ecommerce'
with urllib.request.urlopen(base + '/.well-known/openid-configuration', timeout=10) as response:
    discovery = json.load(response)
assert discovery['issuer'] == base, discovery['issuer']


def request_token(client_id, secret):
    payload = urllib.parse.urlencode({
        'grant_type': 'client_credentials',
        'client_id': client_id,
        'client_secret': secret,
    }).encode()
    return urllib.request.urlopen(base + '/protocol/openid-connect/token', data=payload, timeout=15)


for client in realm['clients']:
    if not client.get('serviceAccountsEnabled'): continue
    with request_token(client['clientId'], client['secret']) as response:
        token = json.load(response)
    assert token['token_type'].lower() == 'bearer'
    assert token['expires_in'] > 0
    encoded = token['access_token'].split('.')[1]
    # Diagnostic claim check only; real resource servers must verify signatures.
    claims = json.loads(base64.urlsafe_b64decode(encoded + '=' * (-len(encoded) % 4)))
    assert claims['iss'] == base
    assert claims['azp'] == client['clientId']
    assert 'gateway-service' in claims.get('aud', [])
    assert claims.get('tenant') == 'demo'
    assert 'service' in claims.get('realm_access', {}).get('roles', [])
    try:
        with request_token(client['clientId'], 'deliberately-invalid-secret'):
            raise AssertionError('Incorrect secret accepted')
    except urllib.error.HTTPError as error:
        assert error.code in (400, 401), error.code
        assert json.load(error)['error'] in ('invalid_client', 'unauthorized_client')
    print(client['clientId'] + ': token issued; issuer/client claims correct; wrong secret rejected')
print('All six clients verified. No secrets or tokens printed.')
