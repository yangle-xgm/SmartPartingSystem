<template>
  <div class="login-page">
    <canvas ref="starCanvas" class="stars" aria-hidden="true"></canvas>
    <div ref="dot" class="cursor-dot" aria-hidden="true"></div>
    <div ref="ring" class="cursor-ring" :class="{ 'is-active': ringActive }" aria-hidden="true"></div>

    <div class="hero">
      <div class="logo">
        <div class="logo-icon">✦</div>
        <div class="logo-text">智慧<em>星河</em></div>
      </div>

      <h1 class="title" aria-label="智慧星河">
        <span
          v-for="(ch, i) in titleChars"
          :key="i"
          :ref="setCharRef"
          class="tc"
          :data-depth="depths[i]"
        >{{ ch }}</span>
      </h1>
      <p class="subtitle">让每一次停泊，都有<b>星光</b>指引</p>

      <div class="actions">
        <button
          class="btn btn-primary"
          @mouseenter="ringActive = true"
          @mouseleave="ringActive = false"
          @click="handleEnter"
        >✦ 进入系统</button>
        <button
          class="btn btn-ghost"
          @mouseenter="ringActive = true"
          @mouseleave="ringActive = false"
          @click="handleMore"
        >了解更多</button>
      </div>

      <div class="mascot-area">
        <div class="mascot">
          <div class="mascot-star">⭐</div>
          <div class="mascot-label">✧ 星仔 <b>Starzo</b> ✧</div>
        </div>
      </div>
    </div>

    <div class="footer-info">✦ 智慧星河 · 让每一次停泊都有星光指引 ✦</div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'

/* ===== 页面数据 ===== */
const starCanvas = ref(null)
const dot = ref(null)
const ring = ref(null)
const ringActive = ref(false)
const charRefs = ref([])
const titleChars = ['智', '慧', '星', '河']
const depths = [0.02, 0.04, 0.06, 0.08]   // 逐字视差深度，制造纵深
const LIGHT_R = 280                       // 文字感光距离阈值(px)

const setCharRef = (el) => {
  if (el && !charRefs.value.includes(el)) charRefs.value.push(el)
}

/* 跳转：按你的路由改，例如 useRouter().push('/dashboard') */
const handleEnter = () => {}
const handleMore = () => {}

/* ===== 探照灯引擎 ===== */
let raf = 0
let meteorTimer = 0
let stars = []
let W = 0, H = 0
let mx = 0, my = 0, tx = 0, ty = 0
let rx = 0, ry = 0
let touchActive = false
let touchTimer = null
let sweep = 0
let isTouch = false

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

  /* 两层粒子：远景星 + 近景光尘 */
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
  for (let i = 0; i < 130; i++) stars.push(makeStar(true))
  for (let j = 0; j < 38; j++) stars.push(makeStar(false))

  mx = W / 2; my = H / 2; tx = mx; ty = my; rx = mx; ry = my

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

  const charCenter = (el) => {
    const r = el.getBoundingClientRect()
    return { x: r.left + r.width / 2, y: r.top + r.height / 2 }
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

    /* L1 探照灯光：加亮合成（不是压暗） */
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

    /* L2 文字感光 + 视差 */
    const els = charRefs.value
    for (let c = 0; c < els.length; c++) {
      const el = els[c]
      if (!el) continue
      const p = charCenter(el)
      const dd = Math.hypot(p.x - mx, p.y - my)
      const ii = Math.max(0, 1 - dd / LIGHT_R)
      el.style.textShadow = `0 0 ${(18 + 46 * ii).toFixed(0)}px rgba(240,201,135,${(0.12 + 0.4 * ii).toFixed(2)})`
      el.style.filter = `brightness(${(1 + 0.3 * ii).toFixed(2)})`
      const dep = parseFloat(el.getAttribute('data-depth')) || 0.04
      el.style.transform = `translate(${((mx - W / 2) * dep).toFixed(1)}px, ${((my - H / 2) * dep * 0.6).toFixed(1)}px)`
    }

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
    meteorTimer = setTimeout(spawnMeteor, 6000 + Math.random() * 6000)
  }

  window.addEventListener('mousemove', onMove)
  window.addEventListener('touchstart', onTouchStart, { passive: true })
  window.addEventListener('touchmove', onTouchMove, { passive: true })

  if (isTouch) autoSweep()
  meteorTimer = setTimeout(() => { try { spawnMeteor() } catch (e) { /* 忽略 */ } }, 4000)

  /* 组件卸载时清理所有监听与定时器 */
  onUnmounted(() => {
    cancelAnimationFrame(raf)
    clearTimeout(meteorTimer)
    clearTimeout(touchTimer)
    window.removeEventListener('resize', resize)
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('touchstart', onTouchStart)
    window.removeEventListener('touchmove', onTouchMove)
  })
})
</script>

<style scoped>
.login-page {
  position: fixed;
  inset: 0;
  overflow: hidden;
  background: var(--sp-bg);
  cursor: none;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--sp-font);
  color: var(--sp-text);
}

/* L0 氛围光：四角基础光晕，避免死黑 */
.login-page::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background:
    radial-gradient(1100px at 85% -10%, rgba(26, 22, 48, 0.95) 0%, rgba(11, 10, 22, 0) 60%),
    radial-gradient(900px at 8% 108%, rgba(123, 200, 176, 0.09) 0%, rgba(11, 10, 22, 0) 55%),
    radial-gradient(700px at 108% 92%, rgba(240, 201, 135, 0.07) 0%, rgba(11, 10, 22, 0) 55%);
}

.stars { position: absolute; inset: 0; z-index: 1; pointer-events: none; }

.hero {
  position: relative;
  z-index: 3;
  text-align: center;
  padding: 40px 24px;
  max-width: 960px;
  width: 100%;
}

/* ---- Logo ---- */
.logo {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 36px;
  opacity: 0;
  transform: translateY(24px);
  animation: rise 0.9s var(--sp-ease) 0.2s forwards;
}
.logo-icon {
  width: 52px;
  height: 52px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--sp-brand), var(--sp-teal));
  color: #0B0A16;
  font-size: 24px;
  font-weight: 700;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.06), 0 8px 32px rgba(123, 200, 176, 0.18), 0 0 60px rgba(123, 200, 176, 0.12);
}
.logo-text { font-size: 20px; font-weight: 500; letter-spacing: 4px; color: var(--sp-text); }
.logo-text em { font-style: normal; color: var(--sp-teal); }

/* ---- 标题：渐变字 + 感光 + 逐字视差 ---- */
.title {
  font-size: clamp(52px, 11vw, 116px);
  font-weight: 800;
  line-height: 1.15;
  letter-spacing: 0.12em;
  color: transparent;
  -webkit-text-fill-color: transparent;
  background: linear-gradient(135deg, #EDF1F8 0%, #9AA9C7 38%, #7BC8B0 66%, #F0C987 100%);
  -webkit-background-clip: text;
  background-clip: text;
  margin-bottom: 18px;
  filter: drop-shadow(0 0 60px rgba(123, 200, 176, 0.08));
  opacity: 0;
  transform: translateY(24px);
  animation: rise 0.9s var(--sp-ease) 0.45s forwards;
}
.title .tc { display: inline-block; will-change: transform, filter, text-shadow; }

.subtitle {
  font-size: clamp(15px, 2vw, 21px);
  color: var(--sp-text-2);
  letter-spacing: 0.42em;
  text-indent: 0.42em;
  font-weight: 300;
  margin-bottom: 44px;
  opacity: 0;
  transform: translateY(24px);
  animation: rise 0.9s var(--sp-ease) 0.7s forwards;
}
.subtitle b { color: var(--sp-gold); font-weight: 400; }

/* ---- 按钮 ---- */
.actions {
  display: flex;
  gap: 18px;
  justify-content: center;
  flex-wrap: wrap;
  opacity: 0;
  transform: translateY(24px);
  animation: rise 0.9s var(--sp-ease) 0.95s forwards;
}
.btn {
  position: relative;
  padding: 15px 46px;
  border-radius: var(--sp-radius-pill);
  font-size: 15px;
  font-weight: 500;
  letter-spacing: 3px;
  border: none;
  cursor: pointer;
  font-family: inherit;
  overflow: hidden;
  transition: transform 0.35s var(--sp-ease), box-shadow 0.35s var(--sp-ease), background 0.35s ease;
}
.btn-primary {
  background: linear-gradient(135deg, var(--sp-brand), var(--sp-teal));
  color: #0B0A16;
  box-shadow: 0 4px 24px rgba(123, 200, 176, 0.22);
}
.btn-primary:hover {
  transform: translateY(-4px);
  box-shadow: 0 10px 44px rgba(123, 200, 176, 0.35);
}
.btn-ghost {
  background: rgba(255, 255, 255, 0.04);
  backdrop-filter: blur(14px);
  color: var(--sp-text-2);
  border: 1px solid rgba(255, 255, 255, 0.09);
}
.btn-ghost:hover {
  background: rgba(255, 255, 255, 0.08);
  color: var(--sp-text);
  transform: translateY(-4px);
}

/* ---- 吉祥物 ---- */
.mascot-area {
  margin-top: 52px;
  opacity: 0;
  transform: translateY(24px);
  animation: rise 0.9s var(--sp-ease) 1.2s forwards;
}
.mascot { display: inline-flex; flex-direction: column; align-items: center; gap: 12px; }
.mascot-star {
  width: 110px;
  height: 110px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 44px;
  background: radial-gradient(circle at 32% 30%, #F7DFA6, #C9A86A 70%);
  box-shadow: 0 8px 40px rgba(240, 201, 135, 0.16), inset 0 0 24px rgba(255, 255, 255, 0.18);
  border: 2px solid rgba(255, 255, 255, 0.1);
  animation: float 3.2s ease-in-out infinite;
  transition: box-shadow 0.3s ease;
}
.mascot-label { font-size: 13px; color: var(--sp-text-3); letter-spacing: 3px; }
.mascot-label b { color: var(--sp-gold); font-weight: 500; }

/* ---- 自定义光标 ---- */
.cursor-dot, .cursor-ring {
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
}
.cursor-ring.is-active {
  width: 54px;
  height: 54px;
  border-color: rgba(123, 200, 176, 0.7);
}

.footer-info {
  position: fixed;
  bottom: 22px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 5;
  color: rgba(147, 163, 191, 0.28);
  font-size: 12px;
  letter-spacing: 4px;
  pointer-events: none;
}

@keyframes rise { to { opacity: 1; transform: translateY(0); } }
@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-14px); }
}

/* 触屏：隐藏光标 + 光束自动漫游（JS 内处理） */
@media (hover: none) {
  .login-page { cursor: auto; }
  .cursor-dot, .cursor-ring { display: none; }
}

@media (max-width: 768px) {
  .title { font-size: 44px; letter-spacing: 0.08em; }
  .subtitle { font-size: 14px; letter-spacing: 0.25em; text-indent: 0.25em; }
  .btn { padding: 13px 30px; font-size: 13px; letter-spacing: 2px; }
  .mascot-star { width: 84px; height: 84px; font-size: 34px; }
  .logo-icon { width: 40px; height: 40px; font-size: 18px; }
  .logo-text { font-size: 17px; }
}

@media (prefers-reduced-motion: reduce) {
  .logo, .title, .subtitle, .actions, .mascot-area {
    animation: none;
    opacity: 1;
    transform: none;
  }
  .mascot-star { animation: none; }
}
</style>

<style>
/* 全局样式（非 scoped）：.meteor 由 JS 动态创建，不在模板内，必须全局定义 */
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
