"""Shopping invariants through the real gateway. Creates learning records; no tokens printed."""
import importlib.util,json,time,uuid,urllib.request,urllib.parse,concurrent.futures
from pathlib import Path
spec=importlib.util.spec_from_file_location('security',Path(__file__).with_name('security-smoke-test.py'))
security=importlib.util.module_from_spec(spec);spec.loader.exec_module(security)
call,expect=security.call,security.expect
seed=security.seed

def machine(name):
    client=next(c for c in seed['clients'] if c['clientId']==name)
    body=urllib.parse.urlencode({'grant_type':'client_credentials','client_id':name,'client_secret':client['secret']}).encode()
    with urllib.request.urlopen(security.KC+'/protocol/openid-connect/token',data=body) as r:return json.load(r)['access_token']

def await_state(oid,token,state):
    for _ in range(40):
        status,data=call(f'/api/orders/{oid}/fulfillment',token)
        if status==200 and data.get('state')==state:return data
        time.sleep(.5)
    raise AssertionError(('checkout did not reach '+state,data))

if __name__=='__main__':
    customer=security.login('customer1')['access_token'];admin=security.login('admin1')['access_token']
    order_token=machine('order-service');pgs=machine('product-aggregator-service')
    catalog=expect(200,call('/api/products',customer),'personalized catalog')
    assert len(catalog['categories'])==5 and sum(len(c['products']) for c in catalog['categories'])==10
    expect(200,call('/api/products/images/ELEC-1.svg'),'public local product image')
    expect(403,call('/api/customers/customer2/preferences',customer),'private preference lookup denied to customers')
    expect(403,call('/api/products/quote',customer,'POST',{'items':[{'sku':'ELEC-1','quantity':1}]}),'internal quote denied to customer')
    expect(400,call('/api/customers/me',customer,'POST',{'email':'invalid','phone':'123','dob':'2999-01-01','preferences':['Invalid']}),'invalid registration rejected')
    expect(200,call('/api/customers/me',customer,'POST',{'email':'customer1@example.test','phone':'+1 312 555 0123','dob':'1995-06-15','preferences':['Electronics']}),'profile saved')
    cart={'idempotencyKey':str(uuid.uuid4()),'items':[{'sku':'BOOK-1','quantity':1}],'address':'123 Learning Lane, Chicago IL 60601','expectedAmount':.01}
    expect(409,call('/api/orders/checkout',customer,'POST',cart),'tampered/stale expected price rejected')
    cart['expectedAmount']=22
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        results=list(pool.map(lambda _:call('/api/orders/checkout',customer,'POST',cart),range(2)))
    assert all(r[0]==202 for r in results),results
    oid=results[0][1]['orderId'];assert results[1][1]['orderId']==oid
    print('PASS concurrent checkout returns one order',flush=True)
    completed=await_state(oid,customer,'CONFIRMED');assert completed['amount']==22 and len(completed['items'])==1
    expect(403,call('/api/orders/checkout',customer,'POST',{**cart,'idempotencyKey':str(uuid.uuid4()),'customerId':'customer2'}),'customer cannot order for another owner')
    expect(400,call('/api/orders/checkout',customer,'POST',{**cart,'idempotencyKey':str(uuid.uuid4()),'items':[{'sku':'BOOK-1','quantity':0}]}),'zero quantity rejected')
    payments=[]
    for _ in range(2):payments.append(expect(201,call('/payments',customer,'POST',{'orderId':oid,'amount':22}),'payment replay safe')['id'])
    assert payments[0]==payments[1]
    expect(409,call('/payments',customer,'POST',{'orderId':oid,'amount':1}),'payment replay cannot change amount')
    expect(409,call(f'/api/orders/{oid}/fulfillment/deliver',admin,'POST',{}),'cannot deliver before shipping')
    expect(403,call(f'/api/orders/{oid}/fulfillment/ship',customer,'POST',{}),'customer cannot ship')
    expect(409,call(f'/api/orders/{oid}/status',admin,'PATCH',{'status':'FAILED'}),'legacy status cannot bypass saga')
    expect(200,call(f'/api/orders/{oid}/fulfillment/ship',admin,'POST',{}),'admin ships confirmed order')
    expect(200,call(f'/api/orders/{oid}/fulfillment/deliver',admin,'POST',{}),'admin delivers shipped order')
    expect(200,call(f'/api/orders/{oid}/fulfillment/deliver',admin,'POST',{}),'delivery retry idempotent')
    stock=expect(200,call('/api/inventory/ELEC-2',order_token),'read real stock')['available']
    rid=1000000000+int(time.time())
    # One valid line plus an invalid SKU must roll back all stock updates.
    expect(409,call('/api/inventory/reservations',order_token,'POST',{'orderId':rid,'items':[{'sku':'ELEC-2','quantity':1},{'sku':'ZZZZ-1','quantity':1}]}),'multi-line reservation rolls back on failure')
    assert call('/api/inventory/ELEC-2',order_token)[1]['available']==stock
    body={'orderId':rid,'items':[{'sku':'ELEC-2','quantity':1}]}
    expect(200,call('/api/inventory/reservations',order_token,'POST',body),'reserve stock')
    expect(200,call('/api/inventory/reservations',order_token,'POST',body),'reservation retry idempotent')
    assert call('/api/inventory/ELEC-2',order_token)[1]['available']==stock-1
    expect(200,call(f'/api/inventory/reservations/{rid}/release',order_token,'POST',{}),'release unpaid reservation')
    assert call('/api/inventory/ELEC-2',order_token)[1]['available']==stock
    expect(409,call(f'/api/inventory/reservations/{rid}/commit',order_token,'POST',{}),'released reservation cannot commit')
    # Competing reservations cannot both claim more than the available stock.
    if 2 <= stock <= 196:
        quantity=stock//2+1
        requests=[{'orderId':rid+10+i,'items':[{'sku':'ELEC-2','quantity':quantity}]} for i in range(2)]
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            results=list(pool.map(lambda body:call('/api/inventory/reservations',order_token,'POST',body),requests))
        assert sorted(r[0] for r in results)==[200,409],results
        winner=next(requests[i]['orderId'] for i,r in enumerate(results) if r[0]==200)
        expect(200,call(f'/api/inventory/reservations/{winner}/release',order_token,'POST',{}),'release concurrent test winner')
        assert call('/api/inventory/ELEC-2',order_token)[1]['available']==stock
        print('PASS concurrent reservations cannot oversell',flush=True)
    available=call('/api/inventory/ELEC-1',order_token)[1]['available']
    if available<99:
        failure=expect(202,call('/api/orders/checkout',customer,'POST',{'idempotencyKey':str(uuid.uuid4()),'items':[{'sku':'ELEC-1','quantity':99}],'address':'123 Learning Lane, Chicago IL 60601','expectedAmount':6647.85}),'durable checkout accepts stock decision asynchronously')
        failed_id=failure['orderId'];await_state(failed_id,customer,'FAILED')
        expect(409,call('/payments',customer,'POST',{'orderId':failed_id,'amount':6647.85}),'failed stock checkout cannot take payment')
        assert call('/api/inventory/ELEC-1',order_token)[1]['available']==available
        print('PASS out-of-stock checkout fails without taking payment',flush=True)
    for _ in range(30):
        inbox=call('/notifications/me',customer)[1]
        if any(n['order_id']==oid and n['status']=='DELIVERED' for n in inbox):break
        time.sleep(.5)
    else:raise AssertionError('Kafka notification not observed')
    print('PASS Kafka delivery notification visible',flush=True)
    preferences=call('/api/customers/customer1/preferences',pgs)[1]
    assert 'Books' in preferences['categories'],preferences
    print('PASS purchase history enriches category preferences',flush=True)
    print('Commerce smoke checks passed; order',oid)
