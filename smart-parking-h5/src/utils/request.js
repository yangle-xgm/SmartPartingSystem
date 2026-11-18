import axios from 'axios'
import { showToast } from 'vant'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 请求拦截：携带 token
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

const SERVER_BUSY = '服务繁忙，请稍后再试'
const NETWORK_ERROR = '网络异常，请检查网络连接'
// 后端异常原文（SQL/堆栈）不在端上展示（DEF-02）
const INTERNAL_MSG = /###|SQLState|SQL\b|MyBatis|java\.|Exception|Out of range|Data truncation/i
/**
 * 真正的「服务端故障」状态码。
 * 注意不能用 `code >= 500` 判断：业务错误码是 5xxx（5201 车位不存在、5402 支付状态异常），
 * 也满足 >= 500，一刀切会把"车位当前不可预约"这类有效文案吞成"服务繁忙"。
 */
const SERVER_ERROR_CODES = new Set([500, 502, 503, 504])

function safeMessage(code, backendMsg) {
  // DEF-01：服务端故障一律用固定文案，不信任后端 message。
  // 网关/框架回的「Internal Server Error」「Bad Gateway」既不命中 INTERNAL_MSG
  // 也不超长，历史上被当成"干净业务文案"直接弹给用户。
  if (SERVER_ERROR_CODES.has(code)) {
    if (backendMsg) console.error('[api] server error:', backendMsg)
    return SERVER_BUSY
  }
  if (!backendMsg) return '请求失败'
  // 含 SQL/堆栈关键字或异常长文本 → 不展示给用户，只写控制台
  if (INTERNAL_MSG.test(backendMsg) || backendMsg.length > 80) {
    return '操作失败，请稍后重试'
  }
  return backendMsg
}

// 401 提示节流，避免并发请求连弹
let lastAuthToastAt = 0
function expireLogin() {
  localStorage.removeItem('token')
  router.push('/login')
  const now = Date.now()
  if (now - lastAuthToastAt < 1500) return
  lastAuthToastAt = now
  showToast('登录已过期，请重新登录')
}

// DEF-03：并发 401 共享同一个刷新 Promise，避免第二个请求直接把人踢下线
let refreshPromise = null
async function doRefresh() {
  const refreshToken = localStorage.getItem('refreshToken')
  if (!refreshToken) return false
  try {
    const resp = await axios.post('/api/user/refresh', {}, { headers: { Authorization: 'Bearer ' + refreshToken } })
    if (resp.data && resp.data.code === 200 && resp.data.data) {
      localStorage.setItem('token', resp.data.data.accessToken)
      localStorage.setItem('refreshToken', resp.data.data.refreshToken)
      return true
    }
  } catch (e) { /* 刷新失败走统一登出 */ }
  return false
}

function refreshAccessToken() {
  if (!refreshPromise) {
    refreshPromise = doRefresh().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

function canRetry(config) {
  if (!config || config.__authRetried) return false
  const url = String(config.url || '')
  return !url.includes('/user/login') && !url.includes('/user/refresh')
}

function replay(config) {
  config.__authRetried = true
  config.headers = config.headers || {}
  config.headers.Authorization = 'Bearer ' + localStorage.getItem('token')
  return request(config)
}

// 响应拦截：统一处理业务码
request.interceptors.response.use(
  async (response) => {
    const res = response.data
    // 后端统一返回 { code, message, data }
    if (res && res.code !== undefined) {
      if (res.code === 200) {
        return res
      }
      if (res.code === 401) {
        if (canRetry(response.config) && (await refreshAccessToken())) {
          return replay(response.config)
        }
        expireLogin()
        const authErr = new Error(res.message || '未登录')
        authErr.__notified = true
        return Promise.reject(authErr)
      }
      // 业务失败由调用页面决定怎么提示，这里只把 message 归一化（避免把 SQL/堆栈透给用户）
      const msg = safeMessage(res.code, res.message)
      const bizErr = new Error(msg)
      bizErr.code = res.code
      // 业务错误的文案由页面决定是否展示，这里不打标记
      bizErr.__notified = false
      return Promise.reject(bizErr)
    }
    return res
  },
  async (error) => {
    const status = error.response?.status
    if (status === 401) {
      if (canRetry(error.config) && (await refreshAccessToken())) {
        return replay(error.config)
      }
      expireLogin()
      error.__notified = true
      return Promise.reject(error)
    }
    if (!error.response) {
      console.error('[api] network error:', error.message)
      showToast(NETWORK_ERROR)
      error.__notified = true
      return Promise.reject(error)
    }
    const backendMsg = error.response?.data?.message
    showToast(safeMessage(status, backendMsg))
    error.__notified = true
    return Promise.reject(error)
  }
)

/**
 * 页面层提示：拦截器已经弹过就跳过，避免同一次失败弹两次（D2）。
 * 业务错误（HTTP 200 + code≠200）拦截器不弹，由页面用本函数展示。
 */
export function toastOnce(err, fallback = '操作失败') {
  if (err && err.__notified) {
    console.warn('[api] 已由请求层提示：', err.message)
    return
  }
  showToast((err && err.message) || fallback)
}

export default request
