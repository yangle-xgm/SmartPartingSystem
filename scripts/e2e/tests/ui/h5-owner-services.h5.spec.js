// @ts-check
/**
 * 业主自助服务 UI（本轮新增）：
 *   首页车主服务入口 / 月卡充值 / 我的车位与共享开关 / 车位预约
 *
 * 约定：注入业主令牌（zhangsan）跳过验证码；
 * 会写库的「共享开关」用例在 finally 里按原值还原，保持演示库基线。
 */
const { test, expect } = require('@playwright/test');
const { login, redisGet } = require('../support/api');
const { uiLogin } = require('../support/ui');

const OWNER = { username: 'zhangsan', password: 'user123' };

async function authAs(page, request, user = OWNER) {
  const token = await login(request, user);
  await page.addInitScript((t) => {
    window.localStorage.setItem('token', t);
  }, token);
  return token;
}

const toast = (page) => page.locator('.van-toast__text');

test.describe('H5 · 首页车主服务入口', () => {
  test('三宫格入口完整渲染并可跳转', async ({ page, request }) => {
    await authAs(page, request);
    await page.goto('/home');

    const entries = page.locator('.entry3 .h-grid__item');
    await expect(entries).toHaveCount(3);
    await expect(entries.nth(0)).toContainText('月卡充值');
    await expect(entries.nth(1)).toContainText('我的车位');
    await expect(entries.nth(2)).toContainText('车位预约');

    await entries.nth(0).click();
    await expect(page).toHaveURL(/\/monthly$/, { timeout: 10_000 });
    await expect(page.locator('.h-hero__title')).toHaveText('月卡充值');

    // 非 Tab 页：必须有返回按钮，否则进去出不来
    await expect(page.locator('.h-hero__back')).toBeVisible();
    await page.locator('.h-hero__back').click();
    await expect(page).toHaveURL(/\/home$/, { timeout: 10_000 });

    await page.locator('.entry3 .h-grid__item').nth(1).click();
    await expect(page).toHaveURL(/\/spaces$/, { timeout: 10_000 });
    await expect(page.locator('.h-hero__title')).toHaveText('我的车位');

    await page.locator('.h-hero__back').click();
    await page.locator('.entry3 .h-grid__item').nth(2).click();
    await expect(page).toHaveURL(/\/reserve$/, { timeout: 10_000 });
    await expect(page.locator('.h-hero__title')).toHaveText('车位预约');
  });

  test('我的页常用功能也能进入三项服务', async ({ page, request }) => {
    await authAs(page, request);
    await page.goto('/mine');
    const links = page.locator('.links__item');
    for (const label of ['月卡充值', '我的车位', '车位预约']) {
      await expect(links.filter({ hasText: label })).toHaveCount(1);
    }

    await links.filter({ hasText: '月卡充值' }).click();
    await expect(page).toHaveURL(/\/monthly$/, { timeout: 10_000 });
  });
});

test.describe('H5 · 月卡充值', () => {
  test('车辆列表、默认选中与报价明细', async ({ page, request }) => {
    await authAs(page, request);
    await page.goto('/monthly');

    await expect(page.locator('.h-hero__title')).toHaveText('月卡充值');

    const cars = page.locator('.pick');
    await expect(cars.first()).toBeVisible({ timeout: 15_000 });
    // 首辆车默认选中（高亮描边）
    await expect(cars.first()).toHaveClass(/pick--on/);

    // 三档统计：月卡车辆 + 临停车辆 + 充值后可用天数
    await expect(page.locator('.h-hero__stat')).toHaveCount(3);

    // 报价必须落到明细里，而不是显示 0.00 或占位
    const total = page.locator('.bill__total b');
    await expect(total).toBeVisible({ timeout: 15_000 });
    const amount1 = await total.innerText();
    expect(amount1).toMatch(/¥?\s*\d+\.\d{2}/);
    expect(Number(amount1.replace(/[^\d.]/g, ''))).toBeGreaterThan(0);

    // 切到 3 个月：总额应为 1 个月的三倍
    await page.locator('.mopt__item').nth(1).click();
    await expect(page.locator('.mopt__item').nth(1)).toHaveClass(/mopt__item--on/);
    await expect
      .poll(async () => {
        const v = await total.innerText();
        return Number(v.replace(/[^\d.]/g, ''));
      }, { timeout: 10_000 })
      .toBeCloseTo(Number(amount1.replace(/[^\d.]/g, '')) * 3, 1);
  });

  test('从车辆页「续费」进入时预选该车', async ({ page, request }) => {
    await authAs(page, request);
    await page.goto('/vehicles');

    const card = page.locator('.h-card.car').first();
    await expect(card).toBeVisible({ timeout: 15_000 });
    await expect(card.getByText(/续费|开月卡/)).toBeVisible();

    await card.getByText(/续费|开月卡/).click();
    await expect(page).toHaveURL(/\/monthly\?vehicleId=/, { timeout: 10_000 });

    const params = new URL(page.url()).searchParams;
    const wanted = params.get('vehicleId');
    expect(wanted).toBeTruthy();
    // 被带过来的车辆必须是高亮选中项
    const on = page.locator('.pick--on');
    await expect(on).toHaveCount(1);
    await expect(on.locator('.h-plate')).toBeVisible();
  });

  test('支付方式弹层可打开并取消，不产生订单', async ({ page, request }) => {
    const token = await authAs(page, request);
    await page.goto('/monthly');

    const total = page.locator('.bill__total b');
    await expect(total).toBeVisible({ timeout: 15_000 });

    const before = await request.get('/api/vehicle/monthly/orders', {
      headers: { Authorization: `Bearer ${token}` },
    });
    const beforeCount = ((await before.json()).data || []).length;

    await page.locator('.paybar__btn').click();
    const sheet = page.locator('.van-action-sheet');
    await expect(sheet).toBeVisible();
    await expect(sheet).toContainText('微信支付');
    await expect(sheet).toContainText('支付宝');

    await page.locator('.van-action-sheet__cancel').click();
    await expect(sheet).toBeHidden();

    // 只到支付方式就取消 → 不应产生月卡订单
    const after = await request.get('/api/vehicle/monthly/orders', {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(((await after.json()).data || []).length).toBe(beforeCount);
  });
});

test.describe('H5 · 我的车位与共享', () => {
  test('车位卡片、共享状态与累计收益', async ({ page, request }) => {
    const token = await authAs(page, request);

    // 夹具：本用例断言的是「共享中」的展示，先确保 A-001 处于共享态，
    // 结束后还原，避免依赖演示库的偶然状态（曾因他人清理数据导致误红）。
    const SPACE_ID = 1;
    await request.put(`/api/parking/space/${SPACE_ID}/share`, {
      headers: { Authorization: `Bearer ${token}` },
      params: { isShared: 1 },
    });

    try {
      await page.goto('/spaces');

      await expect(page.locator('.h-hero__title')).toHaveText('我的车位');
      const cards = page.locator('.h-card.space');
      await expect(cards.first()).toBeVisible({ timeout: 15_000 });

      // 张三名下有 A-001，且处于共享中
      await expect(cards.first()).toContainText('A-001');
      await expect(cards.first()).toContainText('共享收益');
      await expect(cards.first().locator('.van-switch')).toHaveClass(/van-switch--on/);

      // 累计收益卡片与明细行
      await expect(page.locator('.income__sum b')).toBeVisible();
      await expect(page.locator('.rec').first()).toContainText('共享分润');
    } finally {
      await request.put(`/api/parking/space/${SPACE_ID}/share`, {
        headers: { Authorization: `Bearer ${token}` },
        params: { isShared: 0 },
      });
    }
  });

  test('切换共享开关生效并可还原', async ({ page, request }) => {
    const token = await authAs(page, request);
    await page.goto('/spaces');

    const card = page.locator('.h-card.space').first();
    await expect(card).toBeVisible({ timeout: 15_000 });
    const sw = card.locator('.van-switch');
    const wasOn = (await sw.getAttribute('class'))?.includes('van-switch--on');

    try {
      await sw.click();
      await expect(toast(page)).toContainText(/已(开启|关闭)共享/);
      // 状态翻转
      if (wasOn) {
        await expect(sw).not.toHaveClass(/van-switch--on/);
      } else {
        await expect(sw).toHaveClass(/van-switch--on/);
      }
    } finally {
      // 无论断言成功与否都把开关拨回原状态
      const spaceId = 1; // A-001
      await request.put(`/api/parking/space/${spaceId}/share`, {
        headers: { Authorization: `Bearer ${token}` },
        params: { isShared: wasOn ? 1 : 0 },
      });
    }
  });

  test('接口返回空车位数组时展示空态', async ({ page, request }) => {
    await authAs(page, request);
    await page.route('**/api/parking/space/my*', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, message: 'ok', data: [] }),
      }),
    );

    await page.goto('/spaces');
    await expect(page.locator('.h-empty')).toContainText('名下暂无固定车位');
  });
});

test.describe('H5 · 车位预约', () => {
  test('可预约列表、区域筛选与预约弹层', async ({ page, request }) => {
    await authAs(page, request);
    await page.goto('/reserve');

    await expect(page.locator('.h-hero__title')).toHaveText('车位预约');

    const cards = page.locator('.h-card.sp');
    await expect(cards.first()).toBeVisible({ timeout: 15_000 });

    // 区域筛选：切到某个具体区域后，列表里的车位都应属于该区域
    const chips = page.locator('.chip');
    expect(await chips.count()).toBeGreaterThan(1);
    const areaLabel = (await chips.nth(1).innerText()).trim();
    await chips.nth(1).click();
    await expect(chips.nth(1)).toHaveClass(/chip--on/);
    const firstMeta = await page.locator('.h-card.sp .sp__no span').first().innerText();
    expect(firstMeta).toContain(areaLabel);

    // 打开预约弹层，切换时长后预览区间应变化
    await page.locator('.h-card.sp .sp__btn').first().click();
    const sheet = page.locator('.van-popup--bottom');
    await expect(sheet).toBeVisible();
    await expect(sheet).toContainText('开始时间');
    await expect(sheet).toContainText('占用时长');

    const previewBefore = await sheet.locator('.preview').innerText();
    // .opt 有两组：第 0 组是开始时间，第 1 组是占用时长
    const durationGroup = sheet.locator('.opt').nth(1);
    await durationGroup.locator('.opt__item').first().click();  // 30 分钟
    await expect
      .poll(async () => sheet.locator('.preview').innerText(), { timeout: 10_000 })
      .not.toBe(previewBefore);

    // 点遮罩顶部关闭弹层（Vant Popup 默认不响应 Escape），不落库
    await page.locator('.van-overlay').click({ position: { x: 195, y: 60 } });
    await expect(sheet).toBeHidden();
  });

  test('我的预约 Tab 展示预约记录与状态', async ({ page, request }) => {
    await authAs(page, request);
    await page.goto('/reserve');

    await page.locator('.van-tab').filter({ hasText: '我的预约' }).click();
    const cards = page.locator('.h-card.res');
    const empty = page.locator('.h-card.h-empty');

    // 有记录看状态胶囊，没记录看空态 —— 两者必居其一，不能白屏
    await expect(cards.first().or(empty.first())).toBeVisible({ timeout: 15_000 });
    if (await cards.count()) {
      await expect(cards.first().locator('.h-pill')).toBeVisible();
    }
  });

  test('预约接口失败时给出提示且不崩页', async ({ page, request }) => {
    await authAs(page, request);
    await page.route('**/api/parking/reserve*', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 5202, message: '车位当前不可预约或已占用', data: null }),
      }),
    );

    await page.goto('/reserve');
    await page.locator('.h-card.sp .sp__btn').first().click();
    await page.locator('.van-popup--bottom .h-btn').click();

    await expect(toast(page)).toContainText('车位当前不可预约或已占用');
    await expect(page.locator('.h-page')).toBeVisible();
  });
});
