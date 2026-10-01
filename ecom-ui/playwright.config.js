import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './tests',
  timeout: 120000,
  workers: 1,
  expect: { timeout: 20000 },
  use: {
    baseURL: 'http://localhost:5173',
    browserName: 'chromium',
    launchOptions: { timeout: 30000 },
    trace: 'off',
  },
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
  },
});
