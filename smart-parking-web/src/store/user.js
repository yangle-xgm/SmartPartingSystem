import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login, getUserInfo, logout, getCaptcha } from '@/api/user'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(null)

  async function loginAction(username, password, captcha, captchaKey) {
    const res = await login(username, password, captcha, captchaKey)
    if (res.code === 200) {
      token.value = res.data.accessToken
      localStorage.setItem('token', res.data.accessToken)
      localStorage.setItem('refreshToken', res.data.refreshToken)
      return true
    }
    throw new Error(res.message)
  }

  async function getUserInfoAction() {
    const res = await getUserInfo()
    if (res.code === 200) {
      userInfo.value = res.data
      return res.data
    }
    throw new Error(res.message)
  }

  async function logoutAction() {
    try {
      await logout()
    } finally {
      token.value = ''
      userInfo.value = null
      localStorage.removeItem('token')
      localStorage.removeItem('refreshToken')
    }
  }

  return {
    token,
    userInfo,
    loginAction,
    getUserInfoAction,
    logoutAction
  }
})
