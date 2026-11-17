// @ts-check
/**
 * 软删除唯一键回归（前端测试 D4）
 *
 * 背景：uk_space_no 不含 is_deleted 时，删掉的车位编号永久无法重建（409）。
 * 现方案为生成列 alive_space_no + 唯一索引，本用例验证：
 *   1. 在用编号重复创建仍被拒绝（唯一性未被破坏）
 *   2. 删除后可用同一编号重建，且可反复删除/重建
 */
const { test, expect } = require('@playwright/test');
const api = require('../support/api');

const body = (spaceNo) => ({
  spaceNo,
  area: 'ZZ',
  floor: 'B9',
  type: 0,
  status: 0,
  monthPrice: 100.0,
  isShared: 0,
  communityId: 1,
});

test.describe('车位软删除后编号可复用', () => {
  /** @type {string} */
  let token;

  test.beforeAll(async ({ request }) => {
    token = await api.login(request);
  });

  test('在用编号不允许重复，删除后可反复重建', async ({ request }) => {
    const spaceNo = `E2E-SD-${Date.now().toString().slice(-8)}`;

    try {
      for (let round = 1; round <= 3; round += 1) {
        const create = await request.post('/api/parking/space', {
          headers: api.auth(token),
          data: body(spaceNo),
        });
        const created = await create.json();
        expect(created.code, `第 ${round} 轮创建应成功: ${created.message}`).toBe(200);

        const id = api.sql(`SELECT id FROM parking_space WHERE space_no='${spaceNo}' AND is_deleted=0 LIMIT 1`);
        expect(id).not.toBe('');

        // 同编号仍在用时再次创建应被唯一约束拒绝
        const dup = await request.post('/api/parking/space', {
          headers: api.auth(token),
          data: body(spaceNo),
        });
        const dupBody = await dup.json();
        expect(dupBody.code, '未删除的重复编号应返回 409').toBe(409);

        const del = await request.delete(`/api/parking/space/${id}`, { headers: api.auth(token) });
        expect((await del.json()).code, `第 ${round} 轮删除应成功`).toBe(200);
      }
    } finally {
      api.cleanupTestData(spaceNo);
    }
  });
});
