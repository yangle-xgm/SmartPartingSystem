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

// 响应拦截：统一处理业务码
request.interceptors.response.use(
  (response) => {
    const res = response.data
    // 后端统一返回 { code, message, data }
    if (res && res.code !== undefined) {
      if (res.code === 200) {
        return res
      }
      if (res.code === 401) {
        localStorage.removeItem('token')
        router.push('/login')
        showToast('登录已过期，请重新登录')
        return Promise.reject(new Error(res.message || '未登录'))
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      localStorage.removeItem('token')
      router.push('/login')
    }
    showToast(error.response?.data?.message || error.message || '网络异常')
    return Promise.reject(error)
  }
)

export default request
