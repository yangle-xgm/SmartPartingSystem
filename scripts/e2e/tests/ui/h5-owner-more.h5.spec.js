// @ts-check
/**
 * 业主端「后端有能力、前端没入口」补齐后的回归（D-01 ~ D-05）：
 *   支付记录 / 订单详情 / 意见反馈 / 资料编辑 / 月卡充值记录
 *
 * 约定：注入业主令牌（zhangsan）跳过验证码；会写库的用例在 finally 里用 sql()
 * 还原/清理，保持演示库基线（反馈没有删除接口，只能直接删表）。
 */
const { test, expect } = require('@playwright/test');
const { login, sql } = require('../support/api');

const OWNER = { username: 'zhangsan', password: 'user123' };
const OWNER_ID = 1001;

/**
 * 中文不能经 execSync 往返：Windows 下 cmd.exe 按 ANSI 代码页转换参数与输出，
 * 实测「张三」读回来变 '??'、写回去把姓名写坏。需要中文时一律走 HEX：
 * 读用 SELECT HEX(col)，写用 CONVERT(0x... USING utf8mb4)，全程纯 ASCII。
 */

async function authAs(page, request) {
  const token = await login(request, OWNER);
  await page.addInitScript((t) => {
    window.localStorage.setItem('token', t);
  }, token);
  return token;
}

const toast = (page) => page.locator('.van-toast__text');

test.describe('H5 · 支付记录', () => {
  test('列表渲染、统计与状态筛选', async ({ page, request }) => {
    await authAs(page, request);
    await page.goto('/payments');

    await expect(page.locator('.h-hero__title')).toHaveText('支付记录');
    // 非 Tab 页必须有返回入口
    await expect(page.locator('.h-hero__back')).toBeVisible();

    const cards = page.locator('.pay');
    await expect(cards.first()).toBeVisible({ timeout: 15_000 });
    // 演示库中 zhangsan 有已支付流水，累计金额不应为 0
    await expect(page.locator('.h-hero__stat').first().locator('b')).not.toHaveText('¥0.00');
    // 每条流水都要有金额、支付方式与流水号
    await expect(cards.first()).toContainText('微信支付');
    await expect(cards.first()).toContainText('流水号 PAY');

    // 切成「退款」：演示库无退款流水 → 空态而不是空白页
    await page.locator('.chips__item', { hasText: '退款' }).click();
    await expect(page.locator('.h-empty__title')).toHaveText('没有退款记录');

    await page.locator('.chips__item', { hasText: '成功' }).click();
    await expect(page.locator('.pay').first()).toBeVisible();
    // 「成功」筛选后不应出现待支付/退款标签
    await expect(page.locator('.pay .h-pill', { hasText: '待支付' })).toHaveCount(0);
  });
});

test.describe('H5 · 订单详情', () => {
  test('从订单卡片进入详情，展示计费口径与订单信息', async ({ page, request }) => {
    await authAs(page, request);
    await page.goto('/orders');

    const firstCard = page.locator('.order').first();
    await expect(firstCard).toBeVisible({ timeout: 15_000 });
    const orderNo = (await firstCard.locator('.order__no').innerText()).replace('订单号 ', '').trim();

    // 点卡片主体（避开「取消订单/去支付」按钮）
    await firstCard.locator('.order__grid').click();

    await expect(page).toHaveURL(new RegExp(`/orders/${orderNo}$`), { timeout: 10_000 });
    await expect(page.locator('.h-hero__title')).toHaveText('订单详情');
    await expect(page.locator('.h-hero__desc')).toHaveText(orderNo);

    // 费用明细区块必须存在，且给出计费口径（规则名或「按临停标准计费」兜底）
    await expect(page.getByText('费用明细')).toBeVisible();
    await expect(page.getByText('车辆与车位')).toBeVisible();
    await expect(page.getByText('订单信息')).toBeVisible();
    const ruleLabel = page.locator('.h-card', { hasText: '费用明细' }).locator('.h-card__extra');
    await expect(ruleLabel).not.toHaveText('');

    // 返回按钮回到订单列表
    await page.locator('.h-hero__back').click();
    await expect(page).toHaveURL(/\/orders$/, { timeout: 10_000 });
  });

  test('不存在或他人的订单号给出空态，不白屏', async ({ page, request }) => {
    await authAs(page, request);
    await page.goto('/orders/ORD_NOT_EXIST_2026');

    await expect(page.locator('.h-empty__title')).toBeVisible({ timeout: 15_000 });
    await expect(page.locator('.h-empty__hint')).toContainText('不是本人车辆');
    await page.locator('.h-empty__cta').click();
    await expect(page).toHaveURL(/\/orders$/, { timeout: 10_000 });
  });
});

test.describe('H5 · 意见反馈', () => {
  test('内容过短拒绝提交并提示，不产生数据', async ({ page, request }) => {
    await authAs(page, request);
    await page.goto('/feedback');

    await expect(page.locator('.h-hero__title')).toHaveText('意见反馈');
    await page.locator('.field--area textarea').fill('漏水');
    await page.locator('.submit').click();
    await expect(toast(page)).toContainText('至少描述 5 个字');
  });

  test('提交成功后出现在「我的反馈」并标注待处理', async ({ page, request }) => {
    const title = `E2E反馈-${Date.now()}`;
    await authAs(page, request);
    try {
      await page.goto('/feedback');

      // 类型默认「建议」，这里显式选「投诉」验证切换生效
      await page.locator('.types__item', { hasText: '投诉' }).click();
      await page.locator('.field input').first().fill(title);
      await page.locator('.field--area textarea').fill('B2 层 105 车位上方持续漏水，地面已积水。');
      await page.locator('.submit').click();

      await expect(toast(page)).toContainText('已提交', { timeout: 15_000 });
      const card = page.locator('.fb', { hasText: title });
      await expect(card).toBeVisible({ timeout: 15_000 });
      await expect(card.locator('.h-pill')).toHaveText('待处理');
      await expect(card).toContainText('投诉');
      // 提交后表单清空，便于再次提交
      await expect(page.locator('.field--area textarea')).toHaveValue('');
    } finally {
      // 反馈没有删除接口；按 ASCII 前缀匹配，避免中文字面量进 SQL
      sql(`DELETE FROM feedback WHERE user_id = ${OWNER_ID} AND status = 0 AND title LIKE 'E2E%'`);
    }
  });

  test('已有反馈展示物业回复', async ({ page, request }) => {
    await authAs(page, request);
    await page.goto('/feedback');

    // 演示库中已有物业回复过的反馈（status=2）；按「有回复区块」定位，
    // 不能用标题 .first()——同名前缀的未回复反馈可能排在更前面
    const replied = page.locator('.fb').filter({ has: page.locator('.fb__reply') }).first();
    await expect(replied).toBeVisible({ timeout: 15_000 });
    await expect(replied.locator('.fb__reply-label')).toContainText('物业回复');
    await expect(replied.locator('.fb__reply p')).not.toHaveText('');
    await expect(replied.locator('.h-pill')).toHaveText('已回复');
  });
});

test.describe('H5 · 编辑资料', () => {
  test('姓名回填、格式校验与保存生效', async ({ page, request }) => {
    // 先备份 HEX（ASCII 安全），失败/结束后按原值还原
    const hexOriginal = sql(`SELECT HEX(real_name) FROM sys_user WHERE id = ${OWNER_ID}`);
    await authAs(page, request);
    try {
      await page.goto('/profile-edit');
      await expect(page.locator('.h-hero__title')).toHaveText('编辑资料');

      const nameInput = page.locator('.row__input').first();
      // 姓名已回填（值本身是中文，比对长度与占位符即可，避免中文往返）
      await expect(nameInput).not.toHaveValue('');
      expect((await nameInput.inputValue()).length).toBeGreaterThanOrEqual(2);

      // 手机号格式校验：非法号码不提交
      await page.locator('.row__input').nth(1).fill('12345');
      await page.locator('.save').click();
      await expect(toast(page)).toContainText('手机号格式不正确');

      // 中文名保存 → 返回「我的」显示新名字
      await page.locator('.row__input').nth(1).fill('');
      await nameInput.fill('张三丰');
      await page.locator('.save').click();
      await expect(toast(page)).toContainText('资料已更新', { timeout: 15_000 });

      // 保存后自动回「我的」，姓名立即刷新
      await expect(page).toHaveURL(/\/mine$/, { timeout: 15_000 });
      await expect(page.locator('.profile__name')).toHaveText('张三丰', { timeout: 15_000 });
    } finally {
      sql(`UPDATE sys_user SET real_name = CONVERT(0x${hexOriginal} USING utf8mb4) WHERE id = ${OWNER_ID}`);
    }
  });

  test('上传头像后立即预览，且图片可公开访问', async ({ page, request }) => {
    const original = sql(`SELECT COALESCE(avatar, '') FROM sys_user WHERE id = ${OWNER_ID}`);
    await authAs(page, request);
    try {
      await page.goto('/profile-edit');

      // 1x1 PNG，避免依赖外部图片资源
      const png = Buffer.from(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
        'base64',
      );
      await page.locator('.avatar__input').setInputFiles({
        name: 'avatar.png',
        mimeType: 'image/png',
        buffer: png,
      });

      await expect(toast(page)).toContainText('头像已上传', { timeout: 20_000 });
      const src = await page.locator('.avatar__img').getAttribute('src');
      expect(src).toMatch(/^\/api\/user\/avatar\/.+\.png$/);

      // 关键回归：此前返回 /uploads/... 无路由、服务也无静态映射，头像恒裂图
      const res = await request.get(src);
      expect(res.status()).toBe(200);
      expect(res.headers()['content-type']).toContain('image/');
    } finally {
      sql(`UPDATE sys_user SET avatar = ${original ? `'${original}'` : 'NULL'} WHERE id = ${OWNER_ID}`);
    }
  });
});

test.describe('H5 · 月卡充值记录', () => {
  test('月卡页展示充值记录区块（有记录或空态）', async ({ page, request }) => {
    await authAs(page, request);
    await page.goto('/monthly');

    await expect(page.locator('.h-section__title', { hasText: '充值记录' })).toBeVisible({ timeout: 15_000 });
    // 演示库清过月卡订单，正常应落空态；有记录时则必须是可读卡片
    const empty = page.locator('.h-empty__title', { hasText: '还没有充值记录' });
    const row = page.locator('.hist').first();
    await expect(empty.or(row)).toBeVisible({ timeout: 15_000 });
  });
});
