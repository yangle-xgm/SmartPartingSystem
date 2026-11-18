// @ts-check
/**
 * 业主端 H5 健壮性测试：接口异常、空数据、登录过期、响应式与横向溢出边界
 */
const { test, expect } = require('@playwright/test');
const { login } = require('../support/api');

const OWNER = { username: 'zhangsan', password: 'user123' };

async function authAs(page, request) {
  const token = await login(request, OWNER);
  await page.addInitScript((t) => {
    window.localStorage.setItem('token', t);
  }, token);
  return token;
}

const toast = (page) => page.locator('.van-toast__text');

test.describe('H5 · 接口异常与空数据', () => {
  test('用户信息接口 500：页面不白屏且给出提示', async ({ page, request }) => {
    await authAs(page, request);
    await page.route('**/api/user/info*', (route) =>
      route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ code: 500, message: 'Internal Server Error', data: null }),
      }),
    );

    await page.goto('/home');
    await expect(page.locator('.h-page')).toBeVisible();
    // 问候语回退到「业主」，不应整页崩掉
    await expect(page.locator('.h-hero__title')).toContainText('业主');
    // 快捷入口四宫格仍在（车主服务三宫格另算）
    await expect(page.locator('.entry .h-grid__item')).toHaveCount(4);
    await expect(toast(page)).toBeVisible();
  });

  /**
   * DEF-01 回归：safeMessage 只过滤 SQL/堆栈关键字时，英文框架短语
   * 「Internal Server Error」既不命中黑名单也不超长，会被当作可用文案直出给用户。
   * 修复后 code>=500 一律走固定文案，原文只进 console.error。
   */
  test('500 应提示「服务繁忙」而非英文框架原文', async ({ page, request }) => {
    await authAs(page, request);
    await page.route('**/api/user/info*', (route) =>
      route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ code: 500, message: 'Internal Server Error', data: null }),
      }),
    );

    await page.goto('/home');
    const t = toast(page);
    await expect(t).toBeVisible();
    await expect(t).toContainText('服务繁忙');
    await expect(t).not.toContainText('Internal Server Error');
  });

  test('订单接口网络中断：提示网络异常并落到空态', async ({ page, request }) => {
    await authAs(page, request);
    await page.route('**/api/order/list*', (route) => route.abort('failed'));

    await page.goto('/orders');
    await expect(page.locator('.h-hero__title')).toHaveText('停车订单');
    await expect(toast(page)).toContainText('网络异常');
    await expect(page.locator('.h-card.h-empty')).toContainText('暂无停车订单');
  });

  test('订单列表返回空数组：分组空态文案各自正确', async ({ page, request }) => {
    await authAs(page, request);
    await page.route('**/api/order/list*', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, message: 'ok', data: [] }),
      }),
    );

    await page.goto('/orders');
    const tabs = page.locator('.van-tab');
    const expected = ['暂无停车订单', '当前没有停车中的订单', '没有待缴费的订单', '还没有已完成的订单'];

    for (let i = 0; i < 4; i++) {
      await tabs.nth(i).click();
      await expect(page.locator('.h-card.h-empty')).toHaveText(expected[i], { timeout: 10_000 });
    }
  });

  test('业务码 401：清理本地令牌并跳转登录页', async ({ page, request }) => {
    await authAs(page, request);
    await page.route('**/api/order/list*', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 401, message: '未登录或登录已过期', data: null }),
      }),
    );

    await page.goto('/orders');
    await expect(page).toHaveURL(/\/login$/, { timeout: 15_000 });
    expect(await page.evaluate(() => localStorage.getItem('token'))).toBeNull();
    await expect(toast(page)).toContainText('登录已过期');
  });
});

test.describe('H5 · 响应式与边界', () => {
  const PATHS = [
    { path: '/home', name: '首页' },
    { path: '/vehicles', name: '车辆' },
    { path: '/orders', name: '订单' },
    { path: '/invite', name: '访客' },
    { path: '/mine', name: '我的' },
  ];

  test('320 / 390 / 430 三种宽度均无横向溢出且底部导航可见', async ({ page, request }) => {
    await authAs(page, request);
    const overflows = [];

    for (const width of [320, 390, 430]) {
      await page.setViewportSize({ width, height: 800 });
      for (const p of PATHS) {
        await page.goto(p.path);
        await expect(page.locator('.h-page')).toBeVisible();
        // 等骨架屏结束再量宽度
        await page.waitForTimeout(700);

        const overflow = await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        );
        if (overflow > 2) overflows.push(`${p.name}@${width}px 溢出 ${overflow}px`);

        await expect(page.locator('.van-tabbar')).toBeVisible();
        // 底部导航不能被内容顶出视口
        const box = await page.locator('.van-tabbar').boundingBox();
        expect(box && box.y + box.height).toBeLessThanOrEqual(800 + 1);
      }
    }

    expect(overflows, `横向溢出：${overflows.join('；')}`).toHaveLength(0);
  });

  test('登录页在 320px 窄屏下表单不溢出', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 640 });
    await page.goto('/login');
    await expect(page.getByPlaceholder('请输入用户名')).toBeVisible();
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    expect(overflow).toBeLessThanOrEqual(2);
    await expect(page.locator('button.login__submit')).toBeVisible();
  });

  test('通知页深链接直接打开时返回按钮兜底回首页', async ({ page, request }) => {
    await authAs(page, request);
    await page.goto('/notify');
    await expect(page.locator('.h-hero__back')).toBeVisible();
    await page.locator('.h-hero__back').click();
    await expect(page).toHaveURL(/\/home$/, { timeout: 10_000 });
    await expect(page.locator('.h-page')).toBeVisible();
  });

  test('未读数超过 99 时首页角标显示 99+', async ({ page, request }) => {
    await authAs(page, request);
    await page.route('**/api/notify/unread/count*', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, message: 'ok', data: 120 }),
      }),
    );

    await page.goto('/home');
    const badge = page.locator('.h-grid__item').filter({ hasText: '通知中心' }).locator('.h-grid__badge');
    await expect(badge).toHaveText('99+');
    await expect(page.locator('.h-hero__action')).toContainText('通知 99+');
  });

  test('小区档案为空时「我的」显示「未绑定小区」而非错显首条', async ({ page, request }) => {
    await authAs(page, request);
    await page.route('**/api/user/community/list*', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, message: 'ok', data: [] }),
      }),
    );

    await page.goto('/mine');
    const row = page.locator('.h-kv').filter({ hasText: '所属小区' });
    await expect(row).toContainText('未绑定小区');
  });
});
