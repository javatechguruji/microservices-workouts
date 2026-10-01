"""Real PKCE + gateway security checks. Requires six local apps and Keycloak.
Creates learning orders/payments; does not delete data. No tokens are printed.
"""
import base64
import hashlib
from html.parser import HTMLParser
import http.cookiejar
import json
from pathlib import Path
import secrets
import time
import urllib.error
import urllib.parse
import urllib.request

KC='http://localhost:8180/realms/ecommerce'
GW='http://localhost:9100'
REDIRECT='http://localhost:5173/'
seed=json.loads(Path(__file__).with_name('ecommerce-realm.json').read_text())

class LoginForm(HTMLParser):
    def __init__(self): super().__init__(); self.action=None
    def handle_starttag(self,tag,attrs):
        values=dict(attrs)
        if tag=='form' and values.get('id')=='kc-form-login': self.action=values['action']

class CaptureCode(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,req,fp,code,msg,headers,newurl):
        if newurl.startswith(REDIRECT): return None
        return super().redirect_request(req,fp,code,msg,headers,newurl)

def login(username):
    user=next(u for u in seed['users'] if u['username']==username)
    verifier=secrets.token_urlsafe(32); state=secrets.token_urlsafe(24)
    challenge=base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('=')
    params=urllib.parse.urlencode({'client_id':'security-demo-ui','redirect_uri':REDIRECT,'response_type':'code',
        'scope':'openid profile','state':state,'code_challenge':challenge,'code_challenge_method':'S256','prompt':'login'})
    jar=http.cookiejar.CookieJar()
    opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar),CaptureCode())
    with opener.open(KC+'/protocol/openid-connect/auth?'+params,timeout=15) as r: html=r.read().decode()
    # Browsers treat http://localhost as a trustworthy context; Python's cookie jar
    # otherwise withholds Keycloak's Secure session cookies in this loopback-only test.
    for cookie in jar:
        if cookie.domain in ('localhost', 'localhost.local'):
            cookie.secure=False
    form=LoginForm();form.feed(html)
    assert form.action,'Keycloak login form not found'
    body=urllib.parse.urlencode({'username':username,'password':user['credentials'][0]['value'],'credentialId':''}).encode()
    try:
        with opener.open(form.action,data=body,timeout=15) as r:
            import re
            page=r.read().decode()
            title=re.search(r'<h1[^>]*>(.*?)</h1>',page,re.S)
            message=re.sub('<[^>]+>','',title.group(1)).strip() if title else 'unknown login page'
            raise AssertionError('Login did not finish for '+username+': '+message)
    except urllib.error.HTTPError as e:
        assert e.code==302, 'Login failed for '+username
        args=urllib.parse.parse_qs(urllib.parse.urlparse(e.headers['Location']).query)
    assert args['state'][0]==state
    body=urllib.parse.urlencode({'grant_type':'authorization_code','client_id':'security-demo-ui','redirect_uri':REDIRECT,
        'code':args['code'][0],'code_verifier':verifier}).encode()
    with urllib.request.urlopen(KC+'/protocol/openid-connect/token',data=body,timeout=15) as r: return json.load(r)

def call(path,token=None,method='GET',body=None,headers=None):
    h=dict(headers or {})
    if token: h['Authorization']='Bearer '+token
    if body is not None: h['Content-Type']='application/json'
    req=urllib.request.Request(GW+path,method=method,headers=h,data=json.dumps(body).encode() if body is not None else None)
    try:
        with urllib.request.urlopen(req,timeout=20) as r: code=r.status; raw=r.read()
    except urllib.error.HTTPError as e: code=e.code;raw=e.read()
    try: data=json.loads(raw)
    except (ValueError,UnicodeDecodeError): data=raw.decode()
    return code,data

def expect(code,result,label):
    assert result[0]==code,(label,result[0],result[1])
    print('PASS',label,code,flush=True)
    return result[1]

if __name__=='__main__':
    tokens={u:login(u) for u in ['customer1','customer2','admin1','othercustomer']}
    c1=tokens['customer1']['access_token'];c2=tokens['customer2']['access_token'];admin=tokens['admin1']['access_token']
    expect(401,call('/api/orders/security/me'),'missing JWT')
    expect(401,call('/api/orders/security/me',headers={'X-Auth-Subject':'x','X-Auth-Username':'admin1','X-Auth-Tenant':'demo','X-Auth-Roles':'admin'}),'headers cannot replace gateway authentication')
    expect(401,call('/api/orders/security/me',c1[:-8]+'invalid!'),'tampered JWT')
    expect(401,call('/api/orders/security/me',tokens['customer1']['id_token']),'ID token wrong gateway audience')
    who=expect(200,call('/api/orders/security/me',c1,headers={'X-Auth-Username':'admin1','X-Auth-Roles':'admin','X-Auth-Tenant':'other'}),'spoofed headers overwritten')
    assert who['username']=='customer1' and who['roles']==['customer'] and who['tenant']=='demo',who
    expect(403,call('/api/orders/security/admin',c1),'customer denied admin role')
    expect(200,call('/api/orders/security/admin',admin),'admin role accepted')
    own=expect(201,call('/api/orders',c1,'POST',{'customerId':'customer1','amount':25}),'create own order')
    oid=own['id']
    expect(403,call('/api/orders',c1,'POST',{'customerId':'customer2','amount':25}),'cannot choose another owner')
    expect(200,call('/api/orders/'+str(oid),c1),'owner read allowed')
    expect(403,call('/api/orders/'+str(oid),c2),'another customer denied')
    expect(200,call('/api/orders/'+str(oid),admin),'same-tenant admin read-any')
    other=expect(201,call('/api/orders',tokens['othercustomer']['access_token'],'POST',{'customerId':'othercustomer','amount':25}),'other tenant create')
    expect(403,call('/api/orders/'+str(other['id']),admin),'admin cannot cross tenant')
    expect(200,call('/api/products/SKU-1',c1),'customer product read')
    expect(403,call('/api/inventory/SKU-1',c1),'customer lacks inventory permission')
    result=expect(200,call('/api/orders/'+str(oid)+'/inventory/SKU-1',c1),'order uses own machine token through gateway')
    assert result['inventory']['calledAs']=='service-account-order-service',result
    expect(403,call('/api/orders/'+str(oid)+'/inventory/SKU-1',c2),'ownership checked before machine call')
    expect(403,call('/api/inventory/SKU-1/adjust',c1,'POST',{}),'customer inventory adjustment denied')
    expect(200,call('/api/inventory/SKU-1/adjust',admin,'POST',{}),'admin inventory permission and role')
    payment=expect(201,call('/payments',c1,'POST',{'orderId':oid,'amount':25}),'payment checks order via gateway machine token')
    expect(403,call('/payments/'+str(payment['id']),c2),'payment owner restriction')
    expect(200,call('/payments/'+str(payment['id']),admin),'admin payment read-any')
    expect(403,call('/notifications',c1,'POST',{'orderId':oid,'status':'SUCCESS'}),'customer notifications denied')
    expect(202,call('/notifications',admin,'POST',{'orderId':oid,'status':'SUCCESS'}),'admin notification permission')
    print('All security smoke tests passed. Created learning orders',oid,other['id'],'and payment',payment['id'])
