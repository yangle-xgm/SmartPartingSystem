<template>
  <div class="login-page dark">
    <!-- L0 氛围光背景 -->
    <div class="ambient" aria-hidden="true"></div>
    <!-- L1 星尘粒子 + 探照灯光照 -->
    <canvas ref="starCanvas" class="stars" aria-hidden="true"></canvas>
    <!-- 自定义光标 -->
    <div ref="dot" class="cursor-dot" aria-hidden="true"></div>
    <div ref="ring" class="cursor-ring" :class="{ 'is-active': ringActive }" aria-hidden="true"></div>

    <!-- 左右分屏 -->
    <div class="login-shell">
      <!-- ===== 左侧：品牌背景板 ===== -->
      <div class="brand-panel" @mouseenter="ringActive = true" @mouseleave="ringActive = false">
        <div class="brand-glow glow-tl" aria-hidden="true"></div>
        <div class="brand-glow glow-br" aria-hidden="true"></div>

        <div class="brand-top">
          <div class="brand-logo"><b>P</b><i>✦</i></div>
          <div class="brand-name">智慧<em>星河</em>&nbsp;SMART&nbsp;GALAXY</div>
        </div>

        <div class="brand-hero">
          <h1 class="brand-title">小区智慧停车<br />管理系统</h1>
          <p class="brand-slogan">让每一次停泊，都有<b>星光</b>指引</p>

          <ul class="brand-features">
            <li>
              <span class="feat-icon">◉</span>
              <div class="feat-text">
                <b>车位实时可视</b>
                <i>地图实时显示空闲/占用/预约状态</i>
              </div>
            </li>
            <li>
              <span class="feat-icon">◉</span>
              <div class="feat-text">
                <b>无感通行</b>
                <i>车牌识别自动抬杆，识别失败人工确认</i>
              </div>
            </li>
            <li>
              <span class="feat-icon">◉</span>
              <div class="feat-text">
                <b>智能计费</b>
                <i>出场自动结算，超时订单自动补偿</i>
              </div>
            </li>
          </ul>
        </div>

        <!-- 车位指示灯网格（实时车位氛围） -->
        <div class="parking-zone">
          <div class="parking-zone-head">
            <span>● 车位实时状态</span>
            <span class="zone-legend"><i class="lg-free"></i>空闲 <i class="lg-used"></i>占用 <i class="lg-book"></i>预约</span>
          </div>
          <div class="parking-grid" aria-hidden="true">
            <div v-for="(cell, i) in parkingCells" :key="i" class="p-cell" :class="cell"></div>
          </div>
        </div>

        <div class="brand-stats">
          <div class="stat">
            <b class="sp-num">486</b>
            <span>总车位</span>
          </div>
          <div class="stat">
            <b class="sp-num">68.5%</b>
            <span>实时利用率</span>
          </div>
          <div class="stat">
            <b class="sp-num gold">¥2,847</b>
            <span>今日收入</span>
          </div>
        </div>

        <p class="brand-footer">让每一次停泊，都有星光指引</p>
      </div>

      <!-- ===== 右侧：登录卡片 ===== -->
      <div class="form-panel" @mouseenter="ringActive = true" @mouseleave="ringActive = false">
        <div class="form-card">
          <div class="form-header">
            <div class="form-icon"><b>P</b><i>✦</i></div>
            <h2>账号登录</h2>
            <p>欢迎回来</p>
          </div>

          <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" class="login-form">
            <el-form-item prop="username">
              <el-input
                v-model="loginForm.username"
                placeholder="请输入用户名"
                prefix-icon="User"
                size="large"
              />
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                prefix-icon="Lock"
                size="large"
                show-password
                @keyup.enter="handleLogin"
              />
            </el-form-item>
            <el-form-item prop="captcha">
              <div style="display:flex;gap:10px;width:100%;">
                <el-input
                  v-model="loginForm.captcha"
                  placeholder="计算结果"
                  prefix-icon="Key"
                  size="large"
                  style="flex:1"
                  @keyup.enter="handleLogin"
                />
                <img :src="captchaImg" @click="refreshCaptcha" title="点击刷新"
                  style="height:40px;cursor:pointer;border-radius:4px;background:#f0f9eb;" />
              </div>
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                size="large"
                :loading="loading"
                class="login-btn"
                @click="handleLogin"
              >
                <span v-if="!loading">登 录</span>
              </el-button>
            </el-form-item>
          </el-form>

          <div class="form-footer">
            <p class="default-account">默认账号：admin / admin123</p>
            <p class="register-link">
              没有账号？
              <el-link type="primary" class="reg-link" @click="router.push('/register')">业主注册</el-link>
            </p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ElMessage } from 'element-plus'

import { getCaptcha } from '@/api/user'

const router = useRouter()
const userStore = useUserStore()

const loginFormRef = ref(null)
const loading = ref(false)
const captchaImg = ref('')
const captchaKey = ref('')

const loginForm = ref({
  username: '',
  password: '',
  captcha: ''
})

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captcha: [{ required: true, message: '请输入计算结果', trigger: 'blur' }]
}

const refreshCaptcha = async () => {
  try {
    const res = await getCaptcha()
    captchaImg.value = res.data.captchaImg
    captchaKey.value = res.data.captchaKey
  } catch (e) { /* ignore */ }
}

onMounted(() => { refreshCaptcha() })

/* 车位指示灯网格：free / used / book 三种状态（固定分布，纯装饰） */
const parkingCells = computed(() => {
  const seq = ['free', 'used', 'used', 'free', 'book', 'free', 'used', 'free', 'free', 'used', 'free', 'used', 'book', 'used', 'free', 'free', 'used', 'free', 'free', 'used', 'used', 'free', 'book', 'free', 'used', 'free', 'used', 'free', 'free', 'used', 'book', 'free', 'used', 'free', 'used', 'used']
  return seq.map((s, i) => (i % 5 === 0 && s !== 'book' ? 'free' : s))
})

const handleLogin = async () => {
  const form = loginFormRef.value
  if (!form) return

  await form.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      await userStore.loginAction(loginForm.value.username, loginForm.value.password, loginForm.value.captcha, captchaKey.value)
      ElMessage.success('登录成功')
      router.push('/dashboard')
    } catch (error) {
      ElMessage.error(error.message || '登录失败')
      loginForm.value.captcha = ''
      refreshCaptcha()
    } finally {
      loading.value = false
    }
  })
}

/* ===== 光感引擎：星尘粒子 + 探照灯光照 ===== */
const starCanvas = ref(null)
const dot = ref(null)
const ring = ref(null)
const ringActive = ref(false)

let raf = 0
let stars = []
let W = 0, H = 0
let mx = 0, my = 0, tx = 0, ty = 0
let rx = 0, ry = 0
let sweep = 0
let isTouch = false
let touchActive = false
let touchTimer = null

onMounted(() => {
  const canvas = starCanvas.value
  const ctx = canvas ? canvas.getContext('2d') : null
  if (!canvas || !ctx) return

  isTouch = ('ontouchstart' in window) || navigator.maxTouchPoints > 0

  const resize = () => {
    W = canvas.width = window.innerWidth
    H = canvas.height = window.innerHeight
  }
  resize()
  window.addEventListener('resize', resize)

  const makeStar = (far) => ({
    x: Math.random() * W,
    y: Math.random() * H,
    r: far ? Math.random() * 1.1 + 0.4 : Math.random() * 1.6 + 0.9,
    base: far ? Math.random() * 0.45 + 0.25 : Math.random() * 0.5 + 0.35,
    tw: Math.random() * 0.02 + 0.008,
    ph: Math.random() * Math.PI * 2,
    far,
    glow: 0
  })
  stars = []
  for (let i = 0; i < 100; i++) stars.push(makeStar(true))
  for (let j = 0; j < 30; j++) stars.push(makeStar(false))

  mx = W / 2; my = H / 2; tx = mx; ty = my; rx = mx; ry = my

  const LIGHT_R = 280

  const onMove = (e) => { tx = e.clientX; ty = e.clientY }
  const onTouchStart = () => {
    touchActive = true
    clearTimeout(touchTimer)
    touchTimer = setTimeout(() => { touchActive = false }, 4000)
  }
  const onTouchMove = (e) => {
    if (e.touches && e.touches[0]) {
      touchActive = true
      tx = e.touches[0].clientX
      ty = e.touches[0].clientY
      clearTimeout(touchTimer)
      touchTimer = setTimeout(() => { touchActive = false }, 4000)
    }
  }
  const autoSweep = () => {   // 触屏：光束自动漫游
    sweep += 0.004
    tx = W * (0.5 + 0.42 * Math.sin(sweep))
    ty = H * (0.5 + 0.32 * Math.cos(sweep * 1.3))
  }

  const frame = () => {
    if (isTouch && !touchActive) autoSweep()
    mx += (tx - mx) * 0.08      // 磁吸延迟
    my += (ty - my) * 0.08

    ctx.clearRect(0, 0, W, H)

    /* 粒子：靠近灯光才点亮 */
    for (let k = 0; k < stars.length; k++) {
      const s = stars[k]
      s.ph += s.tw
      const d = Math.hypot(s.x - mx, s.y - my)
      const target = d < LIGHT_R ? 1 : (d < LIGHT_R * 2.2 ? 1 - (d - LIGHT_R) / (LIGHT_R * 1.2) : 0)
      s.glow += (target - s.glow) * 0.12
      const a = s.base * (0.65 + 0.35 * Math.sin(s.ph)) * (0.25 + 0.75 * s.glow)
      ctx.beginPath()
      ctx.arc(s.x, s.y, s.r, 0, Math.PI * 2)
      ctx.fillStyle = `rgba(235,243,255,${a.toFixed(3)})`
      ctx.fill()
      if (s.glow > 0.35 && !s.far) {
        ctx.beginPath()
        ctx.arc(s.x, s.y, s.r * 3.4, 0, Math.PI * 2)
        ctx.fillStyle = `rgba(240,201,135,${(0.08 * s.glow).toFixed(3)})`
        ctx.fill()
      }
    }

    /* 探照灯光：加亮合成（不是压暗） */
    const g = ctx.createRadialGradient(mx, my, 0, mx, my, 540)
    g.addColorStop(0, 'rgba(255,252,245,0.10)')
    g.addColorStop(0.3, 'rgba(240,201,135,0.07)')
    g.addColorStop(0.55, 'rgba(123,200,176,0.035)')
    g.addColorStop(1, 'rgba(0,0,0,0)')
    ctx.globalCompositeOperation = 'lighter'
    ctx.fillStyle = g
    ctx.fillRect(mx - 540, my - 540, 1080, 1080)
    const g2 = ctx.createRadialGradient(mx, my, 0, mx, my, 950)
    g2.addColorStop(0, 'rgba(240,201,135,0.05)')
    g2.addColorStop(0.5, 'rgba(123,200,176,0.02)')
    g2.addColorStop(1, 'rgba(0,0,0,0)')
    ctx.fillStyle = g2
    ctx.fillRect(mx - 950, my - 950, 1900, 1900)
    ctx.globalCompositeOperation = 'source-over'

    /* 自定义光标（桌面端） */
    if (!isTouch) {
      if (dot.value) { dot.value.style.left = mx + 'px'; dot.value.style.top = my + 'px' }
      rx += (mx - rx) * 0.16
      ry += (my - ry) * 0.16
      if (ring.value) { ring.value.style.left = rx + 'px'; ring.value.style.top = ry + 'px' }
    }
    raf = requestAnimationFrame(frame)
  }
  raf = requestAnimationFrame(frame)

  window.addEventListener('mousemove', onMove)
  window.addEventListener('touchstart', onTouchStart, { passive: true })
  window.addEventListener('touchmove', onTouchMove, { passive: true })
  if (isTouch) autoSweep()

  onUnmounted(() => {
    cancelAnimationFrame(raf)
    clearTimeout(touchTimer)
    window.removeEventListener('resize', resize)
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('touchstart', onTouchStart)
    window.removeEventListener('touchmove', onTouchMove)
  })
})
</script>

<style lang="scss" scoped>
.login-page {
  position: fixed;
  inset: 0;
  overflow: hidden;
  background: var(--sp-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--sp-font);
  color: var(--sp-text);
}

/* L0 氛围光：四角基础光晕，避免死黑 */
.ambient {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background:
    radial-gradient(1100px at 85% -10%, rgba(26, 22, 48, 0.95) 0%, rgba(11, 10, 22, 0) 60%),
    radial-gradient(900px at 8% 108%, rgba(123, 200, 176, 0.09) 0%, rgba(11, 10, 22, 0) 55%),
    radial-gradient(700px at 108% 92%, rgba(240, 201, 135, 0.07) 0%, rgba(11, 10, 22, 0) 55%);
}

.stars {
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
}

/* ===== 左右分屏容器 ===== */
.login-shell {
  position: relative;
  z-index: 2;
  display: flex;
  width: 100%;
  max-width: 1280px;
  height: 100%;
  margin: 0 auto;
  padding: 32px 40px;
  gap: 40px;
}

/* ================= 左侧：品牌背景板 ================= */
.brand-panel {
  position: relative;
  flex: 1 1 58%;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 48px 56px;
  border-radius: 24px;
  overflow: hidden;
  background:
    radial-gradient(900px at 90% -10%, rgba(26, 22, 48, 0.9) 0%, rgba(11, 10, 22, 0) 60%),
    linear-gradient(160deg, rgba(124, 141, 181, 0.12) 0%, rgba(11, 10, 22, 0.35) 45%, rgba(11, 10, 22, 0.6) 100%);
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.25);
  opacity: 0;
  transform: translateX(-24px);
  animation: slide-left 0.9s var(--sp-ease) 0.15s forwards;
}

.brand-glow {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  filter: blur(60px);

  &.glow-tl {
    top: -120px;
    left: -80px;
    width: 420px;
    height: 420px;
    background: rgba(123, 200, 176, 0.10);
    animation: breathe 6s ease-in-out infinite;
  }

  &.glow-br {
    bottom: -140px;
    right: -100px;
    width: 480px;
    height: 480px;
    background: rgba(240, 201, 135, 0.08);
    animation: breathe 6s ease-in-out 3s infinite;
  }
}

.brand-top {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 14px;

  .brand-logo {
    position: relative;
    width: 44px;
    height: 44px;
    border-radius: 14px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, var(--sp-brand), var(--sp-teal));
    color: #0B0A16;
    font-size: 20px;
    font-weight: 700;
    box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.06), 0 8px 32px rgba(123, 200, 176, 0.2);

    b {
      font-size: 20px;
      font-weight: 800;
      color: #0B0A16;
    }

    i {
      position: absolute;
      top: -5px;
      right: -5px;
      width: 16px;
      height: 16px;
      border-radius: 50%;
      background: var(--sp-gold);
      color: #0B0A16;
      font-style: normal;
      font-size: 10px;
      line-height: 16px;
      text-align: center;
      box-shadow: 0 0 10px rgba(240, 201, 135, 0.7);
    }
  }

  .brand-name {
    font-size: 15px;
    font-weight: 600;
    letter-spacing: 3px;
    color: var(--sp-text);

    em {
      font-style: normal;
      color: var(--sp-teal);
    }
  }
}

.brand-hero {
  position: relative;
  z-index: 1;
  margin-top: 56px;

  .brand-title {
    margin: 0 0 18px;
    font-size: clamp(34px, 4.2vw, 56px);
    font-weight: 800;
    line-height: 1.2;
    letter-spacing: 0.04em;
    color: transparent;
    -webkit-text-fill-color: transparent;
    background: linear-gradient(135deg, #EDF1F8 0%, #9AA9C7 40%, #7BC8B0 72%, #F0C987 100%);
    -webkit-background-clip: text;
    background-clip: text;
    filter: drop-shadow(0 0 40px rgba(123, 200, 176, 0.12));
  }

  .brand-slogan {
    margin: 0 0 40px;
    font-size: 15px;
    letter-spacing: 0.32em;
    color: var(--sp-text-2);
    font-weight: 300;

    b {
      color: var(--sp-gold);
      font-weight: 400;
    }
  }
}

.brand-features {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 18px;

  li {
    display: flex;
    align-items: center;
    gap: 14px;
  }

  .feat-icon {
    width: 34px;
    height: 34px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 10px;
    font-size: 14px;
    color: var(--sp-teal);
    background: rgba(123, 200, 176, 0.1);
    border: 1px solid rgba(123, 200, 176, 0.25);
  }

  .feat-text {
    b {
      display: block;
      font-size: 15px;
      font-weight: 600;
      color: var(--sp-text);
      margin-bottom: 2px;
    }

    i {
      font-style: normal;
      font-size: 12.5px;
      color: var(--sp-text-3);
    }
  }
}

/* ---- 车位指示灯网格 ---- */
.parking-zone {
  position: relative;
  z-index: 1;
  margin-top: 44px;

  .parking-zone-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 12px;
    color: var(--sp-text-2);
    letter-spacing: 1px;
    margin-bottom: 12px;
  }

  .zone-legend {
    font-size: 11px;
    color: var(--sp-text-3);

    i {
      display: inline-block;
      width: 8px;
      height: 8px;
      border-radius: 2px;
      margin: 0 4px 0 10px;
      vertical-align: 1px;
    }

    .lg-free { background: var(--sp-teal); }
    .lg-used { background: var(--sp-brand); }
    .lg-book { background: var(--sp-gold); }
  }
}

.parking-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(26px, 1fr));
  gap: 8px;
  padding: 14px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 12px;
}

.p-cell {
  aspect-ratio: 1;
  border-radius: 6px;
  transition: background 0.25s var(--sp-ease), box-shadow 0.25s var(--sp-ease);

  &.free {
    background: rgba(123, 200, 176, 0.16);
    border: 1px solid rgba(123, 200, 176, 0.3);
    box-shadow: 0 0 12px rgba(123, 200, 176, 0.1);
  }

  &.used {
    background: rgba(124, 141, 181, 0.2);
    border: 1px solid rgba(124, 141, 181, 0.35);
  }

  &.book {
    background: rgba(240, 201, 135, 0.18);
    border: 1px solid rgba(240, 201, 135, 0.4);
    box-shadow: 0 0 14px rgba(240, 201, 135, 0.18);
  }
}

/* ---- 指标 ---- */
.brand-stats {
  position: relative;
  z-index: 1;
  display: flex;
  gap: 32px;
  margin-top: 36px;

  .stat {
    b {
      display: block;
      font-size: 26px;
      font-weight: 800;
      color: var(--sp-text);
      margin-bottom: 2px;

      &.gold {
        color: var(--sp-gold);
        text-shadow: 0 0 24px rgba(240, 201, 135, 0.35);
      }
    }

    span {
      font-size: 12px;
      color: var(--sp-text-3);
      letter-spacing: 1px;
    }
  }
}

.brand-footer {
  position: relative;
  z-index: 1;
  margin: 28px 0 0;
  font-size: 11px;
  letter-spacing: 3px;
  color: rgba(147, 163, 191, 0.35);
}

/* ================= 右侧：登录卡片 ================= */
.form-panel {
  flex: 0 0 42%;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.form-card {
  width: 100%;
  max-width: 400px;
  padding: 40px 36px 28px;
  border-radius: var(--sp-radius-card);
  background: rgba(255, 255, 255, 0.04);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.25);
  opacity: 0;
  transform: translateY(24px);
  animation: rise 0.9s var(--sp-ease) 0.35s forwards;
}

.form-header {
  text-align: center;
  margin-bottom: 28px;

  .form-icon {
    position: relative;
    width: 48px;
    height: 48px;
    margin: 0 auto 14px;
    border-radius: 14px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, var(--sp-brand), var(--sp-teal));
    color: #0B0A16;
    font-size: 22px;
    font-weight: 700;
    box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.06), 0 8px 32px rgba(123, 200, 176, 0.18), 0 0 60px rgba(123, 200, 176, 0.12);

    b {
      font-size: 21px;
      font-weight: 800;
      color: #0B0A16;
    }

    i {
      position: absolute;
      top: -5px;
      right: -5px;
      width: 16px;
      height: 16px;
      border-radius: 50%;
      background: var(--sp-gold);
      color: #0B0A16;
      font-style: normal;
      font-size: 10px;
      line-height: 16px;
      text-align: center;
      box-shadow: 0 0 10px rgba(240, 201, 135, 0.7);
    }
  }

  h2 {
    margin: 0 0 6px;
    font-size: 20px;
    font-weight: 800;
    letter-spacing: 0.1em;
    color: var(--sp-text);
  }

  p {
    margin: 0;
    font-size: 11px;
    letter-spacing: 0.3em;
    color: var(--sp-text-3);
    font-weight: 300;
  }
}

.login-btn {
  width: 100%;
  height: 44px;
  border: none;
  border-radius: var(--sp-radius-pill);
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 4px;
  background: linear-gradient(135deg, var(--sp-brand), var(--sp-teal));
  color: #0B0A16;
  box-shadow: 0 4px 24px rgba(123, 200, 176, 0.22);
  transition: transform 0.35s var(--sp-ease), box-shadow 0.35s var(--sp-ease);

  &:hover,
  &:focus {
    transform: translateY(-2px);
    box-shadow: 0 10px 44px rgba(123, 200, 176, 0.35);
    background: linear-gradient(135deg, var(--sp-brand-bright), var(--sp-teal));
    color: #0B0A16;
  }
}

.form-footer {
  text-align: center;
  margin-top: 18px;

  .default-account {
    color: var(--sp-text-3);
    font-size: 12px;
    margin: 4px 0;
    letter-spacing: 1px;
  }

  .register-link {
    color: var(--sp-text-2);
    font-size: 13px;
    margin: 8px 0 0;
  }

  .reg-link {
    --el-link-text-color: var(--sp-teal);
    --el-link-hover-text-color: var(--sp-gold);
  }
}

/* ---- 自定义光标 ---- */
.cursor-dot,
.cursor-ring {
  position: fixed;
  top: 0;
  left: 0;
  pointer-events: none;
  z-index: 99;
  border-radius: 50%;
}

.cursor-dot {
  width: 8px;
  height: 8px;
  background: var(--sp-gold);
  box-shadow: 0 0 12px rgba(240, 201, 135, 0.8);
  transform: translate(-50%, -50%);
}

.cursor-ring {
  width: 34px;
  height: 34px;
  border: 1.5px solid rgba(240, 201, 135, 0.55);
  transform: translate(-50%, -50%);
  transition: width 0.25s var(--sp-ease), height 0.25s var(--sp-ease), border-color 0.25s ease;

  &.is-active {
    width: 54px;
    height: 54px;
    border-color: rgba(123, 200, 176, 0.7);
  }
}

@keyframes rise {
  to { opacity: 1; transform: translateY(0); }
}

@keyframes slide-left {
  to { opacity: 1; transform: translateX(0); }
}

@keyframes breathe {
  0%, 100% { opacity: 0.7; }
  50% { opacity: 1; }
}

/* 触屏：隐藏光标 + 光束自动漫游（JS 内处理） */
@media (hover: none) {
  .login-page {
    cursor: auto;
  }

  .cursor-dot,
  .cursor-ring {
    display: none;
  }
}

/* 窄屏：隐藏背景板，登录卡片居中 */
@media (max-width: 960px) {
  .login-shell {
    padding: 24px;
  }

  .brand-panel {
    display: none;
  }

  .form-panel {
    flex: 1 1 100%;
  }
}

@media (max-width: 480px) {
  .form-card {
    padding: 32px 24px 24px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .brand-panel,
  .form-card {
    animation: none;
    opacity: 1;
    transform: none;
  }

  .brand-glow {
    animation: none;
  }
}
</style>
