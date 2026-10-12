<template>
  <div class="register-container dark">
    <div class="register-card">
      <div class="register-header">
        <h2>小区智慧停车管理系统</h2>
        <p>业主注册</p>
      </div>
      <el-form ref="registerFormRef" :model="registerForm" :rules="registerRules" class="register-form">
        <el-form-item prop="username">
          <el-input
            v-model="registerForm.username"
            placeholder="请输入用户名"
            prefix-icon="User"
            size="large"
          />
        </el-form-item>
        <el-form-item prop="realName">
          <el-input
            v-model="registerForm.realName"
            placeholder="请输入真实姓名"
            prefix-icon="Postcard"
            size="large"
          />
        </el-form-item>
        <el-form-item prop="phone">
          <el-input
            v-model="registerForm.phone"
            placeholder="请输入手机号"
            prefix-icon="Iphone"
            size="large"
            maxlength="11"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="registerForm.password"
            type="password"
            placeholder="请输入密码"
            prefix-icon="Lock"
            size="large"
            show-password
          />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input
            v-model="registerForm.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            prefix-icon="Lock"
            size="large"
            show-password
            @keyup.enter="handleRegister"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="register-btn"
            @click="handleRegister"
          >
            注 册
          </el-button>
        </el-form-item>
      </el-form>
      <div class="register-footer">
        <p>已有账号？<el-link type="primary" @click="router.push('/login')">去登录</el-link></p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '@/api/user'

const router = useRouter()

const registerFormRef = ref(null)
const loading = ref(false)

const registerForm = ref({
  username: '',
  realName: '',
  phone: '',
  password: '',
  confirmPassword: ''
})

const validateConfirm = (rule, value, callback) => {
  if (value !== registerForm.value.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度 3-20 位', trigger: 'blur' }
  ],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度 6-20 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ]
}

const handleRegister = async () => {
  const form = registerFormRef.value
  if (!form) return

  await form.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      await register({
        username: registerForm.value.username,
        password: registerForm.value.password,
        realName: registerForm.value.realName,
        phone: registerForm.value.phone
      })
      ElMessage.success('注册成功，请登录')
      router.push('/login')
    } catch (error) {
      ElMessage.error(error.message || '注册失败')
    } finally {
      loading.value = false
    }
  })
}
</script>

<style lang="scss" scoped>
.register-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background: var(--sp-bg);
  background-image: var(--sp-bg-grad);
}

.register-card {
  width: 400px;
  padding: 40px;
  background: rgba(255, 255, 255, 0.04);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: var(--sp-radius-card);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.25);
}

.register-header {
  text-align: center;
  margin-bottom: 30px;

  h2 {
    margin: 0 0 8px;
    color: var(--sp-text);
    font-size: 22px;
    font-weight: 800;
    letter-spacing: 0.06em;
  }

  p {
    margin: 0;
    color: var(--sp-text-3);
    font-size: 12px;
    letter-spacing: 0.2em;
  }
}

.register-btn {
  width: 100%;
  border-radius: var(--sp-radius-pill);
}

.register-footer {
  text-align: center;
  margin-top: 16px;

  p {
    color: var(--sp-text-2);
    font-size: 13px;
    margin: 0;
  }
}
</style>
