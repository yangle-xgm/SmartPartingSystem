// @ts-check
/**
 * 浏览器端登录辅助：复用页面自身请求的验证码 key，从 redis 取答案。
 * 不改后端、不绕过验证码 —— 走的是与真实用户完全一致的登录路径。
 */
const { expect } = require('@playwright/test');
const { redisGet } = require('./api');

/**
 * 在已打开的登录页完成登录。
 * @param {import('@playwright/test').Page} page
 * @param {{username?: string, password?: string, submitSelector?: string}} [options]
 */
async function uiLogin(page, options = {}) {
  const username = options.username || process.env.E2E_ADMIN_USER || 'admin';
  const password = options.password || process.env.E2E_ADMIN_PASSWORD || 'admin123';

  let captchaKey = '';
  page.on('response', async (response) => {
    if (response.url().includes('/api/user/captcha') && response.ok()) {
      try {
        const body = await response.json();
        captchaKey = body?.data?.captchaKey || '';
      } catch {
        /* 忽略非 JSON 响应 */
      }
    }
  });

  await page.goto('/login');
  await expect.poll(() => captchaKey, { message: '未捕获到验证码 key' }).toBeTruthy();

  const answer = redisGet(`captcha:${captchaKey}`);

  await page.getByPlaceholder('请输入用户名').fill(username);
  await page.getByPlaceholder('请输入密码').fill(password);
  await page.getByPlaceholder('计算结果').fill(answer);

  // 管理端是 Element Plus 按钮（按文案定位）；H5 是原生 <button class="login__submit">
  const submit = options.submitSelector
    ? page.locator(options.submitSelector).first()
    : page.getByRole('button', { name: /登\s*录/ });
  await submit.click();
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 20_000 });
}

module.exports = { uiLogin };
