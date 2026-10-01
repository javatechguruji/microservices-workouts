import test from 'node:test';
import assert from 'node:assert/strict';
import { gatewayRequest, validateRequest } from './api.js';

test('rejects external and traversal paths before accessing a token', async () => {
  for (const path of [
    'https://evil.test/api/orders',
    '//evil.test',
    '/api/../admin',
    '/api/\\evil',
    '/api/%2e%2e/admin',
  ]) {
    await assert.rejects(
      gatewayRequest({ path, getToken: () => assert.fail('Must not read token') })
    );
  }
  assert.throws(() => validateRequest('/api/orders', 'POST', 'not json'));
});
test('uses refreshed access token and forwards intentional spoof headers for gateway tests', async () => {
  let refreshed = false;
  const result = await gatewayRequest({
    path: '/api/orders/security/me',
    spoof: true,
    getToken: async () => {
      refreshed = true;
      return 'refreshed-token';
    },
    fetcher: async (path, options) => {
      assert.ok(refreshed);
      assert.equal(path, '/api/orders/security/me');
      assert.equal(options.headers.Authorization, 'Bearer refreshed-token');
      assert.equal(options.headers['X-Auth-Username'], 'admin1');
      assert.equal(options.redirect, 'error');
      return new Response(JSON.stringify({ username: 'customer1' }), { status: 200 });
    },
  });
  assert.equal(result.data.username, 'customer1');
});
test('preserves denied responses instead of presenting authorization failures as success', async () => {
  const result = await gatewayRequest({
    path: '/api/inventory/SKU-1',
    getToken: async () => 'token',
    fetcher: async () => new Response('Forbidden', { status: 403 }),
  });
  assert.equal(result.status, 403);
  assert.equal(result.ok, false);
  assert.equal(result.data, 'Forbidden');
});
