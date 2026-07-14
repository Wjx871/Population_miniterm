import { defineConfig, devices } from '@playwright/test'

const baseURL = process.env.E2E_BASE_URL || 'http://127.0.0.1:15180'
const startServers = process.env.E2E_START_SERVERS === 'true'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 45_000,
  expect: { timeout: 10_000 },
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: [
    ['line'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
  ],
  outputDir: 'test-results',
  use: {
    baseURL,
    ...devices['Desktop Chrome'],
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
  },
  webServer: startServers
    ? [
        {
          command: '..\\mvnw.cmd spring-boot:run',
          cwd: '..',
          url: 'http://127.0.0.1:8080/api/health',
          reuseExistingServer: true,
          timeout: 180_000,
        },
        {
          command: 'npm run dev -- --host 127.0.0.1',
          url: baseURL,
          env: {
            ...process.env,
            VITE_DEV_PORT: new URL(baseURL).port || '15180',
            VITE_BACKEND_TARGET: process.env.E2E_BACKEND_URL || 'http://127.0.0.1:8080',
          },
          reuseExistingServer: true,
          timeout: 120_000,
        },
      ]
    : undefined,
})
