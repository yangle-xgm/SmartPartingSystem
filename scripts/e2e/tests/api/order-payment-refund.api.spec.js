// @ts-check
/**
 * 核心链路 E2E：入场 → 出场结算 → 创建支付流水 → 支付成功 → 退款
 *
 * 覆盖此前缺失的端到端用例。注意 createPayment 走网关访问订单内部接口，
 * 必须携带 X-Internal-Token，否则会被网关 403 拦截（表现为 5402）。
 */
const { test, expect } = require('@playwright/test');
const api = require('../support/api');

test.describe('订单出场结算 → 支付 → 退款', () => {
  /** @type {string} */
  let token;
  /** @type {{id: string, spaceNo: string}} */
  let space;

  test.beforeAll(async ({ request }) => {
    token = await api.login(request);
    space = await api.createTestSpace(request, token, 'E2E-PAY');
  });

  test.afterAll(() => {
    if (space) api.cleanupTestData(space.spaceNo);
  });

  test('全链路金额与状态正确流转', async ({ request }) => {
    // 1. 入场建单
    const entryRes = await request.post(
      `/api/order/entry?vehicleId=1&plateNo=E2E888&spaceId=${space.id}&spaceNo=${space.spaceNo}&vehicleType=0`,
      { headers: api.auth(token) },
    );
    const entry = await entryRes.json();
    expect(entry.code, `入场失败: ${entry.message}`).toBe(200);
    const orderNo = entry.data.orderNo;
    const orderId = String(entry.data.id);
    expect(orderNo).toMatch(/^ORD\d+/);

    // 2. 夹具：入场时间回拨 3 小时，制造非 0 金额（真实等待 3 小时不现实）
    api.sql(`UPDATE parking_order SET entry_time = DATE_SUB(NOW(), INTERVAL 3 HOUR) WHERE order_no='${orderNo}'`);

    // 3. 出场结算
    const exitRes = await request.post(`/api/order/exit/${orderNo}`, { headers: api.auth(token) });
    const exit = await exitRes.json();
    expect(exit.code, `出场结算失败: ${exit.message}`).toBe(200);
    expect(exit.data.status).toBe(1);
    expect(Number(exit.data.amount)).toBeGreaterThan(0);
    expect(Number(exit.data.duration)).toBeGreaterThan(0);

    // 4. 创建支付流水：金额以订单应付为准，传入 0.01 应被忽略
    const payRes = await request.post(
      `/api/payment/create?orderId=${orderId}&userId=1&amount=0.01&payType=3`,
      { headers: api.auth(token) },
    );
    const pay = await payRes.json();
    expect(pay.code, `创建支付流水失败: ${pay.message}`).toBe(200);
    expect(Number(pay.data.amount)).toBe(Number(exit.data.amount));
    const payNo = pay.data.payNo;
    expect(payNo).toBeTruthy();

    // 5. 支付成功（内部回调，需内部令牌）
    const okRes = await request.post(
      `/api/payment/internal/success?payNo=${payNo}&transactionId=E2E${Date.now()}`,
      { headers: api.internal(token) },
    );
    expect((await okRes.json()).code, '支付成功回调应返回 200').toBe(200);

    // 6. 订单侧状态已同步为已支付
    const detailRes = await request.get(`/api/order/${orderNo}`, { headers: api.auth(token) });
    const detail = await detailRes.json();
    expect(detail.code).toBe(200);
    expect(detail.data.payStatus, '支付成功后订单应为已支付').toBe(1);

    // 7. 退款
    const refundRes = await request.post(`/api/payment/internal/refund?payNo=${payNo}`, {
      headers: api.internal(token),
    });
    expect((await refundRes.json()).code, '退款应返回 200').toBe(200);

    // 8. 退款后订单不应再处于已支付
    const afterRes = await request.get(`/api/order/${orderNo}`, { headers: api.auth(token) });
    const after = await afterRes.json();
    expect(after.data.payStatus, '退款后订单支付状态应回退为已退款').toBe(2);
  });

  test('同一订单不能重复发起支付', async ({ request }) => {
    // 独立车位，避免与上一个用例的订单状态互相影响
    const own = await api.createTestSpace(request, token, 'E2E-DUP');
    try {
      const entryRes = await request.post(
        `/api/order/entry?vehicleId=1&plateNo=E2E888&spaceId=${own.id}&spaceNo=${own.spaceNo}&vehicleType=0`,
        { headers: api.auth(token) },
      );
      const entry = await entryRes.json();
      expect(entry.code, `入场失败: ${entry.message}`).toBe(200);
      const orderId = String(entry.data.id);
      api.sql(`UPDATE parking_order SET entry_time = DATE_SUB(NOW(), INTERVAL 3 HOUR) WHERE id=${orderId}`);
      await request.post(`/api/order/exit/${entry.data.orderNo}`, { headers: api.auth(token) });

      const first = await request.post(
        `/api/payment/create?orderId=${orderId}&userId=1&amount=1&payType=3`,
        { headers: api.auth(token) },
      );
      const firstBody = await first.json();
      expect(firstBody.code, `首次支付流水应创建成功: ${firstBody.message}`).toBe(200);

      await request.post(
        `/api/payment/internal/success?payNo=${firstBody.data.payNo}&transactionId=E2E${Date.now()}`,
        { headers: api.internal(token) },
      );

      const second = await request.post(
        `/api/payment/create?orderId=${orderId}&userId=1&amount=1&payType=3`,
        { headers: api.auth(token) },
      );
      const secondBody = await second.json();
      expect(secondBody.code, '已支付订单再次发起支付应被拒绝').not.toBe(200);
    } finally {
      api.cleanupTestData(own.spaceNo);
    }
  });
});
