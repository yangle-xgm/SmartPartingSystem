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
// 注意：5101 不再硬覆盖（DEF-09）——后端用它表示「用户名或密码错误 / 验证码错误」，
// 硬映射会让「验证码错误」显示成「用户名或密码错误」，把用户引向错误方向。
const CODE_MESSAGE = {
  401: '登录已过期，请重新登录',
  403: '您没有权限执行此操作',
  409: '数据已存在，请勿重复提交',
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
  4001: '数据不合法，请检查输入',
  4002: '操作未生效，数据可能已被删除',
  429: '请求过于频繁，请稍后再试'
}

const SERVER_BUSY = '服务繁忙，请稍后再试'
const NETWORK_ERROR = '网络异常，请检查网络连接'

// 后端把数据库/框架异常原文塞进 message 时（DEF-02），前端不展示，只写控制台
const INTERNAL_MSG = /###|SQLState|SQL\b|MyBatis|java\.|Exception|Out of range|Data truncation/i

// 语义固定的状态码：这几类必须用前端文案（后端可能返回英文/框架原文，或语义不足）
const FIXED_MESSAGE_CODES = new Set([401, 403, 429])

/**
 * 提示文案优先级（D5）：
 *  1. 401/403/429 → 固定文案（状态语义稳定，且不暴露细节）
 *  2. 后端 message 干净（无 SQL/堆栈、长度合理）→ 直接用后端文案
 *     后端的「车位编号已存在，请换一个编号」「月租价格不能为负数」远比字典里的
 *     「数据已存在」「数据不合法」有用，历史实现用字典先命中把它们全部抹平了
 *  3. 后端 message 缺失或不适合展示 → 字典兜底 → 通用兜底
 */
function safeMessage(code, backendMsg) {
  if (FIXED_MESSAGE_CODES.has(code)) return CODE_MESSAGE[code]
  const clean = !!backendMsg && !INTERNAL_MSG.test(backendMsg) && backendMsg.length <= 80
  if (clean) return backendMsg
  if (CODE_MESSAGE[code]) return CODE_MESSAGE[code]
  return code >= 500 ? SERVER_BUSY : '操作失败，请稍后重试'
}

// 401 提示节流：并发续期失败时只弹一次
let lastAuthToastAt = 0
function notifyAuthExpired() {
  const now = Date.now()
  if (now - lastAuthToastAt < 1500) return
  lastAuthToastAt = now
  ElMessage.warning(CODE_MESSAGE[401])
}

function handleBusinessError(code, backendMsg) {
  // 401 跳登录
  if (code === 401) {
    localStorage.removeItem('token')
    router.push('/login')
    notifyAuthExpired()
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
  // 5xx / 系统异常：内部细节只进控制台，提示语走 safeMessage（干净的业务文案仍可展示）
  if (code >= 500) {
    console.error('[api] server error:', backendMsg)
    ElMessage.error(safeMessage(code, backendMsg))
    return
  }
  ElMessage.error(safeMessage(code, backendMsg))
}

// 401 时自动用 refreshToken 换新 accessToken，并重放原请求。
// DEF-03：原实现只用布尔量挡住重复刷新，后续 401 直接登出；这里改为"共享同一个刷新 Promise"，
// 所有并发 401 都等这次刷新结果，成功后各自重放，只有刷新失败才统一登出。
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
  } catch (e) { /* refresh 失败走统一登出 */ }
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
  config.headers['Authorization'] = 'Bearer ' + localStorage.getItem('token')
  return request(config)
}

// 响应拦截器
request.interceptors.response.use(
  async response => {
    const res = response.data
    if (!res || res.code === undefined) return res
    if (res.code !== 200) {
      // HTTP 200 + 业务码 401：同样尝试续期后重放
      if (res.code === 401 && canRetry(response.config)) {
        const ok = await refreshAccessToken()
        if (ok) return replay(response.config)
      }
      // 支持 config.silent：辅助性查询（如计费规则表）失败时由页面自己降级，
      // 不再弹全局红条提示；但 401 仍必须走登录失效逻辑。
      const silent = response.config && response.config.silent === true
      if (!silent || res.code === 401) {
        handleBusinessError(res.code, res.message)
      }
      // D2：标记"已由请求层提示过"，页面 catch 里再调用 toastOnce 不会重复弹窗
      const bizErr = new Error(res.message || '请求失败')
      bizErr.code = res.code
      bizErr.__notified = !silent
      return Promise.reject(bizErr)
    }
    return res
  },
  async error => {
    const resp = error.response
    const code = resp && resp.status
    const backendMsg = resp && resp.data && resp.data.message

    if (code === 401 && canRetry(error.config)) {
      const ok = await refreshAccessToken()
      if (ok) return replay(error.config)
      handleBusinessError(401, backendMsg)
      error.__notified = true
      return Promise.reject(error)
    }

    if (code === 401) {
      handleBusinessError(401, backendMsg)
      error.__notified = true
      return Promise.reject(error)
    }

    if (!resp) {
      // 超时 / 断网 / 服务未启动
      console.error('[api] network error:', error.message)
      ElMessage.error(NETWORK_ERROR)
      error.__notified = true
      return Promise.reject(error)
    }

    handleBusinessError(code, backendMsg)
    error.__notified = true
    return Promise.reject(error)
  }
)

/**
 * 页面层提示：请求层已经弹过就跳过，避免同一次失败弹两条（D2）。
 * 非接口异常（前端校验、脚本错误）没有 __notified 标记，仍按 fallback 提示。
 */
export function toastOnce(err, fallback = '操作失败') {
  if (err && err.__notified) {
    console.warn('[api] 已由请求层提示：', err.message)
    return
  }
  ElMessage.error((err && err.message) || fallback)
}

export default request
