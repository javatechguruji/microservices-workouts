import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';
const realm = JSON.parse(
  readFileSync(new URL('../../docker/keycloak/ecommerce-realm.json', import.meta.url))
);
async function login(page, username) {
  await page.goto('/');
  await page.getByRole('button', { name: 'Sign in to your account' }).click();
  const user = realm.users.find((u) => u.username === username);
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(user.credentials[0].value);
  await page.locator('#kc-login').click();
  await expect(
    page.getByRole('heading', {
      name: username === 'admin1' ? 'Admin dashboard' : 'A little more you.',
      exact: true,
    })
  ).toBeVisible();
  await expect(page).toHaveURL(
    new RegExp(username === 'admin1' ? '#/admin/dashboard$' : '#/customer/dashboard$')
  );
}
async function create(page, customer) {
  await page.getByRole('navigation').getByRole('link', { name: 'Create order' }).click();
  await page.getByLabel('Customer username').fill(customer);
  await page.getByLabel('Delivery address').fill('123 Assisted Shopping Lane, Chicago IL 60601');
  await page.getByLabel('Quantity for Cotton throw', { exact: true }).fill('1');
  const created = page.waitForResponse(
    (r) => r.url().endsWith('/api/orders/checkout') && r.request().method() === 'POST'
  );
  await page.getByRole('button', { name: 'Pay now', exact: true }).click();
  const response = await created;
  expect(response.status()).toBe(202);
  const result = await response.json();
  await expect(page.getByText('Current step: Confirmed', { exact: true })).toBeVisible();
  return { id: result.orderId };
}
test('customer and administrator order workflows enforce owner and tenant boundaries', async ({
  page,
  browser,
}, testInfo) => {
  const customerModuleRequests = [];
  page.on('request', (request) => {
    if (request.url().includes('/src/modules/')) customerModuleRequests.push(request.url());
  });
  await login(page, 'customer1');
  await expect(
    page.getByRole('navigation').getByRole('link', { name: 'Customers', exact: true })
  ).toHaveCount(0);
  await page.getByRole('link', { name: 'Your profile', exact: true }).click();
  await page.getByLabel('Email', { exact: true }).fill('customer1@example.test');
  await page.getByLabel('Phone number').fill('+1 312 555 0123');
  await page.getByLabel('Date of birth').fill('1995-06-15');
  await page.getByLabel('Electronics', { exact: true }).check();
  await page.getByRole('button', { name: 'Save profile' }).click();
  await expect(page.getByRole('status')).toContainText('Profile saved');
  await page.getByRole('navigation').getByRole('link', { name: 'Shop', exact: true }).click();
  const card = page.locator('.product-card').filter({ hasText: 'Wireless headphones' });
  await expect(card.locator('del')).toHaveText('$79.00');
  await expect(card.locator('.price strong')).toHaveText('$67.15');
  await card.getByRole('button', { name: 'Increase Wireless headphones' }).click();
  await card.getByRole('button', { name: 'Add to cart', exact: true }).click();
  await page.getByRole('navigation').getByRole('link', { name: 'Your cart', exact: true }).click();
  await expect(page.locator('.total')).toContainText('$134.30');
  await page.getByLabel('Delivery address').fill('123 Learning Lane, Chicago, IL 60601');
  const checkout = page.waitForResponse(
    (r) => r.url().endsWith('/api/orders/checkout') && r.request().method() === 'POST'
  );
  await page.getByRole('button', { name: 'Pay now', exact: true }).click();
  const response = await checkout;
  expect(response.status()).toBe(202);
  const result = await response.json();
  const own = { id: result.orderId };
  await expect(page.getByText('Current step: Confirmed', { exact: true })).toBeVisible();
  const originalBody = response.request().postDataJSON();
  const replay = await page.evaluate(async (body) => {
    const { gatewayRequest } = await import('/src/api.js');
    const { accessToken } = await import('/src/auth.js');
    return gatewayRequest({
      path: '/api/orders/checkout',
      method: 'POST',
      body: JSON.stringify(body),
      getToken: accessToken,
    });
  }, originalBody);
  expect(replay.status).toBe(202);
  expect(replay.data.orderId).toBe(own.id);
  const mismatch = await page.evaluate(async (body) => {
    const { gatewayRequest } = await import('/src/api.js');
    const { accessToken } = await import('/src/auth.js');
    return gatewayRequest({
      path: '/api/orders/checkout',
      method: 'POST',
      body: JSON.stringify({ ...body, address: 'A different address for the same key' }),
      getToken: accessToken,
    });
  }, originalBody);
  expect(mismatch.status).toBe(409);
  await expect(page.getByRole('heading', { name: 'Manage order' })).toHaveCount(0);
  await page.getByRole('navigation').getByRole('link', { name: 'My orders' }).click();
  await page.getByLabel('Search orders').fill(String(own.id));
  await page.getByRole('link', { name: `View order ${own.id}`, exact: true }).click();
  await expect(page.getByRole('heading', { name: `Order #${own.id}` })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('heading', { name: `Order #${own.id}` })).toBeVisible();
  await expect(page.getByText('Current step: Confirmed', { exact: true })).toBeVisible();
  await page.getByRole('navigation').getByRole('link', { name: 'Shop', exact: true }).click();
  await expect(page.locator('.product-card')).toHaveCount(10);
  await page
    .locator('.product-card img')
    .evaluateAll((images) => images.forEach((i) => (i.loading = 'eager')));
  await expect
    .poll(() =>
      page
        .locator('.product-card img')
        .evaluateAll((images) => images.every((i) => i.complete && i.naturalWidth > 0))
    )
    .toBe(true);
  await page.screenshot({ path: testInfo.outputPath('customer-dashboard.png'), fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: testInfo.outputPath('shop-mobile.png'), fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.setViewportSize({ width: 1280, height: 900 });
  // The browser clock advances only to exercise the adapter refresh before the next API call.
  await page.clock.install();
  await page.clock.fastForward('06:00');
  const refresh = page.waitForResponse(
    (r) =>
      r.url().includes('/protocol/openid-connect/token') &&
      (r.request().postData() || '').includes('grant_type=refresh_token')
  );
  await page.getByRole('button', { name: 'Refresh workspace' }).click();
  expect((await refresh).status()).toBe(200);
  await page.clock.resume();
  await expect(
    page.getByRole('heading', { name: 'A little more you.', exact: true })
  ).toBeVisible();
  let createdForCustomer;
  for (const username of ['customer2', 'othercustomer', 'admin1']) {
    console.log('Checking workflow for', username);
    const context = await browser.newContext({ baseURL: 'http://localhost:5173' });
    try {
      const other = await context.newPage();
      other.setDefaultTimeout(15000);
      other.setDefaultNavigationTimeout(15000);
      await login(other, username);
      console.log('Signed in:', username);
      if (username !== 'admin1') {
        await other.getByRole('navigation').getByRole('link', { name: 'My orders' }).click();
        await expect(
          other.getByRole('link', { name: `View order ${own.id}`, exact: true })
        ).toHaveCount(0);
        console.log('Checking denied detail:', username);
        await other.goto(`/#/customer/orders/${own.id}`);
        await expect(other.getByRole('heading', { name: 'Order unavailable' })).toBeVisible();
        await expect(other.getByRole('alert')).toContainText('do not have access');
        console.log('Checking admin navigation:', username);
        await other.goto('/#/admin/customers');
        await expect(other.getByRole('heading', { name: 'Access restricted' })).toBeVisible();
        await other.goto('/#/admin/orders/new');
        await expect(other.getByRole('heading', { name: 'Access restricted' })).toBeVisible();
      } else {
        await other.getByRole('navigation').getByRole('link', { name: 'All orders' }).click();
        await other.getByLabel('Search orders').fill(String(own.id));
        await other.getByRole('link', { name: `View order ${own.id}`, exact: true }).click();
        await other.getByRole('button', { name: 'Ship order', exact: true }).click();
        await expect(other.getByText('Current step: Shipped', { exact: true })).toBeVisible();
        await other.getByRole('button', { name: 'Mark delivered', exact: true }).click();
        await expect(other.getByText('Current step: Delivered', { exact: true })).toBeVisible();
        createdForCustomer = await create(other, 'customer1');
        await other.getByRole('button', { name: 'Ship order', exact: true }).click();
        await expect(other.getByText('Current step: Shipped', { exact: true })).toBeVisible();
        await other
          .getByRole('navigation')
          .getByRole('link', { name: 'Customers', exact: true })
          .click();
        await expect(other.getByRole('cell', { name: 'customer1', exact: true })).toBeVisible();
        await other.getByRole('navigation').getByRole('link', { name: 'Dashboard' }).click();
        await expect(
          other.getByRole('heading', { name: 'Admin dashboard', exact: true })
        ).toBeVisible();
        await other.screenshot({
          path: testInfo.outputPath('admin-dashboard.png'),
          fullPage: true,
        });
        await other.setViewportSize({ width: 390, height: 844 });
        await other.screenshot({ path: testInfo.outputPath('admin-mobile.png'), fullPage: true });
        expect(await other.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
          true
        );
      }
    } finally {
      console.log('Closing test session:', username);
      await context.close();
      console.log('Closed:', username);
    }
  }
  await page.goto(`/#/customer/orders/${createdForCustomer.id}`);
  await expect(
    page.getByRole('heading', { name: `Order #${createdForCustomer.id}` })
  ).toBeVisible();
  await expect(page.locator('.detail-list .badge')).toHaveText('Shipped');
  expect(customerModuleRequests.some((url) => url.includes('/modules/customer/'))).toBe(true);
  expect(customerModuleRequests.some((url) => url.includes('/modules/admin/'))).toBe(false);
  await page.goto(`/#/orders/${createdForCustomer.id}`);
  await expect(page).toHaveURL(new RegExp(`#/customer/orders/${createdForCustomer.id}$`));
  await expect(
    page.getByRole('heading', { name: `Order #${createdForCustomer.id}` })
  ).toBeVisible();
  await page.getByRole('button', { name: 'Sign out', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Sign in to your account' })).toBeVisible();
});

test('new shoppers register and complete their profile', async ({ page }) => {
  const username = `shopper${Date.now()}`;
  await page.goto('/');
  await page.getByRole('button', { name: 'Create an account', exact: true }).click();
  await page.locator('#username').fill(username);
  await page.locator('#email').fill(`${username}@example.test`);
  await page.locator('#firstName').fill('Learning');
  await page.locator('#lastName').fill('Shopper');
  await page.locator('#password').fill('Workouts-NewShopper-2026!');
  await page.locator('#password-confirm').fill('Workouts-NewShopper-2026!');
  await page.locator('input[type=submit],button[type=submit]').click();
  await expect(
    page.getByRole('heading', { name: 'A little more you.', exact: true })
  ).toBeVisible();
  await page.getByRole('link', { name: 'Your profile', exact: true }).click();
  await page.getByLabel('Email', { exact: true }).fill(`${username}@example.test`);
  await page.getByLabel('Phone number').fill('+1 312 555 0188');
  await page.getByLabel('Date of birth').fill('2002-04-12');
  await page.getByLabel('Fitness', { exact: true }).check();
  await page.getByRole('button', { name: 'Save profile', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Profile saved');
  await page.getByRole('navigation').getByRole('link', { name: 'Shop', exact: true }).click();
  await expect(page.locator('.category-section h2').first()).toHaveText('Fitness');
});
