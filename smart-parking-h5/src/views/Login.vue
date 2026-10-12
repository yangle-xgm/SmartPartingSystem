<template>
  <div class="login-page">
    <div class="login-header">
      <div class="logo">🅿️</div>
      <h1>智慧停车</h1>
      <p>Smart Parking · 业主端</p>
    </div>

    <div class="login-form">
      <van-cell-group inset>
        <van-field
          v-model="form.username"
          label="用户名"
          placeholder="请输入用户名"
          clearable
          left-icon="contact"
        />
        <van-field
          v-model="form.password"
          type="password"
          label="密码"
          placeholder="请输入密码"
          clearable
          left-icon="lock"
          @keyup.enter="handleLogin"
        />
      </van-cell-group>

      <div class="btn-area">
        <van-button type="primary" block round :loading="loading" @click="handleLogin">
          登 录
        </van-button>
        <van-button plain block round style="margin-top: 12px;" @click="showRegister = true">
          注册业主账号
        </van-button>
      </div>
    </div>

    <!-- 注册弹窗 -->
    <van-popup v-model:show="showRegister" position="bottom" round :style="{ height: '60%' }">
      <div class="register-panel">
        <h3>注册业主账号</h3>
        <van-cell-group inset>
          <van-field v-model="regForm.username" label="用户名" placeholder="4-20位字母数字" clearable />
          <van-field v-model="regForm.password" type="password" label="密码" placeholder="6-20位" clearable />
          <van-field v-model="regForm.realName" label="真实姓名" placeholder="请输入姓名" clearable />
          <van-field v-model="regForm.phone" label="手机号" placeholder="11位手机号" clearable />
        </van-cell-group>
        <div class="btn-area">
          <van-button type="primary" block round :loading="registering" @click="handleRegister">
            注 册
          </van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showSuccessToast } from 'vant'
import { login, register } from '@/api/user'

const router = useRouter()
const loading = ref(false)
const showRegister = ref(false)
const registering = ref(false)

const form = reactive({ username: '', password: '' })
const regForm = reactive({ username: '', password: '', realName: '', phone: '' })

const handleLogin = async () => {
  if (!form.username || !form.password) {
    showToast('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const res = await login(form.username, form.password)
    if (res.code === 200) {
      localStorage.setItem('token', res.data.token)
      showSuccessToast('登录成功')
      router.push('/home')
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  if (!regForm.username || !regForm.password) {
    showToast('请填写用户名和密码')
    return
  }
  registering.value = true
  try {
    const res = await register({
      username: regForm.username,
      password: regForm.password,
      realName: regForm.realName,
      phone: regForm.phone,
      role: 0
    })
    if (res.code === 200) {
      showSuccessToast('注册成功，请登录')
      showRegister.value = false
      form.username = regForm.username
      form.password = regForm.password
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    registering.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  background: linear-gradient(160deg, #1989fa 0%, #2a6ef2 55%, #1e4fd8 100%);
  padding-top: 90px;
}

.login-header {
  text-align: center;
  color: #fff;
  margin-bottom: 40px;
}

.logo {
  font-size: 56px;
  margin-bottom: 8px;
}

.login-header h1 {
  font-size: 26px;
  font-weight: 600;
  letter-spacing: 4px;
}

.login-header p {
  font-size: 13px;
  opacity: 0.85;
  margin-top: 6px;
}

.login-form {
  padding: 0 24px;
}

.btn-area {
  padding: 24px 24px 0;
}

.register-panel {
  padding: 24px 0 30px;
}

.register-panel h3 {
  text-align: center;
  margin-bottom: 20px;
}
</style>
