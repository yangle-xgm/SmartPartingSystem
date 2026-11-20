// @ts-check
/**
 * 业主自助服务接口契约（本轮新增）：
 *   1. 月卡充值：报价 → 下单 → 建支付流水 → 核销续期，且重复支付/越权支付被拒
 *      （月卡与停车订单共用 payment_record，收入统计/发票/对账才不漏账）
 *   2. 车位共享：业主只能开关自己名下的车位，收益按 7:3 分润
 *   3. 车位预约：预约不得篡改车位归属人，前端传入的 userId 不可信
 *
 * 这些能力此前「后端有接口、前端无入口」，回归用例保证两端契约同时成立。
 */
const { test, expect } = require('@playwright/test');
const { login, auth, sql, createTestSpace, cleanupTestData } = require('../support/api');

const OWNER = { username: 'zhangsan', password: 'user123' };   // 1001 社区1
const OTHER = { username: 'zhaoliu', password: 'user123' };    // 1004 社区2

/** 张三的月卡车（type=2），带月卡到期时间 */
const VEHICLE_ID = 4;

test.describe('月卡充值 · 报价与支付', () => {
  test('按 billing_rule 月租价计价，3 个月为单价的 3 倍', async ({ request }) => {
    const token = await login(request, OWNER);

    const res = await request.get('/api/vehicle/monthly/quote', {
      headers: auth(token),
      params: { vehicleId: VEHICLE_ID, months: 3 },
    });
    const body = await res.json();
    expect(body.code).toBe(200);

    const { unitPrice, amount, months, expireBefore, expireAfter } = body.data;
    expect(months).toBe(3);
    expect(unitPrice).toBeGreaterThan(0);
    // 金额必须等于单价 × 月数，避免前端算价与后端不一致
    expect(Number(amount)).toBeCloseTo(unitPrice * 3, 2);
    // 续费基准优先取「当前未过期的到期时间」，而不是 now
    if (expireBefore) {
      expect(new Date(expireAfter).getTime()).toBeGreaterThan(new Date(expireBefore).getTime());
    }
  });

  test('非法月数被拒绝（只允许 1/3/6/12）', async ({ request }) => {
    const token = await login(request, OWNER);
    const res = await request.get('/api/vehicle/monthly/quote', {
      headers: auth(token),
      params: { vehicleId: VEHICLE_ID, months: 5 },
    });
    const body = await res.json();
    expect(body.code).toBe(400);
    expect(body.message).toContain('1/3/6/12');
  });

  test('支付成功后月卡有效期延长，重复支付被拒', async ({ request }) => {
    const token = await login(request, OWNER);
    const original = sql(`SELECT monthly_expire FROM vehicle WHERE id=${VEHICLE_ID}`);
    expect(original, '测试车辆应已有月卡到期时间').not.toBe('');

    let monthlyOrderId = '';
    try {
      // 1) 下单：锁定月单价与充值后的到期承诺
      const created = await request.post('/api/vehicle/monthly/create', {
        headers: auth(token),
        params: { vehicleId: VEHICLE_ID, months: 1, payType: 1 },
      });
      const createBody = await created.json();
      expect(createBody.code).toBe(200);
      monthlyOrderId = createBody.data.id;
      expect(monthlyOrderId, '下单应返回月卡订单ID').toBeTruthy();
      expect(createBody.data.payStatus).toBe(0);

      // 2) 建支付流水：月卡纳入 payment_record（收入统计/发票/对账的唯一口径），
      //    金额必须由车辆服务的订单决定，不取前端传值
      const payCreated = await request.post('/api/payment/monthly/create', {
        headers: auth(token),
        params: { monthlyOrderId, payType: 1 },
      });
      const payBody = await payCreated.json();
      expect(payBody.code, `建支付流水失败: ${payBody.message}`).toBe(200);
      expect(payBody.data.bizType).toBe(2);
      expect(Number(payBody.data.amount)).toBe(Number(createBody.data.amount));
      const payNo = payBody.data.payNo;
      expect(payNo, '建流水应返回支付流水号').toBeTruthy();

      // 3) 核销：paymentSuccess 按月卡分派 → 通知车辆服务续期
      const paid = await request.post('/api/payment/wechat/pay', {
        headers: auth(token),
        params: { payNo },
      });
      const paidBody = await paid.json();
      expect(paidBody.code, `支付失败: ${paidBody.message}`).toBe(200);

      // 4) 库里到期时间必须与下单时的承诺一致（未经支付不得延长）
      const after = sql(`SELECT DATE_FORMAT(monthly_expire,'%Y-%m-%d %H:%i:%s') FROM vehicle WHERE id=${VEHICLE_ID}`);
      expect(new Date(after.replace(' ', 'T')).getTime())
        .toBe(new Date(createBody.data.expireAfter).getTime());

      // 5) 幂等：已支付的月卡订单再次建流水必须被拒，且不能再次延长
      const again = await request.post('/api/payment/monthly/create', {
        headers: auth(token),
        params: { monthlyOrderId, payType: 1 },
      });
      const againBody = await again.json();
      expect(againBody.code).toBe(5402);
      const afterAgain = sql(`SELECT DATE_FORMAT(monthly_expire,'%Y-%m-%d %H:%i:%s') FROM vehicle WHERE id=${VEHICLE_ID}`);
      expect(afterAgain).toBe(after);
    } finally {
      // 还原月卡到期时间并清掉本轮产生的订单与流水，保持演示库基线
      sql(`UPDATE vehicle SET monthly_expire='${original}', type=2 WHERE id=${VEHICLE_ID}`);
      sql(`DELETE FROM payment_record WHERE biz_type=2 AND biz_id IN (SELECT id FROM monthly_card_order WHERE vehicle_id=${VEHICLE_ID})`);
      sql(`DELETE FROM monthly_card_order WHERE vehicle_id=${VEHICLE_ID}`);
    }
  });

  test('越权：不能查看或支付他人车辆的月卡', async ({ request }) => {
    const otherToken = await login(request, OTHER);

    const quote = await request.get('/api/vehicle/monthly/quote', {
      headers: auth(otherToken),
      params: { vehicleId: VEHICLE_ID, months: 1 },
    });
    expect((await quote.json()).code).toBe(403);

    const create = await request.post('/api/vehicle/monthly/create', {
      headers: auth(otherToken),
      params: { vehicleId: VEHICLE_ID, months: 1, payType: 1 },
    });
    expect((await create.json()).code).toBe(403);

    // 张三建一笔待支付月卡订单，赵六不得替它创建支付流水（否则可用他人流水续自己的卡）
    const token = await login(request, OWNER);
    const mine = await request.post('/api/vehicle/monthly/create', {
      headers: auth(token),
      params: { vehicleId: VEHICLE_ID, months: 1, payType: 1 },
    });
    const mineBody = await mine.json();
    expect(mineBody.code).toBe(200);
    try {
      const cross = await request.post('/api/payment/monthly/create', {
        headers: auth(otherToken),
        params: { monthlyOrderId: mineBody.data.id, payType: 1 },
      });
      expect((await cross.json()).code).toBe(403);
    } finally {
      sql(`DELETE FROM payment_record WHERE biz_type=2 AND biz_id=${mineBody.data.id}`);
      sql(`DELETE FROM monthly_card_order WHERE id=${mineBody.data.id}`);
    }
  });
});

test.describe('车位共享 · 归属校验与分润', () => {
  test('业主可开关自己名下车位，跨用户操作被拒', async ({ request }) => {
    const token = await login(request, OWNER);
    const otherToken = await login(request, OTHER);

    // A-001 属于 zhangsan(1001)
    const spaceId = sql(`SELECT id FROM parking_space WHERE space_no='A-001' LIMIT 1`);
    const before = sql(`SELECT is_shared FROM parking_space WHERE id=${spaceId}`);

    try {
      const off = await request.put(`/api/parking/space/${spaceId}/share`, {
        headers: auth(token),
        params: { isShared: 0 },
      });
      expect((await off.json()).code).toBe(200);
      expect(sql(`SELECT is_shared FROM parking_space WHERE id=${spaceId}`)).toBe('0');

      const on = await request.put(`/api/parking/space/${spaceId}/share`, {
        headers: auth(token),
        params: { isShared: 1 },
      });
      expect((await on.json()).code).toBe(200);
      expect(sql(`SELECT is_shared FROM parking_space WHERE id=${spaceId}`)).toBe('1');

      // 跨用户：赵六改张三的车位必须 403（历史实现无归属校验，任何登录用户都能改）
      const cross = await request.put(`/api/parking/space/${spaceId}/share`, {
        headers: auth(otherToken),
        params: { isShared: 0 },
      });
      expect((await cross.json()).code).toBe(403);
      expect(sql(`SELECT is_shared FROM parking_space WHERE id=${spaceId}`)).toBe('1');
    } finally {
      sql(`UPDATE parking_space SET is_shared=${before} WHERE id=${spaceId}`);
    }
  });

  test('我的车位只返回本人归属车位', async ({ request }) => {
    const token = await login(request, OWNER);
    const res = await request.get('/api/parking/space/my', { headers: auth(token) });
    const body = await res.json();
    expect(body.code).toBe(200);
    expect(Array.isArray(body.data)).toBe(true);
    for (const s of body.data) {
      expect(String(s.ownerId)).toBe('1001');
    }
  });

  test('共享收益按 owner_ratio 汇总，跨用户不可读', async ({ request }) => {
    const token = await login(request, OWNER);
    const otherToken = await login(request, OTHER);

    const mine = await request.get('/api/payment/share-income/my', { headers: auth(token) });
    const mineBody = await mine.json();
    expect(mineBody.code).toBe(200);
    const { total, count, records } = mineBody.data;
    expect(count).toBe(records.length);

    // 汇总值必须等于明细之和（前端直接展示 total，不能各算各的）
    const sum = records.reduce((s, r) => s + Number(r.ownerAmount || 0), 0);
    expect(Number(total)).toBeCloseTo(sum, 2);

    // 业主身份下传他人 ownerId 无效，仍只返回本人收益
    const spoof = await request.get('/api/payment/share-income/my', {
      headers: auth(otherToken),
      params: { ownerId: 1001 },
    });
    const spoofBody = await spoof.json();
    expect(spoofBody.code).toBe(200);
    for (const r of spoofBody.data.records) {
      expect(String(r.ownerId)).toBe('1004');
    }
  });
});

test.describe('车位预约 · 归属与身份', () => {
  test('预约不篡改车位归属人，取消后归属仍在', async ({ request }) => {
    const adminToken = await login(request);
    const token = await login(request, OWNER);
    const { id: spaceId, spaceNo } = await createTestSpace(request, adminToken, 'E2ER');

    // 给测试车位设一个归属人，模拟业主固定车位
    sql(`UPDATE parking_space SET owner_id=1001, type=0 WHERE id=${spaceId}`);

    try {
      const reserve = await request.post('/api/parking/reserve', {
        headers: auth(token),
        params: {
          spaceId,
          startTime: '2026-11-19 10:00:00',
          endTime: '2026-11-19 11:00:00',
        },
      });
      expect((await reserve.json()).code).toBe(200);

      // 预约只改状态，归属人必须保持 1001（历史实现会把 owner_id 改成预约人）
      expect(sql(`SELECT status FROM parking_space WHERE id=${spaceId}`)).toBe('2');
      expect(sql(`SELECT owner_id FROM parking_space WHERE id=${spaceId}`)).toBe('1001');

      const my = await request.get('/api/parking/reserve/my', { headers: auth(token) });
      const myBody = await my.json();
      const record = (myBody.data || []).find((r) => String(r.spaceId) === String(spaceId));
      expect(record, '预约记录应出现在「我的预约」里').toBeTruthy();

      const cancel = await request.post('/api/parking/reserve/cancel', {
        headers: auth(token),
        params: { reservationId: record.id },
      });
      expect((await cancel.json()).code).toBe(200);
      expect(sql(`SELECT status FROM parking_space WHERE id=${spaceId}`)).toBe('0');
      // 取消后归属人仍必须保留
      expect(sql(`SELECT owner_id FROM parking_space WHERE id=${spaceId}`)).toBe('1001');
    } finally {
      cleanupTestData(spaceNo);
    }
  });

  test('预约时伪造 userId 无效，落库以登录身份为准', async ({ request }) => {
    const adminToken = await login(request);
    const token = await login(request, OWNER);
    const { id: spaceId, spaceNo } = await createTestSpace(request, adminToken, 'E2EU');

    try {
      const reserve = await request.post('/api/parking/reserve', {
        headers: auth(token),
        params: {
          spaceId,
          userId: 1004,        // 伪造他人身份
          startTime: '2026-11-19 14:00:00',
          endTime: '2026-11-19 15:00:00',
        },
      });
      expect((await reserve.json()).code).toBe(200);

      const owner = sql(`SELECT user_id FROM reservation WHERE space_id=${spaceId} ORDER BY id DESC LIMIT 1`);
      expect(owner, '预约人必须是登录身份 1001，而不是请求参数里的 1004').toBe('1001');
    } finally {
      cleanupTestData(spaceNo);
    }
  });

  test('超出 2 小时的预约被拒', async ({ request }) => {
    const adminToken = await login(request);
    const token = await login(request, OWNER);
    const { id: spaceId, spaceNo } = await createTestSpace(request, adminToken, 'E2ET');

    try {
      const res = await request.post('/api/parking/reserve', {
        headers: auth(token),
        params: {
          spaceId,
          startTime: '2026-11-19 16:00:00',
          endTime: '2026-11-19 19:30:00',
        },
      });
      const body = await res.json();
      expect(body.code).toBe(5206);
      expect(body.message).toContain('小时');
      // 被拒的预约不能留下状态副作用
      expect(sql(`SELECT status FROM parking_space WHERE id=${spaceId}`)).toBe('0');
    } finally {
      cleanupTestData(spaceNo);
    }
  });
});
