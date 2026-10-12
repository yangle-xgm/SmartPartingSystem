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

export function getUserId() {
  // 从token解析或从store获取
  const token = localStorage.getItem('token')
  if (!token) return null
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return payload.userId
  } catch {
    return null
  }
}

export function getRole() {
  // 从token解析角色（0业主 1管理员 2超级管理员）
  const token = localStorage.getItem('token')
  if (!token) return null
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return payload.role
  } catch {
    return null
  }
}
