// @ts-check
/**
 * 业主端 H5 冒烟：登录页渲染、登录后首页不报错、页面无横向溢出
 */
const { test, expect } = require('@playwright/test');
const { uiLogin } = require('../support/ui');

test.describe('业主端 H5', () => {
  test('未登录访问首页应跳转登录页', async ({ page }) => {
    await page.goto('/home');
    await page.waitForLoadState('domcontentloaded');
    await expect(page.getByPlaceholder('请输入用户名')).toBeVisible({ timeout: 15_000 });
  });

  test('登录后首页渲染且无横向溢出', async ({ page }) => {
    const errors = [];
    page.on('console', (msg) => {
      if (msg.type() === 'error') errors.push(msg.text());
    });
    page.on('pageerror', (err) => errors.push(String(err)));

    await uiLogin(page, { submitSelector: 'button.login__submit' });
    await page.waitForTimeout(1500);

    // 页面不应出现横向滚动（移动端常见回归）
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    expect(overflow, 'H5 首页出现横向溢出').toBeLessThanOrEqual(2);

    expect(errors.filter((e) => !/favicon/i.test(e))).toHaveLength(0);
  });
});
