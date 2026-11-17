<template>
  <div class="login">
    <div class="login__sky" />

    <div class="login__brand">
      <div class="login__mark">智</div>
      <h1 class="login__title">智慧星河</h1>
      <p class="login__sub">SMART GALAXY · 业主停车服务</p>
    </div>

    <div class="login__card">
      <div class="login__tabs">
        <button
          type="button"
          class="login__tab"
          :class="{ active: mode === 'login' }"
          @click="mode = 'login'"
        >登录</button>
        <button
          type="button"
          class="login__tab"
          :class="{ active: mode === 'register' }"
          @click="mode = 'register'"
        >注册业主账号</button>
      </div>

      <!-- 登录 -->
      <template v-if="mode === 'login'">
        <van-field
          v-model="form.username"
          label="账号"
          placeholder="请输入用户名"
          clearable
          left-icon="contact"
        />
        <van-field
          v-model="form.password"
          type="password"
          label="密码"
          placeholder="请输入密码"
          left-icon="lock"
          @keyup.enter="handleLogin"
        />
        <van-field
          v-model="form.captcha"
          label="验证码"
          placeholder="请输入计算结果"
          left-icon="shield-o"
          @keyup.enter="handleLogin"
        >
          <template #button>
            <img
              v-if="captchaImg"
              :src="captchaImg"
              class="captcha"
              alt="验证码"
              @click="refreshCaptcha"
            />
            <span v-else class="captcha captcha--empty" @click="refreshCaptcha">点击获取</span>
          </template>
        </van-field>

        <button class="h-btn login__submit" type="button" :disabled="loading" @click="handleLogin">
          {{ loading ? '登录中…' : '登 录' }}
        </button>

        <p class="login__hint">首次使用？切换到「注册业主账号」创建账号</p>
      </template>

      <!-- 注册 -->
      <template v-else>
        <van-field v-model="regForm.username" label="用户名" placeholder="4-20 位字母或数字" clearable />
        <van-field v-model="regForm.password" type="password" label="密码" placeholder="6-20 位" />
        <van-field v-model="regForm.confirm" type="password" label="确认密码" placeholder="再次输入密码" />
        <van-field v-model="regForm.realName" label="姓名" placeholder="请输入真实姓名" clearable />
        <van-field v-model="regForm.phone" label="手机号" placeholder="11 位手机号" clearable maxlength="11" />

        <button class="h-btn login__submit" type="button" :disabled="registering" @click="handleRegister">
          {{ registering ? '提交中…' : '注 册' }}
        </button>
        <p class="login__hint">注册后默认为业主身份，可绑定车牌并在线缴费</p>
      </template>
    </div>

    <div class="login__caps">
      <span>AI 车牌识别</span>
      <i />
      <span>IoT 道闸联动</span>
      <i />
      <span>无感在线支付</span>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showSuccessToast } from 'vant'
import { login, register, getCaptcha } from '@/api/user'

const router = useRouter()

const mode = ref('login')
const loading = ref(false)
const registering = ref(false)

const form = reactive({ username: '', password: '', captcha: '', captchaKey: '' })
const regForm = reactive({ username: '', password: '', confirm: '', realName: '', phone: '' })

const captchaImg = ref('')

async function refreshCaptcha() {
  try {
    const res = await getCaptcha()
    if (res.code === 200) {
      captchaImg.value = res.data.captchaImg
      form.captchaKey = res.data.captchaKey
      form.captcha = ''
    }
  } catch { /* 验证码获取失败时允许重试 */ }
}

onMounted(refreshCaptcha)

async function handleLogin() {
  if (!form.username.trim() || !form.password) {
    showToast('请输入用户名和密码')
    return
  }
  if (!form.captcha) {
    showToast('请输入图形验证码')
    return
  }
  loading.value = true
  try {
    // 后端 @RequestBody LoginDTO{username,password,captcha,captchaKey}
    const res = await login(form.username.trim(), form.password, form.captcha, form.captchaKey)
    if (res.code === 200) {
      // 后端返回 accessToken / refreshToken
      localStorage.setItem('token', res.data.accessToken)
      if (res.data.refreshToken) localStorage.setItem('refreshToken', res.data.refreshToken)
      showSuccessToast('登录成功')
      router.replace('/home')
    }
  } catch {
    refreshCaptcha()   // 验证码一次性，失败后换一张
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  const { username, password, confirm, realName, phone } = regForm
  if (!username.trim() || !password) {
    showToast('请填写用户名和密码')
    return
  }
  if (password.length < 6) {
    showToast('密码至少 6 位')
    return
  }
  if (password !== confirm) {
    showToast('两次输入的密码不一致')
    return
  }
  if (!/^1\d{10}$/.test(phone)) {
    showToast('请输入正确的手机号')
    return
  }
  registering.value = true
  try {
    // RegisterDTO 无 role 字段，角色由后端按业主创建
    const res = await register({
      username: username.trim(),
      password,
      realName: realName.trim(),
      phone
    })
    if (res.code === 200) {
      showSuccessToast('注册成功，请登录')
      form.username = username.trim()
      form.password = password
      regForm.username = ''
      regForm.password = ''
      regForm.confirm = ''
      regForm.realName = ''
      regForm.phone = ''
      mode.value = 'login'
      refreshCaptcha()
    }
  } catch { /* 拦截器已提示 */ } finally {
    registering.value = false
  }
}
</script>

<style scoped>
.login {
  position: relative;
  min-height: 100vh;
  padding: calc(env(safe-area-inset-top) + 42px) 20px 32px;
  background: var(--sp-hero-bg);
  overflow: hidden;
}
.login__sky {
  position: absolute;
  inset: 0;
  background: var(--sp-hero-glow);
  pointer-events: none;
}

.login__brand {
  position: relative;
  z-index: 1;
  text-align: center;
  color: var(--sp-hero-text);
  margin-bottom: 24px;
}
.login__mark {
  width: 56px;
  height: 56px;
  margin: 0 auto 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 800;
  color: #2A2214;
  background: var(--sp-grad-gold);
  border-radius: 18px;
  box-shadow: 0 12px 28px rgba(240, 201, 135, 0.3);
}
.login__title {
  font-size: 24px;
  font-weight: 800;
  letter-spacing: 0.06em;
}
.login__sub {
  margin-top: 6px;
  font-size: 11px;
  letter-spacing: 0.16em;
  color: var(--sp-hero-text-2);
}

.login__card {
  position: relative;
  z-index: 1;
  padding: 16px 14px 20px;
  background: var(--sp-card);
  border-radius: 20px;
  box-shadow: 0 20px 44px rgba(3, 8, 20, 0.36);
}

.login__tabs {
  display: flex;
  gap: 6px;
  padding: 3px;
  margin-bottom: 10px;
  background: var(--sp-surface-2);
  border-radius: var(--sp-radius-pill);
}
.login__tab {
  flex: 1;
  height: 32px;
  font-size: 13px;
  color: var(--sp-text-2);
  background: transparent;
  border: none;
  border-radius: var(--sp-radius-pill);
  transition: all var(--sp-dur-micro) var(--sp-ease);
}
.login__tab.active {
  color: var(--sp-text);
  font-weight: 700;
  background: var(--sp-card);
  box-shadow: var(--sp-shadow);
}

.login__submit { margin-top: 18px; }

.login__hint {
  margin-top: 12px;
  font-size: 11px;
  text-align: center;
  color: var(--sp-text-3);
}

.captcha {
  width: 92px;
  height: 34px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid var(--sp-card-border);
  background: var(--sp-surface-2);
}
.captcha--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: var(--sp-brand);
}

.login__caps {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-top: 26px;
  font-size: 11px;
  color: var(--sp-hero-text-2);
}
.login__caps i {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--sp-gold);
  opacity: 0.7;
}
</style>
