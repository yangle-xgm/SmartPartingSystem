// @ts-check
/**
 * 预约链路 E2E：预约 → 我的预约 → 取消，以及时间边界校验（错误码 5206）。
 */
const { test, expect } = require('@playwright/test');
const api = require('../support/api');

/** 明天 10:00 的 "yyyy-MM-dd HH:mm:ss" */
function tomorrowAt(hour, minute = 0) {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(hour)}:${p(minute)}:00`;
}

test.describe('预约与取消', () => {
  /** @type {string} */
  let token;
  /** @type {{id: string, spaceNo: string}} */
  let space;

  test.beforeAll(async ({ request }) => {
    token = await api.login(request);
    space = await api.createTestSpace(request, token, 'E2E-RSV');
  });

  test.afterAll(() => {
    if (space) api.cleanupTestData(space.spaceNo);
  });

  test('预约成功可查可取消', async ({ request }) => {
    const start = tomorrowAt(10);
    const end = tomorrowAt(11);

    const reserveRes = await request.post(
      `/api/parking/reserve?spaceId=${space.id}&userId=1001&startTime=${encodeURIComponent(start)}&endTime=${encodeURIComponent(end)}`,
      { headers: api.auth(token) },
    );
    const reserve = await reserveRes.json();
    expect(reserve.code, `预约失败: ${reserve.message}`).toBe(200);

    // 我的预约里应能查到这条未取消的预约
    const myRes = await request.get('/api/parking/reserve/my?userId=1001', {
      headers: api.auth(token),
    });
    const my = await myRes.json();
    expect(my.code).toBe(200);
    const mine = (my.data || []).filter((r) => String(r.spaceId) === String(space.id) && r.status === 0);
    expect(mine.length, '我的预约应包含刚创建的预约').toBeGreaterThan(0);
    const reservationId = mine[0].id;

    // 取消
    const cancelRes = await request.post(
      `/api/parking/reserve/cancel?reservationId=${reservationId}&userId=1001`,
      { headers: api.auth(token) },
    );
    expect((await cancelRes.json()).code, '取消预约应成功').toBe(200);

    // 取消后不应再出现在进行中预约列表
    const afterRes = await request.get('/api/parking/reserve/my?userId=1001', {
      headers: api.auth(token),
    });
    const after = await afterRes.json();
    const stillActive = (after.data || []).filter(
      (r) => String(r.id) === String(reservationId) && r.status === 0,
    );
    expect(stillActive.length, '取消后不应仍为进行中').toBe(0);
  });

  test('重复取消应被拒绝', async ({ request }) => {
    const start = tomorrowAt(14);
    const end = tomorrowAt(15);
    await request.post(
      `/api/parking/reserve?spaceId=${space.id}&userId=1002&startTime=${encodeURIComponent(start)}&endTime=${encodeURIComponent(end)}`,
      { headers: api.auth(token) },
    );
    const myRes = await request.get('/api/parking/reserve/my?userId=1002', {
      headers: api.auth(token),
    });
    const mine = ((await myRes.json()).data || []).filter(
      (r) => String(r.spaceId) === String(space.id) && r.status === 0,
    );
    expect(mine.length).toBeGreaterThan(0);
    const reservationId = mine[0].id;

    const first = await request.post(
      `/api/parking/reserve/cancel?reservationId=${reservationId}&userId=1002`,
      { headers: api.auth(token) },
    );
    expect((await first.json()).code).toBe(200);

    const second = await request.post(
      `/api/parking/reserve/cancel?reservationId=${reservationId}&userId=1002`,
      { headers: api.auth(token) },
    );
    const secondBody = await second.json();
    expect(secondBody.code, '重复取消应被拒绝').not.toBe(200);
  });

  test('时间边界：结束早于开始、超时长、过去时间均返回 5206', async ({ request }) => {
    const cases = [
      { name: '结束早于开始', start: tomorrowAt(12), end: tomorrowAt(11) },
      { name: '超过时长上限', start: tomorrowAt(10), end: tomorrowAt(14) },
      { name: '开始时间早于当前', start: '2020-01-01 10:00:00', end: '2020-01-01 11:00:00' },
    ];

    for (const c of cases) {
      const res = await request.post(
        `/api/parking/reserve?spaceId=${space.id}&userId=1003&startTime=${encodeURIComponent(c.start)}&endTime=${encodeURIComponent(c.end)}`,
        { headers: api.auth(token) },
      );
      const body = await res.json();
      expect(body.code, `${c.name} 应返回 5206，实际 ${body.code}/${body.message}`).toBe(5206);
    }
  });
});
