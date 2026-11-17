<template>
  <div class="sp-stat" :data-tone="tone">
    <div class="sp-stat__top">
      <span class="sp-stat__label">{{ label }}</span>
      <span v-if="icon" class="sp-stat__icon">
        <el-icon><component :is="icon" /></el-icon>
      </span>
    </div>
    <div class="sp-stat__value sp-num">
      {{ displayValue }}<span v-if="unit" class="sp-stat__unit">{{ unit }}</span>
    </div>
    <div v-if="hint || $slots.foot" class="sp-stat__foot">
      <slot name="foot">
        <span class="sp-dot" :data-tone="dotTone" />
        {{ hint }}
      </slot>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  label: { type: String, required: true },
  value: { type: [Number, String], default: 0 },
  unit: { type: String, default: '' },
  icon: { type: String, default: '' },
  tone: { type: String, default: 'brand' }, // brand | teal | gold | danger | warning
  hint: { type: String, default: '' }
})

const TONE_DOT = { brand: 'used', teal: 'free', gold: 'book', danger: 'fix', warning: 'book' }

const dotTone = computed(() => TONE_DOT[props.tone] || 'used')

const displayValue = computed(() => {
  if (props.value === null || props.value === undefined || props.value === '') return '—'
  if (typeof props.value === 'number') return props.value.toLocaleString('zh-CN')
  return props.value
})
</script>
