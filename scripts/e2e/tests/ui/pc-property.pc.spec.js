// @ts-check
/**
 * 物业管理端（role=1）功能与权限边界。
 *
 * 此前只覆盖过 role=0（业主）与 role=2（超管），物业管理员是唯一没被验证的角色：
 * 全仓 14 个管理端页面里，role=1 能进 10 个、不能进 3 个（小区/用户/审计），
 * 且所有查询必须被 X-Community-Id 限制在本小区内 —— 这条社区隔离此前无任何用例。
 */
const { test, expect } = require('@playwright/test');
const { uiLogin } = require('../support/ui');

const PROPERTY = { username: 'property_mgr', password: 'admin123' };  // id=2001, community=1

async function loginAsProperty(page) {
  await uiLogin(page, PROPERTY);
}

test.describe('管理端 · 物业管理员', () => {
  test('顶栏角色标识与菜单权限正确', async ({ page }) => {
    await loginAsProperty(page);
    await page.goto('/dashboard');

    await expect(page.locator('.sp-title')).toBeVisible({ timeout: 20_000 });
    await expect(page.locator('.role-chip')).toContainText('物业管理员');

    const nav = page.locator('.nav');
    await expect(nav).toBeVisible();

    // 物业该有的运营菜单
    for (const label of ['车位管理', '订单管理', '缴费管理', '设备管理', '数据统计', '反馈处理']) {
      await expect(nav.getByText(label, { exact: true }), `物业应可见「${label}」`).toBeVisible();
    }
    // 超管专属不能出现
    for (const label of ['小区管理', '用户管理', '操作审计']) {
      await expect(nav.getByText(label, { exact: true }), `物业不应可见「${label}」`).toHaveCount(0);
    }
    // 业主专属入口同样不该出现
    for (const label of ['车位预约', '我的订单', '访客邀请']) {
      await expect(nav.getByText(label, { exact: true })).toHaveCount(0);
    }
  });

  test('直接访问超管页面被路由守卫挡回看板', async ({ page }) => {
    await loginAsProperty(page);
    for (const path of ['/user', '/community', '/audit']) {
      await page.goto(path);
      await expect(page).toHaveURL(/\/dashboard$/, { timeout: 20_000 });
    }
  });

  test('车位列表被限制在本小区（社区隔离）', async ({ page }) => {
    await loginAsProperty(page);
    await page.goto('/parking');

    const firstRow = page.locator('.el-table__body tr').first();
    await expect(firstRow).toBeVisible({ timeout: 20_000 });

    const table = page.locator('.el-table__body');
    const text = await table.innerText();

    // community_id=1 的车位（A-001/A-002）应出现
    expect(text).toContain('A-001');
    // community_id=2 的车位（C-401/C-402）绝不能出现
    for (const other of ['C-401', 'C-402', 'C-410']) {
      expect(text, `物业(社区1)不应看到社区2的车位 ${other}`).not.toContain(other);
    }
  });

  test('反馈处理页可打开并呈现列表结构', async ({ page }) => {
    await loginAsProperty(page);
    await page.goto('/feedback-manage');

    await expect(page.locator('.sp-title')).toBeVisible({ timeout: 20_000 });
    // 有数据看表格、没数据看空态，二者必居其一
    const hasTable = await page.locator('.el-table__body tr').count();
    if (hasTable === 0) {
      await expect(page.locator('.el-table__empty-block')).toBeVisible();
    } else {
      await expect(page.locator('.el-table__body tr').first()).toBeVisible();
    }
  });
});
