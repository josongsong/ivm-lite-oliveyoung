import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 2 : undefined,
  reporter: process.env.CI
    ? [['github'], ['html', { open: 'never' }], ['json', { outputFile: 'test-results/results.json' }]]
    : [['html', { open: 'on-failure' }]],

  use: {
    baseURL: 'http://localhost:3000',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },

  // 테스트 그룹별 타임아웃
  timeout: 30000,
  expect: {
    timeout: 10000,
    toHaveScreenshot: {
      maxDiffPixelRatio: 0.02,
      animations: 'disabled',
    },
  },

  projects: [
    // 기본 테스트 (Chromium)
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
      testIgnore: ['**/a11y/**', '**/performance/**', '**/regression/**'],
    },

    // Visual Regression (Chromium only)
    {
      name: 'visual',
      use: { ...devices['Desktop Chrome'] },
      testMatch: '**/regression/**',
    },

    // Accessibility (Chromium)
    {
      name: 'a11y',
      use: { ...devices['Desktop Chrome'] },
      testMatch: '**/a11y/**',
    },

    // Performance (Chromium)
    {
      name: 'performance',
      use: { ...devices['Desktop Chrome'] },
      testMatch: '**/performance/**',
    },

    // Cross-browser: Firefox
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
      testIgnore: ['**/a11y/**', '**/performance/**', '**/regression/**'],
    },

    // Cross-browser: Safari
    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
      testIgnore: ['**/a11y/**', '**/performance/**', '**/regression/**'],
    },

    // Mobile: Chrome
    {
      name: 'mobile-chrome',
      use: { ...devices['Pixel 5'] },
      testIgnore: ['**/a11y/**', '**/performance/**', '**/regression/**', '**/journeys/**'],
    },

    // Mobile: Safari
    {
      name: 'mobile-safari',
      use: { ...devices['iPhone 12'] },
      testIgnore: ['**/a11y/**', '**/performance/**', '**/regression/**', '**/journeys/**'],
    },
  ],

  webServer: {
    command: 'pnpm dev',
    url: 'http://localhost:3000',
    reuseExistingServer: !process.env.CI,
    timeout: 120000,
  },
});
