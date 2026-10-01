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
      name: username === 'admin1' ? 'Admin dashboard' : `Welcome back, ${username}`,
      exact: true,
    })
  ).toBeVisible();
  await expect(page).toHaveURL(
    new RegExp(username === 'admin1' ? '#/admin/dashboard$' : '#/customer/dashboard$')
  );
}
async function create(page, amount, customer) {
  await page.getByRole('navigation').getByRole('link', { name: 'Create order' }).click();
  if (customer) await page.getByLabel('Customer username').fill(customer);
  else await expect(page.getByLabel('Customer username')).toHaveAttribute('readonly', '');
  await page.getByLabel('Order amount (USD)').fill(amount);
  const created = page.waitForResponse(
    (r) => r.url().endsWith('/api/orders') && r.request().method() === 'POST'
  );
  await page.getByRole('button', { name: 'Place order', exact: true }).click();
  const response = await created;
  expect(response.status()).toBe(201);
  const order = await response.json();
  await expect(
    page.getByRole('heading', { name: `Order #${order.id}`, exact: true })
  ).toBeVisible();
  return order;
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
  const own = await create(page, '37.50');
  await expect(page.getByRole('heading', { name: 'Manage order' })).toHaveCount(0);
  await page.getByRole('navigation').getByRole('link', { name: 'My orders' }).click();
  await page.getByLabel('Search orders').fill(String(own.id));
  await page.getByRole('link', { name: `View order ${own.id}`, exact: true }).click();
  await expect(page.getByRole('heading', { name: `Order #${own.id}` })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('heading', { name: `Order #${own.id}` })).toBeVisible();
  await page.getByRole('button', { name: 'Pay now', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Payment received', exact: true })).toBeVisible();
  await page.getByRole('navigation').getByRole('link', { name: 'Dashboard' }).click();
  await page.screenshot({ path: testInfo.outputPath('customer-dashboard.png'), fullPage: true });
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
    page.getByRole('heading', { name: 'Welcome back, customer1', exact: true })
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
        await expect(other.getByRole('heading', { name: 'Manage order' })).toBeVisible();
        createdForCustomer = await create(other, '84.25', 'customer1');
        await other.getByLabel('Order status', { exact: true }).selectOption('FAILED');
        await other.getByRole('button', { name: 'Update status' }).click();
        await expect(
          other.getByRole('status').filter({ hasText: 'Order status updated.' })
        ).toBeVisible();
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
  await expect(page.locator('.detail-list .badge')).toHaveText('Failed');
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
