// @ts-check
/**
 * 业主端 H5 · 通知中心
 *
 * 回归背景：通知中心不是 Tab 页（无底部导航），此前进入后只能靠浏览器返回键退出，
 * 全屏 WebApp 场景下等于死路。这里锁住「有返回入口」与「返回回到来处」两条契约。
 */
const { test, expect } = require('@playwright/test');
const { uiLogin } = require('../support/ui');

test.describe('业主端 H5 · 通知中心', () => {
  test('从首页进入后点返回，回到首页', async ({ page }) => {
    await uiLogin(page, { submitSelector: 'button.login__submit' });
    await expect(page).toHaveURL(/\/home/);

    // 首页右上角铃铛
    await page.locator('.h-hero__action').first().click();
    await expect(page).toHaveURL(/\/notify/);
    await expect(page.getByRole('heading', { name: '通知中心' })).toBeVisible();

    // 通知中心不是 Tab 页，不应出现底部导航（它是"详情页"而非第五个 Tab）
    await expect(page.locator('.van-tabbar')).toHaveCount(0);

    await page.locator('.h-hero__back').click();
    await expect(page).toHaveURL(/\/home/);
    await expect(page.locator('.h-hero__title')).toContainText('，');
  });

  test('直接打开 /notify 时点返回回首页，而不是退出应用', async ({ page }) => {
    await uiLogin(page, { submitSelector: 'button.login__submit' });

    // 深链接进入：没有历史栈，history.back() 会退出浏览器，必须兜底到首页
    await page.goto('/notify');
    await expect(page.getByRole('heading', { name: '通知中心' })).toBeVisible();

    await page.locator('.h-hero__back').click();
    await expect(page).toHaveURL(/\/home/);
    await expect(page.locator('.h-hero__title')).toContainText('，');
  });
});
