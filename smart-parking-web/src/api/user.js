import request from '@/utils/request'

export function login(username, password, captcha, captchaKey) {
  return request({
    url: '/user/login',
    method: 'post',
    data: { username, password, captcha, captchaKey }
  })
}

export function getCaptcha() {
  return request({
    url: '/user/captcha',
    method: 'get'
  })
}

// 业主注册
export function register(data) {
  return request({
    url: '/user/register',
    method: 'post',
    data
  })
}

export function getUserInfo() {
  return request({
    url: '/user/info',
    method: 'get',
    params: { userId: getUserId() }
  })
}

export function logout() {
  return request({
    url: '/user/logout',
    method: 'post',
    params: { userId: getUserId() }
  })
}

export function getUserList(params) {
  return request({
    url: '/user/list',
    method: 'get',
    params
  })
}

export function updateUser(data) {
  return request({
    url: '/user/update',
    method: 'put',
    data
  })
}

/**
 * 解析 JWT payload。
 *
 * 直接 `atob(token.split('.')[1])` 有两个坑（E2E 报告 P2-9）：
 * 1) JWT 使用 base64url 编码，payload 里一旦出现 `-` / `_`，atob 会抛 InvalidCharacterError；
 * 2) atob 返回的是「二进制字符串」，payload 含中文（如 username=张三）时 JSON.parse 会得到乱码甚至解析失败。
 * 因此先还原 base64url → 补 `=` 对齐 → 按字节用 TextDecoder 以 UTF-8 解码。
 */
function decodeJwtPayload(token) {
  if (!token || typeof token !== 'string') return null
  const seg = token.split('.')[1]
  if (!seg) return null
  try {
    let base64 = seg.replace(/-/g, '+').replace(/_/g, '/')
    while (base64.length % 4) base64 += '='
    const binary = atob(base64)
    const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0))
    return JSON.parse(new TextDecoder('utf-8').decode(bytes))
  } catch {
    return null
  }
}

export function getUserId() {
  return decodeJwtPayload(localStorage.getItem('token'))?.userId ?? null
}

export function getRole() {
  // 0 业主 1 物业管理员 2 超级管理员
  return decodeJwtPayload(localStorage.getItem('token'))?.role ?? null
}
