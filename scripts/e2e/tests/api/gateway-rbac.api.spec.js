// @ts-check
/**
 * 网关 RBAC 边界（验收发现 P0 的回归用例）
 *
 * 背景：`ADMIN_ONLY_PATTERNS` 里设备域原先写成 `/api/device/*`（单段通配），
 * 漏掉了三类路径：
 *   - `POST /api/device`            无子段 → 不匹配
 *   - `POST /api/device/mock/camera` 两段 → 不匹配（业主可伪造抓拍，自动入场刷订单）
 *   - `GET  /api/device/status/overview` 两段 → 不匹配
 * 现统一收敛为 `/api/device` + `/api/device/**`，本文件锁定该边界，
 * 同时防止收紧时误伤业主必需的 `/space/{id}/share`。
 */
const { test, expect } = require('@playwright/test');
const { login, auth, sql } = require('../support/api');

const OWNER = { username: 'zhangsan', password: 'user123' };
const ADMIN = { username: 'admin', password: 'admin123' };

/** 业主调用必须被网关拒绝的管理端接口 */
const OWNER_FORBIDDEN = [
  ['GET', '/api/device/list'],
  ['GET', '/api/device/status/overview'],
  ['POST', '/api/device/mock/camera'],
  ['POST', '/api/device/mock/exit-camera'],
  ['POST', '/api/user/list'],
  ['GET', '/api/audit/list'],
  ['GET', '/api/stats/income'],
  ['GET', '/api/stats/export'],
];

test.describe('网关 RBAC · 业主不可触达管理端资源', () => {
  test('设备域全部端点对业主返回 403（含 mock 抓拍与状态总览）', async ({ request }) => {
    const token = await login(request, OWNER);

    for (const [method, path] of OWNER_FORBIDDEN) {
      const res = await request.fetch(path, {
        method,
        headers: auth(token),
        params: { deviceNo: 'CAM-A-001', plateNo: '京A81236' },
        data: method === 'GET' ? undefined : {},
      });
      expect(res.status(), `${method} ${path} 应被网关拒绝`).toBe(403);
      const body = await res.json().catch(() => ({}));
      expect(body.message || '').toContain('无权限');
    }
  });

  test('业主伪造抓拍不产生订单（修复前会生成入场订单）', async ({ request }) => {
    const token = await login(request, OWNER);
    const before = Number(sql('SELECT COUNT(*) FROM parking_order'));

    await request.post('/api/device/mock/camera', {
      headers: auth(token),
      params: { deviceNo: 'CAM-A-001', plateNo: '京A81236', confidence: 99 },
    });

    const after = Number(sql('SELECT COUNT(*) FROM parking_order'));
    expect(after, '业主伪造抓拍不应新增订单').toBe(before);
  });

  test('管理端账号仍可正常读写设备域（收紧未误伤）', async ({ request }) => {
    const token = await login(request, ADMIN);
    const res = await request.get('/api/device/list', {
      headers: auth(token),
      params: { current: 1, size: 5 },
    });
    const body = await res.json();
    expect(body.code).toBe(200);
    expect(Array.isArray(body.data.records) || Array.isArray(body.data)).toBeTruthy();
  });

  test('业主自助开关车位共享仍需放行（收紧要精准）', async ({ request }) => {
    const token = await login(request, OWNER);
    const ownerId = sql('SELECT id FROM sys_user WHERE username="zhangsan"');
    const spaceId = sql(
      `SELECT id FROM parking_space WHERE owner_id=${ownerId} AND is_deleted=0 ORDER BY id LIMIT 1`,
    );
    expect(spaceId, '前置：张三名下需有产权车位').toBeTruthy();

    const res = await request.put(`/api/parking/space/${spaceId}/share`, {
      headers: auth(token),
      params: { isShared: 1 },
    });
    const body = await res.json();
    expect(body.code, `业主开关自己车位共享应放行: ${body.message}`).toBe(200);

    // 还原
    await request.put(`/api/parking/space/${spaceId}/share`, {
      headers: auth(token),
      params: { isShared: 0 },
    });
  });
});
