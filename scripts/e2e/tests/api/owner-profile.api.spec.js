// @ts-check
/**
 * 业主端新增能力（支付记录 / 订单详情 / 意见反馈 / 资料编辑）的接口回归。
 *
 * 重点不是「接口能通」，而是**归属隔离**：
 *   - 反馈列表按 userId 查询曾是 IDOR（业主可遍历他人反馈）
 *   - 反馈提交曾信任 body.userId（可替他人投诉）
 *   - 订单详情/算费只能看本人车辆订单
 *   - 头像图片必须公网可读（<img> 不带 JWT），但文件名要挡目录穿越
 */
const { test, expect } = require('@playwright/test');
const { login, auth, sql } = require('../support/api');

const OWNER = { username: 'zhangsan', password: 'user123' };
const OWNER_ID = 1001;
const OTHER_ID = 1002;

let token;

test.beforeAll(async ({ request }) => {
  token = await login(request, OWNER);
});

test.describe('业主端 · 反馈归属', () => {
  test('我的反馈：伪造他人 userId 也只返回本人记录', async ({ request }) => {
    const res = await request.get(`/api/notify/feedback/my?userId=${OTHER_ID}&current=1&size=50`, {
      headers: auth(token),
    });
    const body = await res.json();
    expect(body.code).toBe(200);
    const records = body.data.records || [];
    expect(records.length).toBeGreaterThan(0);
    // 关键：全部是本人，说明网关注入的身份覆盖了查询参数
    expect(records.every((f) => Number(f.userId) === OWNER_ID)).toBeTruthy();
    expect(records.some((f) => Number(f.userId) === OTHER_ID)).toBeFalsy();
  });

  test('提交反馈：body 里的他人 userId 被忽略，落库为本人', async ({ request }) => {
    const title = `API-FB-${Date.now()}`;
    try {
      const res = await request.post('/api/notify/feedback', {
        headers: auth(token),
        data: { userId: OTHER_ID, type: 2, title, content: 'API 用例：B2 层照明损坏，请尽快处理' },
      });
      expect((await res.json()).code).toBe(200);

      // 标题是 ASCII，可直接查；不要用中文做 SQL 参数（cmd.exe 会转成 ??）
      const ownerId = sql(
        `SELECT user_id FROM feedback WHERE title = '${title}' ORDER BY id DESC LIMIT 1`,
      );
      expect(Number(ownerId)).toBe(OWNER_ID);
      // status 由服务端置 0，不接受客户端指定
      expect(sql(`SELECT status FROM feedback WHERE title = '${title}'`)).toBe('0');
    } finally {
      sql(`DELETE FROM feedback WHERE title = '${title}'`);
    }
  });

  test('提交反馈：内容为空返回 4001', async ({ request }) => {
    const res = await request.post('/api/notify/feedback', {
      headers: auth(token),
      data: { type: 1, title: 'blank' },
    });
    const body = await res.json();
    expect(body.code).toBe(4001);
    expect(body.message).toContain('反馈内容不能为空');
  });
});

test.describe('业主端 · 订单详情归属', () => {
  test('查看他人车辆订单返回「无权查看」', async ({ request }) => {
    // 注意：sql() 经 cmd.exe 执行，语句必须单行 —— 多行会被截断成半句
    const otherOrderNo = sql(
      `SELECT o.order_no FROM parking_order o WHERE o.is_deleted = 0 AND o.vehicle_id NOT IN (SELECT id FROM vehicle WHERE owner_id = ${OWNER_ID} AND is_deleted = 0) ORDER BY o.id DESC LIMIT 1`,
    );
    test.skip(!otherOrderNo, '演示库没有他人订单可测');

    const res = await request.get(`/api/order/${otherOrderNo}`, { headers: auth(token) });
    const body = await res.json();
    expect(body.code).not.toBe(200);
    expect(body.message).toContain('无权查看');
  });

  test('本人订单详情返回计费规则可用', async ({ request }) => {
    const ownOrderNo = sql(
      `SELECT o.order_no FROM parking_order o JOIN vehicle v ON v.id = o.vehicle_id WHERE o.is_deleted = 0 AND v.owner_id = ${OWNER_ID} ORDER BY o.id DESC LIMIT 1`,
    );
    test.skip(!ownOrderNo, '演示库没有本人订单可测');

    const detail = await (await request.get(`/api/order/${ownOrderNo}`, { headers: auth(token) })).json();
    expect(detail.code).toBe(200);
    expect(detail.data.orderNo).toBe(ownOrderNo);

    // 详情页要用规则表把 billingRuleId 翻译成计费口径
    const rules = await (await request.get('/api/order/billing-rules', { headers: auth(token) })).json();
    expect(rules.code).toBe(200);
    expect(Array.isArray(rules.data)).toBeTruthy();
    expect(rules.data.length).toBeGreaterThan(0);
  });
});

test.describe('业主端 · 支付记录与头像', () => {
  test('支付记录只返回本人流水', async ({ request }) => {
    const body = await (await request.get('/api/payment/list?current=1&size=50', {
      headers: auth(token),
    })).json();
    expect(body.code).toBe(200);
    const records = body.data.records || [];
    expect(records.length).toBeGreaterThan(0);
    expect(records.every((p) => Number(p.userId) === OWNER_ID)).toBeTruthy();
  });

  test('头像图片公开可读，非法文件名不放行', async ({ request }) => {
    const avatar = sql(`SELECT COALESCE(avatar, '') FROM sys_user WHERE id = ${OWNER_ID}`);
    test.skip(!avatar, '当前用户没有头像可测');

    // 不带 Authorization：浏览器 <img> 就长这样
    const ok = await request.get(avatar);
    expect(ok.status()).toBe(200);
    expect(ok.headers()['content-type']).toContain('image/');

    const bad = await request.get('/api/user/avatar/..%2F..%2Fapplication.yml');
    expect([400, 404]).toContain(bad.status());
  });
});
