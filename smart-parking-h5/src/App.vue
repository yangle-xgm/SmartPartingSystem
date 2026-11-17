<template>
  <router-view v-slot="{ Component }">
    <transition name="h-route" mode="out-in">
      <component :is="Component" />
    </transition>
  </router-view>
</template>

<script setup>
// 全局样式统一在 src/styles/tokens.css + mobile.css 中维护
// 路由切换用轻过渡（淡入 + 6px 上移），避免 Tab 之间硬切造成"闪"的感觉
</script>
