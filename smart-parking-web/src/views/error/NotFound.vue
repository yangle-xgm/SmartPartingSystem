<template>
  <div class="nf">
    <div class="nf__glow nf__glow--l" aria-hidden="true"></div>
    <div class="nf__glow nf__glow--r" aria-hidden="true"></div>
    <div class="nf__stars" aria-hidden="true"></div>

    <div class="nf__card">
      <div class="nf__code">404</div>
      <h1 class="nf__title">这片星域没有泊位</h1>
      <p class="nf__desc">
        你访问的页面 <code>{{ path }}</code> 不存在，或已被移除。<br />
        它可能停在了别的星系，也可能从未被点亮。
      </p>
      <div class="nf__actions">
        <button class="nf__btn nf__btn--primary" type="button" @click="goHome">回到首页</button>
        <button class="nf__btn nf__btn--ghost" type="button" @click="goBack">返回上一页</button>
      </div>
      <p class="nf__foot">智慧星河 SmartGalaxy · 小区智慧停车管理系统</p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const path = computed(() => String(route.fullPath || '/').slice(0, 60))

const goHome = () => {
  // 已登录回工作台，未登录回欢迎页（两者都不需要再次鉴权跳转）
  const token = localStorage.getItem('token')
  router.replace(token ? '/dashboard' : '/welcome')
}

const goBack = () => {
  if (window.history.length > 1) router.back()
  else goHome()
}
</script>

<style scoped>
/*
 * 404 页不挂在 Layout 内，也不依赖 --sp-* 令牌（管理端会切到 light 主题，
 * 令牌取值与品牌深色页不同）。这里写死品牌深色色值，保证任何主题下都对。
 */
.nf {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: 32px 20px;
  background:
    radial-gradient(900px at 80% -10%, rgba(35, 29, 64, 0.95) 0%, rgba(11, 10, 22, 0) 62%),
    radial-gradient(700px at 6% 108%, rgba(123, 200, 176, 0.12) 0%, rgba(11, 10, 22, 0) 58%),
    #0B0A16;
  font-family: var(--sp-font, 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif);
}

.nf__glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  pointer-events: none;
}

.nf__glow--l {
  top: -140px;
  left: -100px;
  width: 420px;
  height: 420px;
  background: rgba(123, 200, 176, 0.16);
  animation: nf-breathe 7s ease-in-out infinite;
}

.nf__glow--r {
  right: -120px;
  bottom: -160px;
  width: 460px;
  height: 460px;
  background: rgba(240, 201, 135, 0.13);
  animation: nf-breathe 7s ease-in-out 3s infinite;
}

/* 星点：radial-gradient 平铺，避免引入 canvas */
.nf__stars {
  position: absolute;
  inset: 0;
  pointer-events: none;
  opacity: 0.8;
  background-image:
    radial-gradient(1.4px 1.4px at 18% 22%, rgba(237, 243, 255, 0.85), transparent 100%),
    radial-gradient(1.2px 1.2px at 72% 14%, rgba(237, 243, 255, 0.6), transparent 100%),
    radial-gradient(1.6px 1.6px at 44% 68%, rgba(240, 201, 135, 0.7), transparent 100%),
    radial-gradient(1.2px 1.2px at 88% 62%, rgba(123, 200, 176, 0.65), transparent 100%),
    radial-gradient(1.1px 1.1px at 8% 76%, rgba(237, 243, 255, 0.5), transparent 100%),
    radial-gradient(1.3px 1.3px at 62% 88%, rgba(237, 243, 255, 0.45), transparent 100%);
}

.nf__card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 560px;
  padding: 44px 40px 32px;
  border-radius: 24px;
  text-align: center;
  background: rgba(255, 255, 255, 0.035);
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  animation: nf-rise 0.7s cubic-bezier(0.22, 0.61, 0.36, 1) both;
}

.nf__code {
  font-size: clamp(84px, 18vw, 132px);
  font-weight: 800;
  line-height: 1;
  letter-spacing: 0.04em;
  color: transparent;
  -webkit-text-fill-color: transparent;
  background: linear-gradient(135deg, #EDF1F8 0%, #9AA9C7 38%, #7BC8B0 70%, #F0C987 100%);
  -webkit-background-clip: text;
  background-clip: text;
  filter: drop-shadow(0 0 40px rgba(123, 200, 176, 0.18));
}

.nf__title {
  margin: 16px 0 10px;
  font-size: 21px;
  font-weight: 800;
  letter-spacing: 0.08em;
  color: #EDF1F8;
}

.nf__desc {
  margin: 0 auto;
  max-width: 420px;
  font-size: 13px;
  line-height: 1.9;
  color: #93A3BF;
}

.nf__desc code {
  padding: 1px 7px;
  border-radius: 6px;
  font-family: 'SFMono-Regular', ui-monospace, Consolas, monospace;
  font-size: 12px;
  color: #F0C987;
  background: rgba(240, 201, 135, 0.12);
  word-break: break-all;
}

.nf__actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 26px;
}

.nf__btn {
  height: 42px;
  padding: 0 26px;
  border: none;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.06em;
  cursor: pointer;
  transition: transform 0.3s cubic-bezier(0.22, 0.61, 0.36, 1), box-shadow 0.3s ease, background 0.3s ease;
}

.nf__btn--primary {
  color: #0B0A16;
  background: linear-gradient(135deg, #7BC8B0, #5FB39A);
  box-shadow: 0 8px 24px rgba(123, 200, 176, 0.28);
}

.nf__btn--primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 32px rgba(123, 200, 176, 0.38);
}

.nf__btn--ghost {
  color: #C7D2E4;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.12);
}

.nf__btn--ghost:hover {
  transform: translateY(-2px);
  background: rgba(255, 255, 255, 0.1);
}

.nf__foot {
  margin: 28px 0 0;
  font-size: 11px;
  letter-spacing: 0.18em;
  color: rgba(147, 163, 191, 0.45);
}

@keyframes nf-rise {
  from { opacity: 0; transform: translateY(22px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes nf-breathe {
  0%, 100% { opacity: 0.72; }
  50% { opacity: 1; }
}

@media (max-width: 520px) {
  .nf__card { padding: 34px 22px 26px; }
  .nf__actions { flex-direction: column; }
  .nf__btn { width: 100%; }
}

@media (prefers-reduced-motion: reduce) {
  .nf__card { animation: none; }
  .nf__glow { animation: none; }
}
</style>
