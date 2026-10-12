import request from '@/utils/request'

// 登录（业主账号）
export function login(username, password) {
  return request({
    url: '/user/login',
    method: 'post',
    params: { username, password }
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

// 当前用户信息
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

// 从 JWT 解析用户ID
export function getUserId() {
  const token = localStorage.getItem('token')
  if (!token) return null
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return payload.userId
  } catch {
    return null
  }
}

// 从 JWT 解析角色（0业主 1管理员 2超管）
export function getRole() {
  const token = localStorage.getItem('token')
  if (!token) return null
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return payload.role
  } catch {
    return null
  }
}
