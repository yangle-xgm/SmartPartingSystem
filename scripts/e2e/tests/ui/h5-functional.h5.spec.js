// @ts-check
/**
 * 业主端 H5 功能测试：页面渲染 / 路由跳转 / 表单校验 / 按钮与弹窗交互 / 数据展示
 *
 * 约定：除登录页自身外，用例统一注入业主令牌（zhangsan），
 * 避免每条用例都消耗一次图形验证码，同时保留一条完整 UI 登录链路。
 */
const { test, expect } = require('@playwright/test');
const { login, redisGet } = require('../support/api');
const { uiLogin } = require('../support/ui');

const OWNER = { username: 'zhangsan', password: 'user123' };
const OWNER_ID = '1001';

/** 注入业主令牌，跳过验证码交互 */
async function authAs(page, request, user = OWNER) {
  const token = await login(request, user);
  await page.addInitScript((t) => {
    window.localStorage.setItem('token', t);
  }, token);
  return token;
}

/** vant toast 文案 */
const toast = (page) => page.locator('.van-toast__text');

test.describe('H5 · 登录与路由守卫', () => {
  test('登录页品牌、双 Tab、输入项与验证码图完整渲染', async ({ page }) => {
    await page.goto('/login');

    await expect(page.locator('.login__title')).toHaveText('智慧星河');
    await expect(page.locator('.login__tab').first()).toHaveText('登录');
    await expect(page.locator('.login__tab').nth(1)).toHaveText('注册业主账号');

    await expect(page.getByPlaceholder('请输入用户名')).toBeVisible();
    await expect(page.getByPlaceholder('请输入密码')).toBeVisible();
    await expect(page.getByPlaceholder('请输入计算结果')).toBeVisible();
    await expect(page.locator('button.login__submit')).toBeEnabled();

    const captcha = page.locator('img.captcha');
    await expect(captcha).toBeVisible();
    expect(await captcha.getAttribute('src')).toMatch(/^data:image\//);
  });

  test('未登录访问任意内页都重定向到登录页', async ({ page }) => {
    for (const path of ['/home', '/orders', '/invite', '/mine']) {
      await page.goto(path);
      await expect(page).toHaveURL(/\/login$/, { timeout: 10_000 });
      await expect(page.getByPlaceholder('请输入用户名')).toBeVisible();
    }
  });

  test('已登录访问 /login 自动回首页', async ({ page, request }) => {
    await authAs(page, request);
    await page.goto('/login');
    await expect(page).toHaveURL(/\/home$/, { timeout: 10_000 });
    await expect(page.locator('.h-hero__title')).toContainText('张三');
  });

  test('未知路径回落到首页而非白屏', async ({ page, request }) => {
    await authAs(page, request);
    await page.goto('/this-page-does-not-exist');
    await expect(page).toHaveURL(/\/home$/, { timeout: 10_000 });
    await expect(page.locator('.h-page')).toBeVisible();
  });

  test('完整 UI 登录链路（真实验证码）', async ({ page }) => {
    await uiLogin(page, {
      username: OWNER.username,
      password: OWNER.password,
      submitSelector: 'button.login__submit',
    });
    await expect(page).toHaveURL(/\/home$/);
    const token = await page.evaluate(() => localStorage.getItem('token'));
    expect(token && token.split('.').length).toBe(3);
  });
});

test.describe('H5 · 登录表单校验', () => {
  test('空账号密码提交提示「请输入用户名和密码」', async ({ page }) => {
    await page.goto('/login');
    await page.locator('button.login__submit').click();
    await expect(toast(page)).toHaveText('请输入用户名和密码');
  });

  test('只填账号密码不填验证码提示「请输入图形验证码」', async ({ page }) => {
    await page.goto('/login');
    await page.getByPlaceholder('请输入用户名').fill('zhangsan');
    await page.getByPlaceholder('请输入密码').fill('user123');
    await page.locator('button.login__submit').click();
    await expect(toast(page)).toHaveText('请输入图形验证码');
  });

  test('验证码错误时提示失败并自动刷新验证码', async ({ page }) => {
    let captchaKey = '';
    page.on('response', async (res) => {
      if (res.url().includes('/api/user/captcha') && res.ok()) {
        try {
          captchaKey = (await res.json())?.data?.captchaKey || '';
        } catch {
          /* ignore */
        }
      }
    });
    await page.goto('/login');
    await expect.poll(() => captchaKey).toBeTruthy();

    const before = await page.locator('img.captcha').getAttribute('src');
    await page.getByPlaceholder('请输入用户名').fill(OWNER.username);
    await page.getByPlaceholder('请输入密码').fill(OWNER.password);
    await page.getByPlaceholder('请输入计算结果').fill('99999');
    await page.locator('button.login__submit').click();

    // 后端返回 5101「验证码错误」，页面停留登录页并刷新验证码
    await expect(page).toHaveURL(/\/login$/);
    await expect
      .poll(async () => page.locator('img.captcha').getAttribute('src'), { timeout: 10_000 })
      .not.toBe(before);
  });

  test('注册校验：密码过短 / 两次不一致 / 手机号非法', async ({ page }) => {
    await page.goto('/login');
    await page.locator('.login__tab').nth(1).click();
    await expect(page.getByPlaceholder('4-20 位字母或数字')).toBeVisible();

    await page.getByPlaceholder('4-20 位字母或数字').fill('e2echeck');
    await page.getByPlaceholder('6-20 位').fill('123');
    await page.locator('button.login__submit').click();
    await expect(toast(page)).toHaveText('密码至少 6 位');

    await page.getByPlaceholder('6-20 位').fill('abc123');
    await page.getByPlaceholder('再次输入密码').fill('abc124');
    await page.locator('button.login__submit').click();
    await expect(toast(page)).toHaveText('两次输入的密码不一致');

    await page.getByPlaceholder('再次输入密码').fill('abc123');
    await page.getByPlaceholder('11 位手机号').fill('12345');
    await page.locator('button.login__submit').click();
    await expect(toast(page)).toHaveText('请输入正确的手机号');
  });
});

test.describe('H5 · 底部导航与页面渲染', () => {
  test('五个 Tab 可切换且高亮正确', async ({ page, request }) => {
    await authAs(page, request);
    await page.goto('/home');
    await expect(page.locator('.van-tabbar-item')).toHaveCount(5);

    const tabs = [
      { label: '车辆', path: '/vehicles', selector: '.h-hero__title', title: '我的车辆' },
      { label: '订单', path: '/orders', selector: '.h-hero__title', title: '停车订单' },
      { label: '访客', path: '/invite', selector: '.h-hero__title', title: '访客邀请' },
      { label: '我的', path: '/mine', selector: '.profile__name', title: '张三' },
      { label: '首页', path: '/home', selector: '.h-hero__title', title: '张三' },
    ];

    for (const t of tabs) {
      await page.locator('.van-tabbar-item').filter({ hasText: t.label }).first().click();
      await expect(page).toHaveURL(new RegExp(`${t.path}$`), { timeout: 10_000 });
      await expect(
        page.locator('.van-tabbar-item--active').filter({ hasText: t.label }).first(),
      ).toBeVisible();
      await expect(page.locator(t.selector).first()).toContainText(t.title);
    }
  });

  test('首页：问候语、三宫格指标与进行中停车区展示', async ({ page, request }) => {
    await authAs(page, request);
    await page.goto('/home');

    await expect(page.locator('.h-hero__title')).toContainText('张三');
    const stats = page.locator('.h-hero__stat b');
    await expect(stats).toHaveCount(3);
    for (let i = 0; i < 3; i++) {
      await expect(stats.nth(i)).toHaveText(/^\d+$/);
    }
    await expect(page.getByText('进行中的停车')).toBeVisible();
    // 快捷入口四宫格（车主服务三宫格另算，故限定在 .entry 内）
    await expect(page.locator('.entry .h-grid__item')).toHaveCount(4);
  });

  test('首页四宫格跳转正确', async ({ page, request }) => {
    await authAs(page, request);
    const cases = [
      { text: '我的车辆', path: '/vehicles' },
      { text: '停车订单', path: '/orders' },
      { text: '访客邀请', path: '/invite' },
      { text: '通知中心', path: '/notify' },
    ];
    for (const c of cases) {
      await page.goto('/home');
      await page.locator('.h-grid__item').filter({ hasText: c.text }).click();
      await expect(page).toHaveURL(new RegExp(`${c.path}$`), { timeout: 10_000 });
    }
  });

  test('通知中心不是 Tab 页：无底部导航，但有返回入口', async ({ page, request }) => {
    await authAs(page, request);
    await page.goto('/notify');
    await expect(page.locator('.h-hero__title')).toHaveText('通知中心');
    await expect(page.locator('.van-tabbar')).toHaveCount(0);
    await expect(page.locator('.h-hero__back')).toBeVisible();
  });

  test('订单页：列表、四个分组 Tab 与操作按钮', async ({ page, request }) => {
    await authAs(page, request);
    await page.goto('/orders');
    await expect(page.locator('.h-hero__title')).toHaveText('停车订单');

    const tabs = page.locator('.van-tab');
    await expect(tabs).toHaveCount(4);
    await expect(tabs.nth(0)).toHaveText('全部');

    // 逐个切换分组，结果必须自洽：要么有卡片，要么出现明确空态
    for (let i = 0; i < 4; i++) {
      await tabs.nth(i).click();
      await expect(tabs.nth(i)).toHaveClass(/van-tab--active/);
      const cards = page.locator('.h-card.order');
      const empty = page.locator('.h-card.h-empty');
      await expect(cards.first().or(empty.first())).toBeVisible({ timeout: 10_000 });
    }

    await tabs.nth(0).click();
    const firstCard = page.locator('.h-card.order').first();
    await expect(firstCard).toBeVisible();
    await expect(firstCard.locator('.h-plate')).toBeVisible();
    await expect(firstCard.locator('.order__no')).toContainText('订单号');
  });

  test('订单操作：取消订单二次确认、去支付唤起支付方式弹层', async ({ page, request }) => {
    await authAs(page, request);
    await page.goto('/orders');
    const tabs = page.locator('.van-tab');

    // 进行中：取消订单需二次确认，取消后订单仍在
    await tabs.nth(1).click();
    await page.waitForTimeout(500);
    const cancelBtn = page.locator('.minibtn').filter({ hasText: '取消订单' }).first();
    if (await cancelBtn.count()) {
      const cardsBefore = await page.locator('.h-card.order').count();
      await cancelBtn.click();
      const dialog = page.locator('.van-dialog');
      await expect(dialog).toBeVisible();
      await dialog.locator('.van-dialog__cancel').click();
      await expect(dialog).toBeHidden();
      await expect(page.locator('.h-card.order')).toHaveCount(cardsBefore);
    } else {
      await expect(page.locator('.h-card.h-empty')).toBeVisible();
    }

    // 待缴费：去支付唤起支付方式（不实际支付）
    await tabs.nth(2).click();
    await page.waitForTimeout(500);
    const payBtn = page.locator('.minibtn').filter({ hasText: '去支付' }).first();
    if (await payBtn.count()) {
      await payBtn.click();
      const sheet = page.locator('.van-action-sheet');
      await expect(sheet).toBeVisible();
      await expect(sheet).toContainText('微信支付（模拟）');
      await expect(sheet).toContainText('支付宝（模拟）');
      await sheet.locator('.van-action-sheet__cancel').click();
      await expect(sheet).toBeHidden();
    } else {
      await expect(page.locator('.h-card.h-empty')).toBeVisible();
    }
  });

  test('车辆页：卡片信息、详情弹层与解绑二次确认', async ({ page, request }) => {
    await authAs(page, request);
    await page.goto('/vehicles');
    await expect(page.locator('.h-hero__title')).toHaveText('我的车辆');
    await expect(page.locator('.h-hero__stat b').first()).toHaveText(/^\d+$/);

    const card = page.locator('.h-card.car').first();
    await expect(card).toBeVisible();
    await expect(card.locator('.h-plate')).toBeVisible();

    // 解绑二次确认：取消后不改变列表
    const countBefore = await page.locator('.h-card.car').count();
    await card.getByText('解绑', { exact: true }).click();
    const dialog = page.locator('.van-dialog').filter({ hasText: '解绑' });
    await expect(dialog).toBeVisible();
    await dialog.locator('.van-dialog__cancel').click();
    await expect(dialog).toBeHidden();
    await expect(page.locator('.h-card.car')).toHaveCount(countBefore);

    // 详情弹层
    await card.getByText('详情', { exact: true }).click();
    const popup = page.locator('.van-popup--bottom').filter({ hasText: '车辆详情' });
    await expect(popup).toBeVisible();
    await expect(popup).toContainText('车牌颜色');
    await expect(popup).toContainText('月卡到期');
  });

  test('车辆页：绑定弹层校验空车牌与过短车牌', async ({ page, request }) => {
    await authAs(page, request);
    await page.goto('/vehicles');
    await page.locator('.h-fab').click();

    const popup = page.locator('.van-popup--bottom').filter({ hasText: '绑定新车牌' });
    await expect(popup).toBeVisible();

    await popup.getByText('确认绑定').click();
    await expect(toast(page)).toHaveText('请输入车牌号');

    await popup.getByPlaceholder('如：京A88888').fill('京A1');
    await popup.getByText('确认绑定').click();
    await expect(toast(page)).toHaveText('车牌号格式不正确');
  });

  test('邀请页：有效期切换、生成校验与作废二次确认', async ({ page, request }) => {
    await authAs(page, request);
    await page.goto('/invite');
    await expect(page.locator('.h-hero__title')).toHaveText('访客邀请');

    // 有效期按钮切换（默认 1 天，即第 3 项）
    const durs = page.locator('.dur__item');
    await expect(durs).toHaveCount(4);
    await expect(durs.nth(2)).toHaveClass(/active/);
    await durs.last().click();
    await expect(durs.last()).toHaveClass(/active/);
    await expect(durs.nth(2)).not.toHaveClass(/active/);

    // 空车牌校验
    await page.locator('.h-btn').filter({ hasText: '生成核验码' }).click();
    await expect(toast(page)).toHaveText('请输入访客车牌号');

    // 过短车牌校验
    await page.getByPlaceholder('如：京E88888').fill('京E1');
    await page.locator('.h-btn').filter({ hasText: '生成核验码' }).click();
    await expect(toast(page)).toHaveText('车牌号格式不正确');

    // 筛选 chips：每个分组都要有明确结果（卡片或空态）
    const chips = page.locator('.chips__item');
    for (let i = 0; i < (await chips.count()); i++) {
      await chips.nth(i).click();
      await expect(page.locator('.h-card.inv').first().or(page.locator('.h-card.h-empty').first()))
        .toBeVisible({ timeout: 10_000 });
    }

    // 作废二次确认：取消后记录仍在
    await chips.first().click();
    const invalidate = page.locator('.minibtn--danger').first();
    if (await invalidate.count()) {
      const codeText = await page.locator('.inv__code').first().innerText();
      await invalidate.click();
      const dialog = page.locator('.van-dialog');
      await expect(dialog).toBeVisible();
      await dialog.locator('.van-dialog__cancel').click();
      await expect(dialog).toBeHidden();
      await expect(page.locator('.inv__code').first()).toContainText(codeText.trim().split('\n')[0]);
    }
  });

  test('通知页：未读筛选、点卡片已读、返回首页', async ({ page, request }) => {
    const token = await authAs(page, request);
    expect(token).toBeTruthy();

    await page.goto('/notify');
    const unreadStat = page.locator('.h-hero__stat[data-tone="gold"] b');
    const unreadBefore = Number(await unreadStat.innerText());
    expect(unreadBefore).toBeGreaterThan(0);

    // 切到未读分组
    await page.locator('.chips__item').nth(1).click();
    await expect(page.locator('.chips__item').nth(1)).toHaveClass(/active/);

    // 点第一条未读 → 未读数 -1
    await page.locator('.h-card.msg').first().click();
    await expect.poll(async () => Number(await unreadStat.innerText()), { timeout: 10_000 })
      .toBe(unreadBefore - 1);

    // 还原该条为未读，避免污染演示库
    const restored = require('node:child_process').execSync(
      `docker exec sp-mysql mysql -uroot -p21491879 smart_parking -N -e "UPDATE notification SET is_read=0 WHERE user_id=${OWNER_ID} ORDER BY update_time DESC LIMIT 1"`,
      { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] },
    );
    expect(restored).toBe('');

    // 返回按钮回到首页
    await page.locator('.h-hero__back').click();
    await expect(page).toHaveURL(/\/home$/, { timeout: 10_000 });
  });

  test('通知页「全部已读」清零未读数（用例结束还原数据）', async ({ page, request }) => {
    const { execSync } = require('node:child_process');
    const sql = (stmt) =>
      execSync(
        `docker exec sp-mysql mysql -uroot -p21491879 smart_parking -N -e ${JSON.stringify(stmt)}`,
        { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] },
      ).trim();

    // 记录当前未读，便于用例结束后还原
    const unreadIds = sql(`SELECT id FROM notification WHERE user_id=${OWNER_ID} AND is_read=0`)
      .split('\n')
      .map((s) => s.trim())
      .filter(Boolean);

    try {
      await authAs(page, request);
      await page.goto('/notify');
      const unreadStat = page.locator('.h-hero__stat[data-tone="gold"] b');
      if (unreadIds.length === 0) {
        // 无未读时应给出「没有未读消息」而不是报错
        await page.locator('.h-hero__action').click();
        await expect(toast(page)).toHaveText('没有未读消息');
        return;
      }

      await expect(unreadStat).toHaveText(String(unreadIds.length));
      await page.locator('.h-hero__action').click();
      await expect(toast(page)).toContainText('已全部标记为已读');
      await expect(unreadStat).toHaveText('0');
      await expect(page.locator('.chips__item').nth(1)).not.toContainText(/\d/);
    } finally {
      if (unreadIds.length) {
        sql(`UPDATE notification SET is_read=0 WHERE id IN (${unreadIds.join(',')})`);
      }
    }
  });

  test('我的页：用户信息、手机号脱敏与所属小区', async ({ page, request }) => {
    await authAs(page, request);
    await page.goto('/mine');

    await expect(page.locator('.profile__name')).toHaveText('张三');
    const body = await page.locator('.h-page').innerText();
    expect(body).toMatch(/1\d{2}\*{4}\d{4}/);        // 手机号脱敏
    expect(body).toContain('业主');
    expect(body).toContain('智慧星河花园');           // 由 communityId=1 映射，不再取列表首条兜底
    expect(body).not.toContain('未绑定小区');
  });

  test('退出登录需二次确认，取消后仍在登录态', async ({ page, request }) => {
    await authAs(page, request);
    await page.goto('/mine');

    const logout = page.locator('button.logout');
    await logout.scrollIntoViewIfNeeded();
    await logout.click();

    const dialog = page.locator('.van-dialog').filter({ hasText: '退出登录' });
    await expect(dialog).toBeVisible();
    await dialog.locator('.van-dialog__cancel').click();
    await expect(dialog).toBeHidden();
    await expect(page).toHaveURL(/\/mine$/);
    expect(await page.evaluate(() => localStorage.getItem('token'))).toBeTruthy();
  });
});
