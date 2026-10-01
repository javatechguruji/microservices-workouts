import test from 'node:test';
import assert from 'node:assert/strict';
import { defaultModule, moduleForRoute, canEnterModule, canonicalRoute } from './routing.js';

test('login lands in the correct module; unsupported roles never become customers', () => {
  assert.equal(defaultModule(['customer']), 'customer');
  assert.equal(defaultModule(['admin']), 'admin');
  assert.equal(defaultModule(['customer', 'admin']), 'admin');
  assert.equal(defaultModule(['service']), null);
  assert.equal(defaultModule([]), null);
});
test('explicit module URLs require that module role', () => {
  for (const route of [
    '/admin/dashboard',
    '/admin/orders/new',
    '/admin/orders/42',
    '/admin/customers',
  ]) {
    assert.equal(moduleForRoute(route), 'admin');
    assert.equal(canEnterModule(['customer'], moduleForRoute(route)), false);
    assert.equal(canEnterModule(['admin'], moduleForRoute(route)), true);
  }
  assert.equal(canEnterModule(['admin'], 'customer'), false);
  assert.equal(canEnterModule(['customer'], 'customer'), true);
  assert.equal(canEnterModule(['customer', 'admin'], 'customer'), true);
  assert.equal(canEnterModule(['service'], 'admin'), false);
  assert.equal(moduleForRoute('/administrator/orders'), null);
});
test('old bookmarks redirect without granting extra module access', () => {
  assert.equal(canonicalRoute('/', ['customer']), '/customer/dashboard');
  assert.equal(canonicalRoute('/', ['admin']), '/admin/dashboard');
  assert.equal(canonicalRoute('/orders/42', ['customer']), '/customer/orders/42');
  assert.equal(
    canonicalRoute('/orders?customer=customer1', ['admin']),
    '/admin/orders?customer=customer1'
  );
  const destination = canonicalRoute('/customers', ['customer']);
  assert.equal(destination, '/admin/customers');
  assert.equal(canEnterModule(['customer'], moduleForRoute(destination)), false);
  assert.equal(canonicalRoute('/admin/orders', ['customer']), '/admin/orders');
});
