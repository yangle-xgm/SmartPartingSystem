// @ts-check
const { defineConfig } = require('@playwright/test');

/**
 * 智慧停车系统 E2E 配置
 *
 * 环境变量：
 *   API_BASE_URL  网关地址（默认 http://localhost:8080）
 *   PC_BASE_URL   管理端地址（默认 http://localhost:3000，vite dev）
 *   H5_BASE_URL   业主端地址（默认 http://localhost:3001，vite dev）
 *   E2E_ADMIN_USER / E2E_ADMIN_PASSWORD  管理端账号（默认 admin/admin123）
 *
 * 浏览器：复用本机 Edge（channel: msedge），无需 `playwright install` 下载 Chromium。
 */
const API_BASE = process.env.API_BASE_URL || 'http://localhost:8080';
const PC_BASE = process.env.PC_BASE_URL || 'http://localhost:3000';
const H5_BASE = process.env.H5_BASE_URL || 'http://localhost:3001';

module.exports = defineConfig({
  testDir: './tests',
  timeout: 90_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
    actionTimeout: 15_000,
  },
  projects: [
    {
      name: 'api',
      testMatch: /.*\.api\.spec\.js/,
      use: { baseURL: API_BASE, extraHTTPHeaders: { 'Content-Type': 'application/json' } },
    },
    {
      name: 'pc-ui',
      testMatch: /.*\.pc\.spec\.js/,
      use: { baseURL: PC_BASE, channel: 'msedge', viewport: { width: 1440, height: 900 } },
    },
    {
      name: 'h5-ui',
      testMatch: /.*\.h5\.spec\.js/,
      use: { baseURL: H5_BASE, channel: 'msedge', viewport: { width: 390, height: 844 } },
    },
  ],
});
