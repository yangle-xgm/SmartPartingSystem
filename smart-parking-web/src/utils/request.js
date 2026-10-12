import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 请求拦截器
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 业务错误码 → 友好提示（覆盖后端 message 时用）
const CODE_MESSAGE = {
  401: '登录已过期，请重新登录',
  403: '您没有权限执行此操作',
  5101: '用户名或密码错误',
  5102: '账号已被禁用，请联系管理员',
  5103: '用户不存在',
  5104: '用户名已存在',
  5201: '车位不存在',
  5202: '车位当前不可预约或已被占用',
  5203: '停车场暂无空闲车位',
  5301: '订单不存在',
  5302: '订单状态异常',
  5303: '该车辆已有进行中的订单',
  5401: '支付记录不存在',
  5402: '支付状态异常',
  5501: '车辆不存在',
  5502: '未识别到注册车辆',
  429: '请求过于频繁，请稍后再试'
}

function handleBusinessError(code, backendMsg) {
  // 401 跳登录
  if (code === 401) {
    localStorage.removeItem('token')
    router.push('/login')
    ElMessage.warning(CODE_MESSAGE[401])
    return
  }
  // 403 弹提示但不跳转
  if (code === 403) {
    ElMessage.error(CODE_MESSAGE[403])
    return
  }
  // 429 限流
  if (code === 429) {
    ElMessage.warning(CODE_MESSAGE[429])
    return
  }
  // 其他业务码：优先用友好映射，没有则用后端 message
  ElMessage.error(CODE_MESSAGE[code] || backendMsg || '操作失败')
}

// 401 时自动用 refreshToken 换新 accessToken，重试一次
let isRefreshing = false
async function doRefresh() {
  const refreshToken = localStorage.getItem('refreshToken')
  if (!refreshToken) return false
  try {
    const resp = await axios.post('/api/user/refresh', {}, { headers: { Authorization: 'Bearer ' + refreshToken } })
    if (resp.data && resp.data.code === 200) {
      localStorage.setItem('token', resp.data.data.accessToken)
      localStorage.setItem('refreshToken', resp.data.data.refreshToken)
      return true
    }
  } catch (e) { /* refresh失败 */ }
  return false
}

// 响应拦截器
request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code !== 200) {
      handleBusinessError(res.code, res.message)
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  async error => {
    const resp = error.response
    const code = resp && resp.status
    const backendMsg = resp && resp.data && resp.data.message
    // 401 且不是登录/刷新接口，尝试用 refreshToken 自动续期
    if (code === 401 && !error.config.url.includes('/user/login') && !error.config.url.includes('/user/refresh') && !isRefreshing) {
      isRefreshing = true
      const ok = await doRefresh()
      isRefreshing = false
      if (ok) {
        error.config.headers['Authorization'] = 'Bearer ' + localStorage.getItem('token')
        return request(error.config)
      }
    }
    handleBusinessError(code, backendMsg)
    return Promise.reject(error)
  }
)

export default request
