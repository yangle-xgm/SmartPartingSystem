<template>
  <div class="starzo-hero" :class="{ 'is-lit': phase === 'lit' }">
    <!-- 落地涟漪（触地瞬间） -->
    <div v-if="rippleOn" class="landing-ripple" aria-hidden="true"></div>

    <!-- 星仔主体 -->
    <img
      class="starzo-img"
      :class="imgClass"
      :src="starzoImg"
      alt="星仔 Starzo"
      title="点击星仔重播点亮动画"
      @mouseenter="isHovered = true"
      @mouseleave="isHovered = false"
      @click="startSequence"
    />

    <!-- 悬停 / 点亮后的星光光晕 -->
    <div class="starzo-glow" :class="{ on: isHovered || phase === 'lit' }" aria-hidden="true"></div>

    <!-- 挥棒光束 -->
    <div class="beam" :class="{ on: phase === 'swinging' }" aria-hidden="true"></div>

    <!-- 点亮爆发光浪（lit 瞬间扩散一圈） -->
    <div v-if="phase === 'lit'" class="lit-flash" aria-hidden="true"></div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import starzoImg from '@/assets/images/starzo.png'

const emit = defineEmits(['lit'])

/* ---------- 状态机 ---------- */
const phase = ref('idle')          // idle | dropping | bouncing | standing | swinging | lit
const isHovered = ref(false)
const rippleOn = ref(false)

const timers = []
const setTimer = (fn, ms) => timers.push(setTimeout(fn, ms))

const imgClass = computed(() => ({
  'is-dropping': phase.value === 'dropping',
  'is-bouncing': phase.value === 'bouncing',
  'is-standing': phase.value === 'standing',
  'is-swinging': phase.value === 'swinging'
}))

/* 动画序列（挂载 / 点击重播共用） */
const startSequence = () => {
  timers.forEach((t) => clearTimeout(t))
  timers.length = 0
  phase.value = 'idle'
  rippleOn.value = false

  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const isNarrow = () => window.innerWidth <= 960

  /* 无障碍：跳过所有动画，直接点亮 */
  if (reduced) {
    phase.value = 'lit'
    emit('lit')
    return
  }

  /* 移动端：关闭掉落，直接站立（性能） */
  if (isNarrow()) {
    phase.value = 'standing'
    return
  }

  /* 桌面时序（全部用 tokens 动效档位编排） */
  setTimer(() => { phase.value = 'dropping' }, 100)                          // 100ms 掉落
  setTimer(() => {                                                           // 900ms 触地弹跳 + 涟漪
    phase.value = 'bouncing'
    rippleOn.value = true
    setTimer(() => { rippleOn.value = false }, 600)
  }, 900)
  setTimer(() => { phase.value = 'standing' }, 1500)                         // 1500ms 站起
  setTimer(() => { phase.value = 'swinging' }, 1800)                         // 1800ms 挥棒
  setTimer(() => {                                                           // 2100ms 星仔点亮爆发 + 通知标题点亮
    phase.value = 'lit'
    emit('lit')
  }, 2100)
}

onMounted(startSequence)

onUnmounted(() => {
  timers.forEach((t) => clearTimeout(t))
})
</script>

<style lang="scss" scoped>
.starzo-hero {
  position: relative;
  width: 100%;
  height: 100%;
  pointer-events: none;

  .starzo-img {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    object-fit: contain;
    pointer-events: auto;
    transform-origin: 50% 100%;
    will-change: transform, filter;

    &.is-dropping {
      animation: starzo-drop var(--sp-dur-hero) cubic-bezier(0.45, 0, 1, 1) forwards;
    }

    &.is-bouncing {
      animation: starzo-bounce var(--sp-dur-hero) var(--sp-ease) forwards;
    }

    &.is-standing {
      animation: starzo-stand var(--sp-dur-page) var(--sp-ease) forwards;
    }

    &.is-swinging {
      animation: starzo-swing var(--sp-dur-page) var(--sp-ease) forwards;
    }
  }

  /* 点亮后：星仔本体被点亮 —— 亮度提升 + 金色辉光 + 悬浮漂浮 + 点亮爆发脉冲 */
  &.is-lit .starzo-img {
    animation:
      starzo-lit-burst 0.9s var(--sp-ease) 0.05s both,
      starzo-float 3.2s ease-in-out 0.6s infinite;
    filter: brightness(1.32) saturate(1.18)
            drop-shadow(0 0 18px rgba(240, 201, 135, 0.55))
            drop-shadow(0 0 52px rgba(240, 201, 135, 0.32));
  }

  .starzo-glow {
    position: absolute;
    inset: -40% -30%;
    pointer-events: none;
    opacity: 0;
    background: radial-gradient(circle, rgba(240, 201, 135, 0.62) 0%, rgba(240, 201, 135, 0.16) 42%, transparent 70%);
    transform: scale(0.75);
    transition: opacity 0.4s var(--sp-ease), transform 0.4s var(--sp-ease);
    border-radius: 50%;

    &.on {
      opacity: 1;
      transform: scale(1.25);
      animation: glow-breathe 2.4s ease-in-out infinite alternate;
    }
  }

  /* 点亮瞬间：向外扩散一圈金色光浪 */
  .lit-flash {
    position: absolute;
    inset: -12%;
    border-radius: 50%;
    border: 2px solid rgba(240, 201, 135, 0.8);
    box-shadow: 0 0 40px rgba(240, 201, 135, 0.45), inset 0 0 30px rgba(240, 201, 135, 0.25);
    animation: lit-flash-out 0.9s var(--sp-ease) forwards;
    pointer-events: none;
  }

  .landing-ripple {
    position: absolute;
    left: 50%;
    bottom: -4%;
    width: 150%;
    height: 26%;
    transform: translateX(-50%) scale(0.4);
    border-radius: 50%;
    background: radial-gradient(ellipse, rgba(123, 200, 176, 0.35) 0%, transparent 70%);
    animation: ripple-out var(--sp-dur-hero) var(--sp-ease) forwards;
    pointer-events: none;
  }

  .beam {
    position: absolute;
    left: 58%;
    top: 18%;
    height: 3px;
    width: 0;
    opacity: 0;
    transform-origin: left center;
    background: linear-gradient(90deg, rgba(240, 201, 135, 0.85), transparent);
    border-radius: var(--sp-radius-pill);
    box-shadow: 0 0 12px rgba(240, 201, 135, 0.6);
    pointer-events: none;

    &.on {
      animation: beam-flash var(--sp-dur-page) var(--sp-ease) forwards;
    }
  }
}

/* ---- 关键帧 ---- */
@keyframes starzo-drop {
  from { transform: translateY(-120vh) rotate(-6deg); }
  to   { transform: translateY(0) rotate(0); }
}

@keyframes starzo-bounce {
  0%   { transform: translateY(0) scaleY(1); }
  30%  { transform: translateY(0) scaleY(0.82); }
  60%  { transform: translateY(0) scaleY(1.15); }
  80%  { transform: translateY(0) scaleY(0.92); }
  100% { transform: translateY(0) scaleY(1); }
}

@keyframes starzo-stand {
  from { transform: translateY(12px); }
  to   { transform: translateY(0); }
}

@keyframes starzo-swing {
  0%   { transform: rotate(-18deg); }
  60%  { transform: rotate(12deg); }
  100% { transform: rotate(0); }
}

@keyframes ripple-out {
  to { transform: translateX(-50%) scale(1.8); opacity: 0; }
}

@keyframes beam-flash {
  0%   { width: 0; opacity: 0.9; }
  60%  { width: 100%; opacity: 0.9; }
  100% { width: 100%; opacity: 0; }
}

@keyframes starzo-float {
  0%, 100% { transform: translateY(0); }
  50%      { transform: translateY(-14px); }
}

@keyframes glow-breathe {
  from { opacity: 0.7; transform: scale(1.12); }
  to   { opacity: 1; transform: scale(1.28); }
}

/* 星仔本体点亮爆发：亮度 + 金色辉光脉冲 */
@keyframes starzo-lit-burst {
  0%   { filter: brightness(1) saturate(1) drop-shadow(0 0 0 rgba(240, 201, 135, 0)); }
  30%  { filter: brightness(1.7) saturate(1.35) drop-shadow(0 0 36px rgba(240, 201, 135, 0.9)) drop-shadow(0 0 100px rgba(240, 201, 135, 0.55)); }
  65%  { filter: brightness(1.18) saturate(1.12) drop-shadow(0 0 12px rgba(240, 201, 135, 0.5)) drop-shadow(0 0 36px rgba(240, 201, 135, 0.28)); }
  100% { filter: brightness(1.32) saturate(1.18) drop-shadow(0 0 18px rgba(240, 201, 135, 0.55)) drop-shadow(0 0 52px rgba(240, 201, 135, 0.32)); }
}

@keyframes lit-flash-out {
  0%   { transform: scale(0.55); opacity: 0.95; }
  100% { transform: scale(1.6); opacity: 0; }
}

@media (prefers-reduced-motion: reduce) {
  .starzo-img, .starzo-glow, .landing-ripple, .beam, .lit-flash {
    animation: none !important;
    transition: none !important;
  }
  .starzo-glow { opacity: 1; transform: scale(1); }
}
</style>
