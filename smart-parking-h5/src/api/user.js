import request from '@/utils/request'

// 登录（业主账号）
// 后端 POST /api/user/login 接收 @RequestBody LoginDTO{username,password,captcha,captchaKey}，
// 因此必须用 data 传 body；且后端强制校验图形验证码，先调 getCaptcha 拿 captchaKey。
export function login(username, password, captcha, captchaKey) {
  return request({
    url: '/user/login',
    method: 'post',
    data: { username, password, captcha, captchaKey }
  })
}

// 图形验证码：返回 { captchaKey, captchaImg }
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

// 当前用户信息（后端 /api/user/info 必传 userId）
export function getUserInfo(userId) {
  return request({
    url: '/user/info',
    method: 'get',
    params: { userId }
  })
}

// 退出登录
export function logout(userId) {
  return request({
    url: '/user/logout',
    method: 'post',
    params: { userId }
  })
}

// 修改本人资料（后端在 role=0 时强制用 X-User-Id 覆盖 dto.id，防越权改他人）
export function updateUser(data) {
  return request({
    url: '/user/update',
    method: 'put',
    data
  })
}

// 上传头像（multipart），返回 /api/user/avatar/xxx.png
export function uploadAvatar(file) {
  const fd = new FormData()
  fd.append('file', file)
  return request({
    url: '/user/upload-avatar',
    method: 'post',
    data: fd,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 小区档案列表（用于把 communityId 显示成真实小区名）
export function getCommunityList() {
  return request({
    url: '/user/community/list',
    method: 'get'
  })
}

/**
 * 解析 JWT payload。
 * base64url 里的 `-` / `_` 会让 atob 抛 InvalidCharacterError；
 * atob 返回二进制字符串，payload 含中文时必须按 UTF-8 解码再 JSON.parse（E2E 报告 P2-9）。
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

// 从 JWT 解析用户ID
export function getUserId() {
  return decodeJwtPayload(localStorage.getItem('token'))?.userId ?? null
}

// 从 JWT 解析角色（0业主 1管理员 2超管）
export function getRole() {
  return decodeJwtPayload(localStorage.getItem('token'))?.role ?? null
}
