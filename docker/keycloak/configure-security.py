"""Reconcile the learning security configuration without deleting the realm/data.
Run from repo root: python3 docker/keycloak/configure-security.py
Existing human passwords are not reset. New users receive the seed password.
"""
import json
import os
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.parse import urlencode, quote
from urllib.error import HTTPError

base = 'http://localhost:8180'
seed = json.loads(Path(__file__).with_name('ecommerce-realm.json').read_text())
body = urlencode({'grant_type':'password','client_id':'admin-cli','username':os.getenv('KEYCLOAK_ADMIN_USER','admin'),
                  'password':os.getenv('KEYCLOAK_ADMIN_PASSWORD','admin')}).encode()
with urlopen(base+'/realms/master/protocol/openid-connect/token',data=body,timeout=15) as r:
    admin_token=json.load(r)['access_token']
api=base+'/admin/realms/ecommerce'
def call(method,path,data=None):
    req=Request(api+path,data=json.dumps(data).encode() if data is not None else None,method=method,
                headers={'Authorization':'Bearer '+admin_token,'Content-Type':'application/json'})
    with urlopen(req,timeout=20) as r:
        raw=r.read()
        return json.loads(raw) if raw else None

# Tenant is an authorization attribute: users can view it but only admins may edit it.
profile=call('GET','/users/profile')
attributes=profile.setdefault('attributes',[])
attributes[:]=[a for a in attributes if a.get('name') != 'tenant']
attributes.append({'name':'tenant','displayName':'Tenant','multivalued':False,
                   'permissions':{'view':['admin','user'],'edit':['admin']}})
call('PUT','/users/profile',profile)

call('PUT','',{'registrationAllowed':True})
roles={r['name']:r for r in call('GET','/roles')}
for role in seed['roles']['realm']:
    if role['name'] not in roles:
        call('POST','/roles',{'name':role['name']})
roles={r['name']:r for r in call('GET','/roles')}
for role in seed['roles']['realm']:
    if role.get('composite'):
        call('POST','/roles/'+quote(role['name'])+'/composites', [roles[n] for n in role['composites']['realm']])

for client in seed['clients']:
    matches=call('GET','/clients?'+urlencode({'clientId':client['clientId']}))
    if matches:
        cid=matches[0]['id']
        # Update settings and mappers on the existing client, retaining its UUID.
        call('PUT','/clients/'+cid,client)
    else:
        call('POST','/clients',client)
        cid=call('GET','/clients?'+urlencode({'clientId':client['clientId']}))[0]['id']
    if client.get('serviceAccountsEnabled'):
        account=call('GET','/clients/'+cid+'/service-account-user')
        desired=next(u for u in seed['users'] if u.get('serviceAccountClientId')==client['clientId'])
        call('PUT','/users/'+account['id'],{'attributes':desired['attributes']})
        call('POST','/users/'+account['id']+'/role-mappings/realm',[roles[n] for n in desired['realmRoles']])
        # Realm default roles are for human registration, never machine accounts.
        direct=call('GET','/users/'+account['id']+'/role-mappings/realm')
        human_defaults=[r for r in direct if r['name'] in ('default-roles-ecommerce','customer','admin')]
        if human_defaults: call('DELETE','/users/'+account['id']+'/role-mappings/realm',human_defaults)
    print('Configured client:',client['clientId'])

for user in seed['users']:
    if user.get('serviceAccountClientId'): continue
    matches=call('GET','/users?'+urlencode({'username':user['username'],'exact':'true'}))
    if not matches:
        call('POST','/users',user)
        matches=call('GET','/users?'+urlencode({'username':user['username'],'exact':'true'}))
    uid=matches[0]['id']
    update={k:user[k] for k in ('username','email','firstName','lastName','emailVerified','attributes','enabled')}
    update['requiredActions']=[]
    call('PUT','/users/'+uid,update)
    call('POST','/users/'+uid+'/role-mappings/realm',[roles[n] for n in user['realmRoles']])
    if 'admin' in user['realmRoles']:
        direct=call('GET','/users/'+uid+'/role-mappings/realm')
        defaults=[r for r in direct if r['name']=='default-roles-ecommerce']
        if defaults: call('DELETE','/users/'+uid+'/role-mappings/realm',defaults)
    print('Configured user:',user['username'])
print('Learning clients, roles, users and mappers synchronized; existing data retained.')

# New public registrations receive only the customer composite role.
default_role=call('GET','/roles/default-roles-ecommerce')
call('POST','/roles/'+default_role['name']+'/composites',[roles['customer']])

# Retire the renamed client without deleting its stored configuration.
for retired in call('GET','/clients?'+urlencode({'clientId':'product-service'})):
    call('PUT','/clients/'+retired['id'],{'enabled':False})
