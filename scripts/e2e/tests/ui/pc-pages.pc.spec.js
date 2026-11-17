// @ts-check
/**
 * 管理端 UI 冒烟：登录 → 首页渲染 → 车位管理表格
 */
const { test, expect } = require('@playwright/test');
const { uiLogin } = require('../support/ui');

test.describe('管理端页面', () => {
  test.beforeEach(async ({ page }) => {
    await uiLogin(page);
  });

  test('登录后进入主框架，侧边菜单可见', async ({ page }) => {
    // 登录成功后应离开 /login
    expect(page.url()).not.toContain('/login');
    // 主框架：侧边栏 + 顶部，至少能看到"车位"相关菜单项
    await expect(page.getByText('车位管理').first()).toBeVisible({ timeout: 20_000 });
  });

  test('车位管理页能渲染表格数据', async ({ page }) => {
    await page.goto('/parking');
    // 表格首行应出现车位编号（A-/B- 开头）
    await expect(page.locator('.el-table__body tr').first()).toBeVisible({ timeout: 20_000 });
    const firstRow = await page.locator('.el-table__body tr').first().innerText();
    expect(firstRow).toMatch(/[A-Z]-\d+/);
  });

  test('车位页状态筛选不报错且结果自洽', async ({ page }) => {
    const errors = [];
    page.on('console', (msg) => {
      if (msg.type() === 'error') errors.push(msg.text());
    });

    await page.goto('/parking');
    await expect(page.locator('.el-table__body tr').first()).toBeVisible({ timeout: 20_000 });

    // 打开"状态"筛选下拉，选择"已占用"
    const statusSelect = page.locator('.el-select').filter({ hasText: '状态' }).first();
    if (await statusSelect.count()) {
      await statusSelect.click();
      const option = page.locator('.el-select-dropdown__item').filter({ hasText: '已占用' }).first();
      if (await option.count()) {
        await option.click();
        await page.waitForTimeout(1500);
        const rows = page.locator('.el-table__body tr');
        const count = await rows.count();
        // 结果自洽：要么为空态提示，要么每行状态列显示"已占用"
        if (count > 0) {
          const text = await rows.first().innerText();
          expect(text).toContain('已占用');
        }
      }
    }

    expect(errors.filter((e) => !/favicon|404 \(Not Found\)/i.test(e))).toHaveLength(0);
  });
});
