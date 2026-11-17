// @ts-check
/**
 * E2E 支撑工具：登录、验证码、SQL 夹具、内部令牌。
 *
 * 本机开发环境的约定（可在环境变量中覆盖）：
 *   MYSQL_CONTAINER / MYSQL_PASSWORD / REDIS_CONTAINER / SP_INTERNAL_TOKEN
 */
const { execSync } = require('node:child_process');
const { expect } = require('@playwright/test');

const MYSQL_CONTAINER = process.env.MYSQL_CONTAINER || 'sp-mysql';
const MYSQL_PASSWORD = process.env.MYSQL_PASSWORD || '21491879';
const REDIS_CONTAINER = process.env.REDIS_CONTAINER || 'sp-redis';
const DB_NAME = process.env.SP_DB_NAME || 'smart_parking';

/** 内部接口令牌：经网关访问 /api/**‌/internal/** 时必须携带 */
const INTERNAL_TOKEN = process.env.SP_INTERNAL_TOKEN || 'SmartParking-Internal-2026';

const ADMIN_USER = process.env.E2E_ADMIN_USER || 'admin';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD || 'admin123';

/** 读取 redis 中的验证码答案（登录/注册都要用） */
function redisGet(key) {
  const out = execSync(`docker exec ${REDIS_CONTAINER} redis-cli GET "${key}"`, { encoding: 'utf8' });
  return out.trim();
}

/** 执行一段 SQL，返回裸文本结果（-N 去掉表头） */
function sql(statement) {
  const cmd = `docker exec ${MYSQL_CONTAINER} mysql -uroot -p${MYSQL_PASSWORD} ${DB_NAME} -N -e ${JSON.stringify(statement)}`;
  return execSync(cmd, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
}

/** 走网关登录，返回 accessToken */
async function login(request, options = {}) {
  const username = options.username || ADMIN_USER;
  const password = options.password || ADMIN_PASSWORD;

  const captchaRes = await request.get('/api/user/captcha');
  expect(captchaRes.ok(), '验证码接口应可访问').toBeTruthy();
  const captchaBody = await captchaRes.json();
  expect(captchaBody.code, `获取验证码失败: ${captchaBody.message}`).toBe(200);

  const key = captchaBody.data.captchaKey;
  const answer = redisGet(`captcha:${key}`);

  const loginRes = await request.post('/api/user/login', {
    data: { username, password, captcha: answer, captchaKey: key },
  });
  const body = await loginRes.json();
  expect(body.code, `登录失败: ${body.message}`).toBe(200);
  return body.data.accessToken;
}

/** 带 Bearer 的请求头 */
function auth(token) {
  return { Authorization: `Bearer ${token}` };
}

/** 带内部令牌的请求头（访问 /internal/ 接口时用） */
function internal(token) {
  return { ...auth(token), 'X-Internal-Token': INTERNAL_TOKEN };
}

/** 创建一辆测试用车位，返回 { id, spaceNo } */
async function createTestSpace(request, token, prefix = 'E2E') {
  const spaceNo = `${prefix}-${Date.now().toString().slice(-8)}`;
  const res = await request.post('/api/parking/space', {
    headers: auth(token),
    data: {
      spaceNo,
      area: 'ZZ',
      floor: 'B9',
      type: 0,
      status: 0,
      monthPrice: 100.0,
      isShared: 0,
      communityId: 1,
    },
  });
  const body = await res.json();
  expect(body.code, `创建测试车位失败: ${body.message}`).toBe(200);
  const id = sql(`SELECT id FROM parking_space WHERE space_no='${spaceNo}' AND is_deleted=0 LIMIT 1`);
  expect(id, '测试车位应能从库里查到').not.toBe('');
  // 雪花 ID 超过 2^53，必须保持字符串，转 Number 会丢精度导致后续请求 404
  return { id, spaceNo };
}

/** 物理清理测试数据（车位/订单/支付流水/预约），避免污染演示库 */
function cleanupTestData(spaceNo) {
  sql(`DELETE FROM payment_record WHERE order_id IN (SELECT id FROM parking_order WHERE space_no='${spaceNo}')`);
  sql(`DELETE FROM parking_order WHERE space_no='${spaceNo}'`);
  sql(`DELETE FROM reservation WHERE space_id IN (SELECT id FROM parking_space WHERE space_no='${spaceNo}')`);
  sql(`DELETE FROM parking_space WHERE space_no='${spaceNo}'`);
}

module.exports = {
  INTERNAL_TOKEN,
  ADMIN_USER,
  ADMIN_PASSWORD,
  redisGet,
  sql,
  login,
  auth,
  internal,
  createTestSpace,
  cleanupTestData,
};
