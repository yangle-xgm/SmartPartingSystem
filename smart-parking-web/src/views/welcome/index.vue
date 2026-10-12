<template>
  <div class="welcome-page dark">
    <!-- L0 氛围光背景（固定全屏） -->
    <div class="ambient" aria-hidden="true"></div>
    <!-- L1 星尘粒子 + 探照灯光照 -->
    <canvas ref="starCanvas" class="stars" aria-hidden="true"></canvas>
    <!-- 自定义光标 -->
    <div ref="dot" class="cursor-dot" aria-hidden="true"></div>
    <div ref="ring" class="cursor-ring" :class="{ 'is-active': ringActive }" aria-hidden="true"></div>

    <!-- ============ ① Hero 首屏 ============ -->
    <section class="hero">
      <nav class="nav">
        <div class="nav-brand">
          <span class="nav-logo"><b>P</b><i>✦</i></span>
          <span class="nav-name">智慧<em>星河</em>&nbsp;SMART&nbsp;GALAXY</span>
        </div>
        <button class="nav-login" @mouseenter="ringActive = true" @mouseleave="ringActive = false" @click="goLogin">登 录</button>
      </nav>

      <div class="hero-main">
        <div class="hero-badge">小区智慧停车管理系统</div>
        <h1 class="hero-title" :class="{ 'is-lit': heroLit }">智慧<em>星河</em></h1>
        <p class="hero-slogan">让每一次停泊，都有<b>星光</b>指引</p>

        <div class="hero-actions">
          <button class="btn btn-primary" @mouseenter="ringActive = true" @mouseleave="ringActive = false" @click="enterSystem">
            <span v-if="!entering">进入系统</span>
            <span v-else class="spinner">◌</span>
          </button>
          <button class="btn btn-ghost" @mouseenter="ringActive = true" @mouseleave="ringActive = false" @click="scrollToFeatures">了解更多</button>
        </div>
      </div>

      <div class="hero-mascot" @mouseenter="ringActive = true" @mouseleave="ringActive = false">
        <div class="mascot-star">
          <StarzoHero @lit="heroLit = true" />
        </div>
        <div class="mascot-label">星仔&nbsp;<b>Starzo</b></div>
      </div>

      <div class="hero-scroll-hint" aria-hidden="true">▾</div>
    </section>

    <!-- ============ ② 平台特性区 ============ -->
    <section ref="featuresRef" class="section features">
      <div class="section-head reveal">
        <h2>核心功能</h2>
        <p>找位、进出、计费、设备，覆盖停车全流程</p>
      </div>
      <div class="feature-grid">
        <div v-for="(f, i) in features" :key="i" class="feature-card reveal" :style="{ transitionDelay: i * 80 + 'ms' }" @mouseenter="ringActive = true" @mouseleave="ringActive = false">
          <div class="feature-icon" :class="'c' + (i + 1)">{{ f.icon }}</div>
          <h3>{{ f.title }}</h3>
          <p>{{ f.desc }}</p>
        </div>
      </div>
    </section>

    <!-- ============ ③ 数字指标区 ============ -->
    <section ref="statsRef" class="section stats">
      <div class="stat reveal" v-for="(s, i) in statList" :key="i">
        <b class="stat-num sp-num">
          <span :ref="el => setNumRef(el, i)">0</span><span v-if="s.suffix" class="stat-suffix">{{ s.suffix }}</span>
        </b>
        <span class="stat-label">{{ s.label }}</span>
      </div>
    </section>

    <!-- ============ ④ 智慧停车闭环 ============ -->
    <section ref="flowRef" class="section flow">
      <div class="section-head reveal">
        <h2>停车流程</h2>
        <p>入场识别 → 计费 → 出场，三步走完一次停车</p>
      </div>
      <div class="flow-steps">
        <div v-for="(step, i) in flowSteps" :key="i" class="flow-step reveal" :style="{ transitionDelay: i * 120 + 'ms' }">
          <div class="step-no">0{{ i + 1 }}</div>
          <div class="step-icon">{{ step.icon }}</div>
          <h3>{{ step.title }}</h3>
          <p>{{ step.desc }}</p>
        </div>
      </div>
    </section>

    <!-- ============ ⑤ 功能模块墙 ============ -->
    <section ref="moduleRef" class="section modules">
      <div class="section-head reveal">
        <h2>功能模块</h2>
        <p>系统共 11 个功能模块，按角色开放权限</p>
      </div>
      <div class="module-wall">
        <span v-for="(m, i) in modules" :key="i" class="module-tag reveal" :style="{ transitionDelay: (i % 6) * 60 + 'ms' }">{{ m }}</span>
      </div>
    </section>

    <!-- ============ ⑥ 底部 ============ -->
    <footer class="footer">
      <div class="footer-mascot">
        <img class="mascot-img" :src="starzoImg" alt="星仔 Starzo" />
      </div>
      <p class="footer-slogan">让每一次停泊，都有<b>星光</b>指引</p>
      <p class="footer-copy">© 2026 智慧星河 SmartGalaxy · 小区智慧停车管理系统</p>
    </footer>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import StarzoHero from '@/components/StarzoHero.vue'
import starzoImg from '@/assets/images/starzo.png'

const router = useRouter()
const userStore = useUserStore()
const entering = ref(false)
const heroLit = ref(false)

const ringActive = ref(false)

/* ---------- 数据 ---------- */
const features = [
  { icon: '◉', title: '车位实时可视', desc: '地图实时显示车位空闲/占用/预约状态，业主找车位不用再绕圈。' },
  { icon: '◈', title: '无感通行', desc: '车牌识别后自动抬杆，识别失败转人工确认，进出场不用等。' },
  { icon: '✦', title: '智能计费', desc: '出场自动结算，微信/支付宝直接支付，超时订单自动补偿。' },
  { icon: '◈', title: '设备联动', desc: '摄像头、道闸、地磁、显示屏统一接入，MQTT 实时联动，设备在线率随时可查。' }
]

const statList = [
  { value: 486, suffix: '', label: '总车位' },
  { value: 68.5, suffix: '%', label: '实时利用率' },
  { value: 2847, suffix: '', label: '今日收入（元）' },
  { value: 98.5, suffix: '%', label: '设备在线率' }
]

const flowSteps = [
  { icon: '⇥', title: '入场识别', desc: '摄像头识别车牌，注册车辆自动开闸，访客凭邀请码入场。' },
  { icon: '¥', title: '智能计费', desc: '离场自动结算，微信/支付宝支付，超时订单自动补偿确认。' },
  { icon: '⇤', title: '无感出场', desc: '支付完成后道闸联动开启，业主端实时收到出入场通知。' }
]

const modules = [
  '车位管理', '车辆管理', '订单管理', '缴费管理', '设备管理', '访客邀请',
  '通知中心', '操作审计', '数据统计', '车位预约', '业主端 H5'
]

/* ---------- 数字滚动 ---------- */
const numEls = []
const setNumRef = (el, i) => {
  if (el && !numEls[i]) numEls[i] = el
}

const animateNum = (el, target, decimals) => {
  const dur = 800
  const start = performance.now()
  const step = (now) => {
    const p = Math.min(1, (now - start) / dur)
    const eased = 1 - Math.pow(1 - p, 3)
    const v = target * eased
    el.textContent = decimals > 0 ? v.toFixed(decimals) : Math.round(v).toLocaleString()
    if (p < 1) requestAnimationFrame(step)
  }
  requestAnimationFrame(step)
}

/* ---------- 滚动入场（IntersectionObserver） ---------- */
const featuresRef = ref(null)
const statsRef = ref(null)
const flowRef = ref(null)
const moduleRef = ref(null)
let observer = null

const observeReveal = () => {
  observer = new IntersectionObserver((entries) => {
    entries.forEach((e) => {
      if (e.isIntersecting) {
        e.target.classList.add('in')
        observer.unobserve(e.target)
      }
    })
  }, { threshold: 0.15 })

  document.querySelectorAll('.reveal').forEach((el) => observer.observe(el))

  // 数字滚动：统计区进入视口时触发一次
  const statsObs = new IntersectionObserver((entries) => {
    entries.forEach((e) => {
      if (e.isIntersecting) {
        statList.forEach((s, i) => {
          if (numEls[i]) animateNum(numEls[i], s.value, s.value % 1 !== 0 ? 1 : 0)
        })
        statsObs.disconnect()
      }
    })
  }, { threshold: 0.4 })
  statsObs.observe(statsRef.value)
}

/* ---------- 交互 ---------- */
const enterSystem = () => {
  if (entering.value) return
  entering.value = true
  setTimeout(() => {
    if (userStore.token) {
      router.push('/dashboard')
    } else {
      router.push('/login')
    }
  }, 400)
}

const goLogin = () => router.push('/login')

const scrollToFeatures = () => {
  if (featuresRef.value) featuresRef.value.scrollIntoView({ behavior: 'smooth' })
}

/* ===== 探照灯引擎：星尘粒子 + 光照 + 光标 ===== */
const starCanvas = ref(null)
const dot = ref(null)
const ring = ref(null)

let raf = 0
let stars = []
let W = 0, H = 0
let mx = 0, my = 0, tx = 0, ty = 0
let rx = 0, ry = 0
let sweep = 0
let isTouch = false
let touchActive = false
let touchTimer = null
let meteorTimer = null

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
  const autoSweep = () => {
    sweep += 0.004
    tx = W * (0.5 + 0.42 * Math.sin(sweep))
    ty = H * (0.5 + 0.32 * Math.cos(sweep * 1.3))
  }

  const frame = () => {
    if (isTouch && !touchActive) autoSweep()
    mx += (tx - mx) * 0.08
    my += (ty - my) * 0.08

    ctx.clearRect(0, 0, W, H)

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

    if (!isTouch) {
      if (dot.value) { dot.value.style.left = mx + 'px'; dot.value.style.top = my + 'px' }
      rx += (mx - rx) * 0.16
      ry += (my - ry) * 0.16
      if (ring.value) { ring.value.style.left = rx + 'px'; ring.value.style.top = ry + 'px' }
    }
    raf = requestAnimationFrame(frame)
  }
  raf = requestAnimationFrame(frame)

  /* 流星 */
  const spawnMeteor = () => {
    const m = document.createElement('div')
    m.className = 'meteor'
    const sx = W * 0.1 + Math.random() * W * 0.8
    const sy = H * 0.05 + Math.random() * H * 0.25
    m.style.left = sx + 'px'
    m.style.top = sy + 'px'
    document.body.appendChild(m)
    const dist = Math.min(W, H) * 0.6 + 120
    const dx = Math.cos(-25 * Math.PI / 180) * dist
    const dy = Math.sin(-25 * Math.PI / 180) * dist
    try {
      m.animate([
        { transform: 'translate(0,0) scale(1)', opacity: 1 },
        { transform: `translate(${dx}px,${dy}px) scale(0.3)`, opacity: 0 }
      ], { duration: 900 + Math.random() * 400, easing: 'cubic-bezier(.2,.6,.4,1)' })
    } catch (e) { /* 忽略 */ }
    setTimeout(() => { if (m.parentNode) m.parentNode.removeChild(m) }, 1600)
    meteorTimer = setTimeout(spawnMeteor, 7000 + Math.random() * 6000)
  }
  meteorTimer = setTimeout(() => { try { spawnMeteor() } catch (e) { /* 忽略 */ } }, 4000)

  window.addEventListener('mousemove', onMove)
  window.addEventListener('touchstart', onTouchStart, { passive: true })
  window.addEventListener('touchmove', onTouchMove, { passive: true })
  if (isTouch) autoSweep()

  observeReveal()

  onUnmounted(() => {
    cancelAnimationFrame(raf)
    clearTimeout(meteorTimer)
    clearTimeout(touchTimer)
    if (observer) observer.disconnect()
    window.removeEventListener('resize', resize)
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('touchstart', onTouchStart)
    window.removeEventListener('touchmove', onTouchMove)
  })
})
</script>

<style lang="scss" scoped>
.welcome-page {
  position: relative;
  min-height: 100vh;
  background: var(--sp-bg);
  font-family: var(--sp-font);
  color: var(--sp-text);
  overflow-x: hidden;
}

.ambient {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background:
    radial-gradient(1100px at 85% -10%, rgba(26, 22, 48, 0.95) 0%, rgba(11, 10, 22, 0) 60%),
    radial-gradient(900px at 8% 108%, rgba(123, 200, 176, 0.09) 0%, rgba(11, 10, 22, 0) 55%),
    radial-gradient(700px at 108% 92%, rgba(240, 201, 135, 0.07) 0%, rgba(11, 10, 22, 0) 55%);
}

.stars {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
}

/* ============ Hero ============ */
.hero {
  position: relative;
  z-index: 1;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28px 48px;

  .nav-brand {
    display: flex;
    align-items: center;
    gap: 12px;
    opacity: 0;
    animation: rise 0.9s var(--sp-ease) 0.15s forwards;
  }

  .nav-logo {
    position: relative;
    width: 38px;
    height: 38px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, var(--sp-brand), var(--sp-teal));
    color: #0B0A16;
    font-size: 17px;
    font-weight: 700;
    box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.06), 0 8px 32px rgba(123, 200, 176, 0.2);

    b {
      font-size: 18px;
      font-weight: 800;
      color: #0B0A16;
      letter-spacing: 0;
    }

    i {
      position: absolute;
      top: -4px;
      right: -4px;
      width: 14px;
      height: 14px;
      border-radius: 50%;
      background: var(--sp-gold);
      color: #0B0A16;
      font-style: normal;
      font-size: 9px;
      line-height: 14px;
      text-align: center;
      box-shadow: 0 0 10px rgba(240, 201, 135, 0.7);
    }
  }

  .nav-name {
    font-size: 15px;
    font-weight: 600;
    letter-spacing: 3px;

    em {
      font-style: normal;
      color: var(--sp-teal);
    }
  }

  .nav-login {
    padding: 9px 26px;
    border-radius: var(--sp-radius-pill);
    border: 1px solid rgba(255, 255, 255, 0.12);
    background: rgba(255, 255, 255, 0.04);
    color: var(--sp-text-2);
    font-size: 13px;
    letter-spacing: 3px;
    cursor: pointer;
    font-family: inherit;
    transition: all 0.35s var(--sp-ease);
    opacity: 0;
    animation: rise 0.9s var(--sp-ease) 0.15s forwards;

    &:hover {
      background: rgba(123, 200, 176, 0.12);
      border-color: rgba(123, 200, 176, 0.4);
      color: var(--sp-teal);
      transform: translateY(-2px);
    }
  }
}

.hero-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 40px 24px;
}

.hero-badge {
  font-size: 12px;
  letter-spacing: 4px;
  color: var(--sp-teal);
  border: 1px solid rgba(123, 200, 176, 0.3);
  border-radius: var(--sp-radius-pill);
  padding: 8px 22px;
  background: rgba(123, 200, 176, 0.06);
  margin-bottom: 32px;
  opacity: 0;
  animation: rise 0.9s var(--sp-ease) 0.3s forwards;
}

.hero-title {
  margin: 0 0 24px;
  font-size: clamp(52px, 9vw, 104px);
  font-weight: 800;
  line-height: 1.2;
  letter-spacing: 0.14em;
  text-indent: 0.14em;
  color: transparent;
  -webkit-text-fill-color: transparent;
  background: linear-gradient(135deg, #EDF1F8 0%, #9AA9C7 40%, #7BC8B0 70%, #F0C987 100%);
  -webkit-background-clip: text;
  background-clip: text;
  filter: drop-shadow(0 0 50px rgba(123, 200, 176, 0.14));
  opacity: 0;
  transform: translateY(30px);
  animation: rise 0.9s var(--sp-ease) 0.5s forwards;

  em {
    font-style: normal;
    /* 初始弱化：等待星仔挥棒点亮（与星仔本体发光同步） */
    -webkit-text-fill-color: var(--sp-text-3);
    color: var(--sp-text-3);
    filter: none;
    transition: -webkit-text-fill-color 0.4s var(--sp-ease), color 0.4s var(--sp-ease), filter 0.4s var(--sp-ease);
  }

  &.is-lit em {
    -webkit-text-fill-color: var(--sp-gold);
    color: var(--sp-gold);
    filter: drop-shadow(0 0 30px rgba(240, 201, 135, 0.4));
  }
}

.hero-slogan {
  margin: 0 0 44px;
  font-size: clamp(14px, 2vw, 18px);
  letter-spacing: 0.42em;
  text-indent: 0.42em;
  color: var(--sp-text-2);
  font-weight: 300;
  opacity: 0;
  animation: rise 0.9s var(--sp-ease) 1.1s forwards;

  b {
    color: var(--sp-gold);
    font-weight: 400;
  }
}

.hero-actions {
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
  justify-content: center;
  opacity: 0;
  animation: rise 0.9s var(--sp-ease) 1.3s forwards;
}

.btn {
  position: relative;
  padding: 15px 44px;
  border-radius: var(--sp-radius-pill);
  font-size: 15px;
  font-weight: 500;
  letter-spacing: 3px;
  border: none;
  cursor: pointer;
  font-family: inherit;
  transition: transform 0.35s var(--sp-ease), box-shadow 0.35s var(--sp-ease), background 0.35s ease;

  &.btn-primary {
    background: linear-gradient(135deg, var(--sp-brand), var(--sp-teal));
    color: #0B0A16;
    box-shadow: 0 4px 24px rgba(123, 200, 176, 0.22);

    &:hover {
      transform: translateY(-4px);
      box-shadow: 0 10px 44px rgba(123, 200, 176, 0.35);
    }
  }

  &.btn-ghost {
    background: rgba(255, 255, 255, 0.04);
    backdrop-filter: blur(14px);
    color: var(--sp-text-2);
    border: 1px solid rgba(255, 255, 255, 0.09);

    &:hover {
      background: rgba(255, 255, 255, 0.08);
      color: var(--sp-text);
      transform: translateY(-4px);
    }
  }
}

.spinner {
  display: inline-block;
  animation: spin 0.8s linear infinite;
}

.hero-mascot {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  margin-bottom: 36px;
  opacity: 0;
  animation: rise 0.9s var(--sp-ease) 1.6s forwards;
  cursor: default;

  .mascot-star {
    position: relative;
    width: 150px;
    height: 150px;
    filter: drop-shadow(0 10px 34px rgba(240, 201, 135, 0.25));
    /* 掉落/弹跳/挥棒动效由 StarzoHero 组件内部状态机驱动，不再叠加 float */
  }

  .mascot-img {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: contain;
  }

  .mascot-label {
    font-size: 12px;
    color: var(--sp-text-3);
    letter-spacing: 3px;

    b {
      color: var(--sp-gold);
      font-weight: 500;
    }
  }
}

.hero-scroll-hint {
  position: absolute;
  bottom: 18px;
  left: 50%;
  transform: translateX(-50%);
  color: rgba(147, 163, 191, 0.4);
  font-size: 18px;
  animation: hint-bounce 2s ease-in-out infinite;
  opacity: 0;
  animation: rise 0.9s var(--sp-ease) 2s forwards, hint-bounce 2s ease-in-out 2.5s infinite;
}

/* ============ 通用区块 ============ */
.section {
  position: relative;
  z-index: 1;
  max-width: 1160px;
  margin: 0 auto;
  padding: 96px 32px;
}

.section-head {
  text-align: center;
  margin-bottom: 56px;

  h2 {
    margin: 0 0 10px;
    font-size: clamp(26px, 3.4vw, 36px);
    font-weight: 800;
    letter-spacing: 0.04em;

    b {
      color: var(--sp-gold);
    }
  }

  p {
    margin: 0;
    color: var(--sp-text-3);
    font-size: 14px;
    letter-spacing: 1px;
  }
}

/* 滚动入场动画 */
.reveal {
  opacity: 0;
  transform: translateY(32px);
  transition: opacity 0.7s var(--sp-ease), transform 0.7s var(--sp-ease);

  &.in {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ============ 特性区 ============ */
.feature-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 24px;
}

.feature-card {
  padding: 32px 26px;
  border-radius: var(--sp-radius-card);
  background: rgba(255, 255, 255, 0.04);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.25);
  transition: transform 0.35s var(--sp-ease), box-shadow 0.35s var(--sp-ease), border-color 0.35s ease;

  &:hover {
    transform: translateY(-4px);
    border-color: rgba(123, 200, 176, 0.3);
    box-shadow: 0 12px 40px rgba(0, 0, 0, 0.35), 0 0 40px rgba(123, 200, 176, 0.1);
  }

  .feature-icon {
    width: 46px;
    height: 46px;
    border-radius: 14px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 19px;
    margin-bottom: 20px;
    border: 1px solid;

    &.c1 { color: var(--sp-teal); border-color: rgba(123, 200, 176, 0.3); background: rgba(123, 200, 176, 0.08); }
    &.c2 { color: var(--sp-brand-bright); border-color: rgba(154, 169, 199, 0.3); background: rgba(154, 169, 199, 0.08); }
    &.c3 { color: var(--sp-gold); border-color: rgba(240, 201, 135, 0.3); background: rgba(240, 201, 135, 0.08); }
    &.c4 { color: var(--sp-rose); border-color: rgba(180, 139, 157, 0.3); background: rgba(180, 139, 157, 0.08); }
  }

  h3 {
    margin: 0 0 10px;
    font-size: 18px;
    font-weight: 600;
    color: var(--sp-text);
  }

  p {
    margin: 0;
    font-size: 13.5px;
    line-height: 1.7;
    color: var(--sp-text-2);
  }
}

/* ============ 数字区 ============ */
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 24px;
  text-align: center;
}

.stat {
  padding: 36px 20px;
  border-radius: var(--sp-radius-card);
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);

  .stat-num {
    display: block;
    font-size: clamp(30px, 3.4vw, 40px);
    font-weight: 800;
    color: var(--sp-gold);
    text-shadow: 0 0 30px rgba(240, 201, 135, 0.25);
    margin-bottom: 6px;
  }

  .stat-suffix {
    font-size: 20px;
    margin-left: 2px;
  }

  .stat-label {
    font-size: 13px;
    color: var(--sp-text-3);
    letter-spacing: 2px;
  }
}

/* ============ 流程区 ============ */
.flow-steps {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 32px;
  position: relative;
}

.flow-step {
  text-align: center;
  padding: 36px 24px;
  border-radius: var(--sp-radius-card);
  background: rgba(255, 255, 255, 0.04);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  position: relative;

  .step-no {
    position: absolute;
    top: 16px;
    left: 20px;
    font-size: 13px;
    font-weight: 700;
    color: rgba(147, 163, 191, 0.35);
    letter-spacing: 2px;
  }

  .step-icon {
    width: 54px;
    height: 54px;
    margin: 0 auto 18px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 20px;
    color: var(--sp-gold);
    border: 1px solid rgba(240, 201, 135, 0.3);
    background: rgba(240, 201, 135, 0.06);
    box-shadow: 0 0 24px rgba(240, 201, 135, 0.12);
  }

  h3 {
    margin: 0 0 10px;
    font-size: 17px;
    font-weight: 600;
  }

  p {
    margin: 0;
    font-size: 13px;
    color: var(--sp-text-2);
    line-height: 1.7;
  }
}

/* ============ 模块墙 ============ */
.module-wall {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 16px;
}

.module-tag {
  padding: 12px 24px;
  border-radius: var(--sp-radius-pill);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.09);
  font-size: 14px;
  letter-spacing: 1px;
  color: var(--sp-text-2);
  transition: all 0.3s var(--sp-ease);
  cursor: default;

  &:hover {
    color: var(--sp-teal);
    border-color: rgba(123, 200, 176, 0.4);
    background: rgba(123, 200, 176, 0.08);
    transform: translateY(-3px);
    box-shadow: 0 0 24px rgba(123, 200, 176, 0.12);
  }
}

/* ============ 底部 ============ */
.footer {
  position: relative;
  z-index: 1;
  text-align: center;
  padding: 64px 24px 48px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);

  .footer-mascot {
    position: relative;
    width: 76px;
    height: 76px;
    margin: 0 auto 14px;
    filter: drop-shadow(0 8px 24px rgba(240, 201, 135, 0.22));
  }

  .footer-slogan {
    margin: 0 0 10px;
    font-size: 15px;
    letter-spacing: 4px;
    color: var(--sp-text-2);

    b {
      color: var(--sp-gold);
    }
  }

  .footer-copy {
    margin: 0;
    font-size: 12px;
    color: rgba(147, 163, 191, 0.4);
    letter-spacing: 2px;
  }
}

/* ---- 光标 ---- */
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

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-14px); }
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@keyframes hint-bounce {
  0%, 100% { transform: translate(-50%, 0); }
  50% { transform: translate(-50%, 8px); }
}

/* 触屏 */
@media (hover: none) {
  .welcome-page { cursor: auto; }
  .cursor-dot, .cursor-ring { display: none; }
}

@media (max-width: 768px) {
  .nav { padding: 20px 24px; }
  .hero-title { font-size: 40px; letter-spacing: 0.05em; }
  .btn { padding: 13px 30px; font-size: 13px; letter-spacing: 2px; }
  .section { padding: 72px 20px; }
  .feature-grid, .flow-steps { grid-template-columns: 1fr; }
}

/* ≤960px：星仔缩小 0.7 倍（StarzoHero 内部同时关闭掉落动画） */
@media (max-width: 960px) {
  .hero-mascot .mascot-star {
    width: 105px;
    height: 105px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .nav, .nav-login, .hero-badge, .hero-title, .hero-slogan, .hero-actions, .hero-mascot,
  .reveal {
    animation: none !important;
    opacity: 1 !important;
    transform: none !important;
    transition: none !important;
  }
  .mascot-star, .hero-scroll-hint, .footer-mascot { animation: none !important; }
}
</style>

<style>
/* 全局：.meteor 由 JS 动态创建 */
.meteor {
  position: fixed;
  z-index: 4;
  width: 2px;
  height: 2px;
  border-radius: 50%;
  pointer-events: none;
  background: linear-gradient(90deg, rgba(237, 241, 248, 0.9), transparent);
  box-shadow: 0 0 8px 1px rgba(237, 241, 248, 0.5);
}
</style>
