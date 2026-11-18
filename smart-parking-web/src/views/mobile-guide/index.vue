<template>
  <div class="mg">
    <div class="mg__glow mg__glow--l" aria-hidden="true"></div>
    <div class="mg__glow mg__glow--r" aria-hidden="true"></div>

    <div class="mg__card">
      <div class="mg__badge">移动设备</div>
      <h1 class="mg__title">业主请使用手机业主端</h1>
      <p class="mg__desc">
        管理端为电脑端设计，在小屏上表格与图表无法正常操作。<br />
        停车缴费、车辆与月卡、车位共享、访客邀请等自助功能，请打开业主端。
      </p>

      <div v-if="h5Base" class="mg__addr">
        <span class="mg__addr-label">业主端地址</span>
        <code class="mg__addr-url">{{ h5Base }}</code>
        <button class="mg__copy" type="button" @click="copy">{{ copied ? '已复制' : '复制' }}</button>
      </div>
      <div v-else class="mg__addr mg__addr--empty">
        <span class="mg__addr-label">业主端地址</span>
        <span class="mg__addr-hint">未配置，请联系物业获取</span>
      </div>

      <div class="mg__actions">
        <a v-if="h5Base" class="mg__btn mg__btn--primary" :href="h5Base">前往业主端</a>
        <button class="mg__btn mg__btn--ghost" type="button" @click="continueAdmin">
          仍要使用管理端
        </button>
      </div>

      <p class="mg__foot">智慧星河 SmartGalaxy · 小区智慧停车管理系统</p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const copied = ref(false)

/**
 * 业主端地址：部署时用 VITE_H5_BASE_URL 指定（见 .env.example）。
 * 开发环境未配置时回落到本地 vite 端口，避免联调还要额外配环境变量。
 */
const h5Base = (import.meta.env.VITE_H5_BASE_URL || (import.meta.env.DEV ? 'http://localhost:3001' : '')).replace(/\/+$/, '')

async function copy() {
  try {
    await navigator.clipboard.writeText(h5Base)
    copied.value = true
    setTimeout(() => { copied.value = false }, 2000)
  } catch {
    // 非 https 或权限被拒时退化为选中文本，用户可手动复制
    copied.value = false
  }
}

/**
 * 放行一次：写 sessionStorage 标记，仅当前标签页会话有效。
 * 用 sessionStorage 而非 localStorage，避免手机上「永久绕过」后再也回不到引导。
 */
function continueAdmin() {
  sessionStorage.setItem('sp-mobile-bypass', '1')
  const target = typeof route.query.to === 'string' && route.query.to.startsWith('/')
    ? route.query.to
    : '/dashboard'
  router.replace(target)
}
</script>

<style scoped>
/*
 * 品牌深色页，不套管理端 --sp-* 令牌（管理端会切 light，取值不同），
 * 与 error/NotFound.vue 保持同一套视觉。
 */
.mg {
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

.mg__glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  pointer-events: none;
}

.mg__glow--l {
  top: -140px;
  left: -100px;
  width: 420px;
  height: 420px;
  background: rgba(123, 200, 176, 0.16);
}

.mg__glow--r {
  right: -120px;
  bottom: -160px;
  width: 460px;
  height: 460px;
  background: rgba(240, 201, 135, 0.13);
}

.mg__card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 520px;
  padding: 40px 30px 28px;
  border-radius: 24px;
  text-align: center;
  background: rgba(255, 255, 255, 0.035);
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
}

.mg__badge {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 11px;
  letter-spacing: 0.16em;
  color: #7BC8B0;
  background: rgba(123, 200, 176, 0.12);
  border: 1px solid rgba(123, 200, 176, 0.24);
}

.mg__title {
  margin: 16px 0 10px;
  font-size: 21px;
  font-weight: 800;
  letter-spacing: 0.06em;
  color: #EDF1F8;
}

.mg__desc {
  margin: 0 auto;
  max-width: 400px;
  font-size: 13px;
  line-height: 1.9;
  color: #93A3BF;
}

.mg__addr {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 22px 0 0;
  padding: 12px 14px;
  border-radius: 14px;
  text-align: left;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.mg__addr-label {
  flex: none;
  font-size: 11px;
  letter-spacing: 0.1em;
  color: #7E8CA8;
}

.mg__addr-url {
  flex: 1;
  min-width: 0;
  font-family: 'SFMono-Regular', ui-monospace, Consolas, monospace;
  font-size: 12px;
  color: #F0C987;
  word-break: break-all;
}

.mg__addr-hint {
  flex: 1;
  font-size: 12px;
  color: #93A3BF;
}

.mg__copy {
  flex: none;
  height: 26px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  background: rgba(255, 255, 255, 0.06);
  color: #C7D2E4;
  font-size: 12px;
  cursor: pointer;
}

.mg__actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 24px;
}

.mg__btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 42px;
  padding: 0 26px;
  border: none;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-decoration: none;
  cursor: pointer;
  transition: transform 0.3s cubic-bezier(0.22, 0.61, 0.36, 1), box-shadow 0.3s ease;
}

.mg__btn--primary {
  color: #0B0A16;
  background: linear-gradient(135deg, #7BC8B0, #5FB39A);
  box-shadow: 0 8px 24px rgba(123, 200, 176, 0.28);
}

.mg__btn--primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 32px rgba(123, 200, 176, 0.38);
}

.mg__btn--ghost {
  color: #C7D2E4;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.12);
}

.mg__btn--ghost:hover {
  background: rgba(255, 255, 255, 0.1);
}

.mg__foot {
  margin: 26px 0 0;
  font-size: 11px;
  letter-spacing: 0.18em;
  color: rgba(147, 163, 191, 0.45);
}

@media (max-width: 520px) {
  .mg__card { padding: 32px 20px 24px; }
  .mg__actions { flex-direction: column; }
  .mg__btn { width: 100%; }
}
</style>
