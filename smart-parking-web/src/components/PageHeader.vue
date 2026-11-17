<template>
  <header class="sp-head">
    <div class="sp-head__main">
      <span v-if="eyebrow" class="sp-eyebrow">{{ eyebrow }}</span>
      <h1 class="sp-title">{{ title }}</h1>
      <p v-if="desc" class="sp-desc">{{ desc }}</p>
    </div>
    <div v-if="$slots.actions" class="sp-head__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  desc: { type: String, default: '' },
  eyebrow: { type: String, default: 'SMART GALAXY' }
})
</script>
