// @ts-check
/**
 * 管理端（Element Plus）功能测试：
 * 登录与表单校验 / 角色菜单与路由守卫 / 全页面渲染 / 弹窗交互 / 筛选 / 404 / 移动端重定向 / 接口异常
 */
const { test, expect } = require('@playwright/test');
const { uiLogin } = require('../support/ui');
const { redisGet, login } = require('../support/api');

const ADMIN = { username: 'admin', password: 'admin123' };
const OWNER = { username: 'zhaoliu', password: 'user123' };

const ADMIN_PAGES = [
  { path: '/dashboard', title: '运营数据看板' },
  { path: '/parking', title: '车位管理' },
  { path: '/vehicle', title: '车辆档案' },
  { path: '/order', title: '订单管理' },
  { path: '/payment', title: '缴费管理' },
  { path: '/device', title: '设备管理' },
  { path: '/statistics', title: '数据统计' },
  { path: '/notification', title: '通知中心' },
  { path: '/feedback', title: '意见反馈' },
  { path: '/feedback-manage', title: '反馈处理' },
  { path: '/user', title: '用户管理' },
  { path: '/community', title: '小区管理' },
  { path: '/audit', title: '操作审计' },
  { path: '/profile', title: '个人中心' },
];

test.describe('管理端 · 登录页与表单校验', () => {
  test('登录页品牌、输入项、验证码与默认账号提示齐全', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByText('账号登录')).toBeVisible();
    await expect(page.getByPlaceholder('请输入用户名')).toBeVisible();
    await expect(page.getByPlaceholder('请输入密码')).toBeVisible();
    await expect(page.getByPlaceholder('计算结果')).toBeVisible();
    await expect(page.locator('img[title="点击刷新"]')).toBeVisible();
    await expect(page.getByText('默认账号：admin / admin123')).toBeVisible();
    await expect(page.getByRole('button', { name: /登\s*录/ })).toBeVisible();
  });

  test('空表单提交触发三条必填校验', async ({ page }) => {
    await page.goto('/login');
    await page.getByRole('button', { name: /登\s*录/ }).click();

    await expect(page.locator('.el-form-item__error').filter({ hasText: '请输入用户名' })).toBeVisible();
    await expect(page.locator('.el-form-item__error').filter({ hasText: '请输入密码' })).toBeVisible();
    await expect(page.locator('.el-form-item__error').filter({ hasText: '请输入计算结果' })).toBeVisible();
    await expect(page).toHaveURL(/\/login$/);
  });

  test('密码错误时给出提示且停留在登录页', async ({ page }) => {
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

    await page.getByPlaceholder('请输入用户名').fill(ADMIN.username);
    await page.getByPlaceholder('请输入密码').fill('wrong-password');
    await page.getByPlaceholder('计算结果').fill(redisGet(`captcha:${captchaKey}`));
    await page.getByRole('button', { name: /登\s*录/ }).click();

    await expect(page.locator('.el-message').first()).toBeVisible({ timeout: 15_000 });
    await expect(page).toHaveURL(/\/login$/);
  });

  test('「业主注册」链接跳转注册页', async ({ page }) => {
    await page.goto('/login');
    await page.getByText('业主注册').click();
    await expect(page).toHaveURL(/\/register$/);
    await expect(page.locator('.el-form')).toBeVisible();
  });
});

test.describe('管理端 · 角色菜单与路由守卫', () => {
  test('未登录访问管理页重定向登录', async ({ page }) => {
    await page.goto('/parking');
    await expect(page).toHaveURL(/\/login$/, { timeout: 15_000 });
  });

  test('超管菜单按角色渲染，且不含业主专属入口', async ({ page }) => {
    await uiLogin(page);
    const nav = page.locator('.nav');
    await expect(nav).toBeVisible();

    for (const text of ['车位管理', '订单管理', '设备管理', '数据统计', '用户管理', '小区管理', '操作审计']) {
      await expect(nav.getByText(text, { exact: true })).toBeVisible();
    }
    for (const text of ['车位预约', '我的订单', '访客邀请']) {
      await expect(nav.getByText(text, { exact: true })).toHaveCount(0);
    }
    await expect(page.locator('.role-chip')).toContainText('管理员');
  });

  test('业主账号访问管理页被重定向回看板', async ({ page }) => {
    await uiLogin(page, { username: OWNER.username, password: OWNER.password });
    await page.goto('/parking');
    await expect(page).toHaveURL(/\/dashboard$/, { timeout: 15_000 });
  });

  test('未知路径渲染 404 页而非白屏', async ({ page }) => {
    await uiLogin(page);
    await page.goto('/definitely-not-a-page');
    await expect(page.locator('.nf__code')).toHaveText('404');
    await expect(page.getByText('不存在')).toBeVisible();
    await expect(page.locator('.nf__btn').first()).toBeVisible();
  });
});

test.describe('管理端 · 全页面渲染', () => {
  test('14 个管理页面标题正确、无控制台错误', async ({ page }) => {
    await uiLogin(page);

    const errors = [];
    page.on('console', (msg) => {
      if (msg.type() === 'error') errors.push(`${page.url()} :: ${msg.text()}`);
    });
    page.on('pageerror', (err) => errors.push(`${page.url()} :: ${String(err)}`));

    const broken = [];
    for (const p of ADMIN_PAGES) {
      await page.goto(p.path);
      try {
        await expect(page.locator('.sp-title').first()).toHaveText(p.title, { timeout: 20_000 });
      } catch {
        const actual = await page.locator('.sp-title').first().innerText().catch(() => '(无标题)');
        broken.push(`${p.path} 期望「${p.title}」实际「${actual}」`);
      }
    }

    expect(broken, `标题不符：\n${broken.join('\n')}`).toHaveLength(0);
    expect(
      errors.filter((e) => !/favicon|404 \(Not Found\)|ResizeObserver/i.test(e)),
      `控制台错误：\n${errors.join('\n')}`,
    ).toHaveLength(0);
  });

  test('车位管理表格展示真实数据且状态列自洽', async ({ page }) => {
    await uiLogin(page);
    await page.goto('/parking');

    const firstRow = page.locator('.el-table__body tr').first();
    await expect(firstRow).toBeVisible({ timeout: 20_000 });
    await expect(firstRow).toContainText(/[A-Z]-\d+/);

    // 统计卡数字与表格可读
    const stats = page.locator('.sp-stat-grid');
    await expect(stats).toBeVisible();
  });

  test('数据统计页三张图表完成初始化', async ({ page }) => {
    await uiLogin(page);
    await page.goto('/statistics');

    await expect(page.locator('.sp-title')).toHaveText('数据统计');
    const charts = page.locator('.chart canvas');
    await expect.poll(async () => charts.count(), { timeout: 25_000 }).toBeGreaterThanOrEqual(3);
  });
});

test.describe('管理端 · 弹窗与表单交互', () => {
  test('新增车位：默认值、必填校验与重复编号拦截', async ({ page }) => {
    await uiLogin(page);
    await page.goto('/parking');
    await expect(page.locator('.el-table__body tr').first()).toBeVisible({ timeout: 20_000 });

    await page.getByRole('button', { name: /新增车位/ }).click();
    const dialog = page.locator('.el-dialog').filter({ hasText: '新增车位' });
    await expect(dialog).toBeVisible();

    // 区域/楼层/类型带默认值，减少必填项
    await expect(dialog.locator('.el-select').first()).toContainText('A 区');
    await expect(dialog.getByPlaceholder('如 B1')).toHaveValue('B1');
    await expect(dialog.locator('.el-select').nth(1)).toContainText('临时车位');

    // 只留车位编号一项必填
    await dialog.getByRole('button', { name: '保存' }).click();
    await expect(dialog.locator('.el-form-item__error').filter({ hasText: '请输入车位编号' })).toBeVisible();

    // 填一个已存在的编号 → 后端应拒绝并提示，且弹窗不关闭
    await dialog.getByPlaceholder('如 A-001').fill('A-001');
    await dialog.getByRole('button', { name: '保存' }).click();
    await expect(page.locator('.el-message').first()).toBeVisible({ timeout: 15_000 });
    await expect(dialog).toBeVisible();

    // 取消后弹窗关闭，不产生脏数据
    await dialog.getByRole('button', { name: '取消' }).click();
    await expect(dialog).toBeHidden();
  });

  test('新增车位：月租价格输入负数被组件下限拦截为 0', async ({ page }) => {
    await uiLogin(page);
    await page.goto('/parking');
    await expect(page.locator('.el-table__body tr').first()).toBeVisible({ timeout: 20_000 });

    await page.getByRole('button', { name: /新增车位/ }).click();
    const dialog = page.locator('.el-dialog').filter({ hasText: '新增车位' });
    await expect(dialog).toBeVisible();

    const price = dialog.locator('.el-input-number input').first();
    await price.fill('-100');
    await price.blur();

    const value = await price.inputValue();
    expect(Number(value)).toBeGreaterThanOrEqual(0);

    await dialog.getByRole('button', { name: '取消' }).click();
  });

  test('车辆档案：关键字搜索无结果显示空态，筛选黑名单结果自洽', async ({ page }) => {
    await uiLogin(page);
    await page.goto('/vehicle');
    await expect(page.locator('.el-table__body tr').first()).toBeVisible({ timeout: 20_000 });

    const search = page.getByPlaceholder(/搜索车牌/);
    await search.fill('ZZZZZZ99999');
    await page.waitForTimeout(800);
    await expect(page.locator('.el-table__body tr')).toHaveCount(0);

    await search.clear();
    await page.waitForTimeout(500);

    await page.getByText('黑名单', { exact: true }).first().click();
    await page.waitForTimeout(800);
    const rows = page.locator('.el-table__body tr');
    const count = await rows.count();
    if (count > 0) {
      await expect(rows.first()).toContainText('黑名单');
    }
  });

  test('意见反馈：提交弹窗必填校验与字数限制', async ({ page }) => {
    await uiLogin(page);
    await page.goto('/feedback');
    await expect(page.locator('.sp-title')).toHaveText('意见反馈');

    await page.getByRole('button', { name: /提交反馈/ }).click();
    const dialog = page.locator('.el-dialog').filter({ hasText: '提交反馈' });
    await expect(dialog).toBeVisible();

    await dialog.locator('.el-dialog__footer').getByRole('button', { name: /提\s*交/ }).click();
    await expect(dialog.locator('.el-form-item__error').filter({ hasText: '请输入标题' })).toBeVisible();
    await expect(dialog.locator('.el-form-item__error').filter({ hasText: '请填写详细描述' })).toBeVisible();

    // maxlength 生效：标题最多 40 字
    const title = dialog.getByPlaceholder('一句话描述问题');
    await title.fill('一'.repeat(60));
    expect((await title.inputValue()).length).toBeLessThanOrEqual(40);

    await dialog.getByRole('button', { name: '取消' }).click();
    await expect(dialog).toBeHidden();
  });

  test('车位删除：二次确认取消后数据不变', async ({ page }) => {
    await uiLogin(page);
    await page.goto('/parking');
    await expect(page.locator('.el-table__body tr').first()).toBeVisible({ timeout: 20_000 });

    const rows = page.locator('.el-table__body tr');
    const before = await rows.count();

    await rows.first().getByText('删除', { exact: true }).click();
    const box = page.locator('.el-message-box');
    await expect(box).toBeVisible();
    await expect(box).toContainText('删除后车位');
    await box.getByRole('button', { name: '取消' }).click();
    await expect(box).toBeHidden();

    await page.waitForTimeout(800);
    await expect(page.locator('.el-table__body tr')).toHaveCount(before);
  });

  test('车位详情抽屉可从表格打开并展示字段', async ({ page }) => {
    await uiLogin(page);
    await page.goto('/parking');
    await expect(page.locator('.el-table__body tr').first()).toBeVisible({ timeout: 20_000 });

    await page.locator('.el-table__body tr').first().getByText('详情', { exact: true }).click();
    const drawer = page.locator('.el-drawer').filter({ hasText: '车位详情' });
    await expect(drawer).toBeVisible({ timeout: 15_000 });
    await expect(drawer).toContainText('所属区域');
    await expect(drawer).toContainText('月租价格');
    await page.keyboard.press('Escape');
  });
});

test.describe('管理端 · 接口异常与响应式', () => {
  test('车位列表接口 500：给出提示且页面不白屏', async ({ page }) => {
    await uiLogin(page);
    await page.route('**/api/parking/**', (route) =>
      route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ code: 500, message: 'Internal Server Error', data: null }),
      }),
    );

    await page.goto('/parking');
    await expect(page.locator('.sp-title')).toHaveText('车位管理');
    await expect(page.locator('.el-message').first()).toBeVisible({ timeout: 20_000 });
    await expect(page.locator('.sp-stat-grid')).toBeVisible();
  });

  test('移动端 UA 访问管理端自动跳到 H5 业主端', async ({ browser, request }) => {
    const token = await login(request, ADMIN);
    const ctx = await browser.newContext({
      userAgent:
        'Mozilla/5.0 (Linux; Android 13; 23127PN0CC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36',
      viewport: { width: 390, height: 844 },
    });
    await ctx.addInitScript((t) => window.localStorage.setItem('token', t), token);

    const page = await ctx.newPage();
    await page.goto('http://localhost:3000/dashboard');
    await expect(page).toHaveURL(/\/h5\/home$/, { timeout: 20_000 });
    await ctx.close();
  });

  /**
   * DEF-01 回归：与 H5 同源（safeMessage 的 INTERNAL_MSG 只拦 SQL/堆栈关键字），
   * 英文框架短语被判定为「干净文案」直出。修复后 code>=500 一律走固定文案。
   */
  test('500 应提示友好文案而非英文框架原文', async ({ page }) => {
    await uiLogin(page);
    await page.route('**/api/parking/**', (route) =>
      route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ code: 500, message: 'Internal Server Error', data: null }),
      }),
    );

    await page.goto('/parking');
    const msg = page.locator('.el-message').first();
    await expect(msg).toContainText('服务繁忙');
    await expect(msg).not.toContainText('Internal Server Error');
  });

  test('1440 与 1024 宽度下主框架不出现横向溢出', async ({ page }) => {
    await uiLogin(page);
    const overflows = [];
    for (const width of [1440, 1024]) {
      await page.setViewportSize({ width, height: 900 });
      await page.goto('/dashboard');
      await expect(page.locator('.sp-title')).toBeVisible();
      await page.waitForTimeout(600);
      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
      );
      if (overflow > 2) overflows.push(`${width}px 溢出 ${overflow}px`);
    }
    expect(overflows).toHaveLength(0);
  });
});
