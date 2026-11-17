<template>
  <van-tabbar route fixed safe-area-inset-bottom>
    <van-tabbar-item replace to="/home" icon="wap-home-o" active-icon="wap-home">首页</van-tabbar-item>
    <van-tabbar-item replace to="/vehicles" icon="logistics">车辆</van-tabbar-item>
    <van-tabbar-item replace to="/orders" icon="orders-o" :badge="orderBadge || ''">订单</van-tabbar-item>
    <van-tabbar-item replace to="/invite" icon="friends-o" active-icon="friends">访客</van-tabbar-item>
    <van-tabbar-item replace to="/mine" icon="user-o" active-icon="user">我的</van-tabbar-item>
  </van-tabbar>
</template>

<script setup>
/**
 * 业主端底部导航（5 个 Tab 共用）
 * orderBadge：待缴费笔数，0 或空则不显示角标
 */
defineProps({
  orderBadge: { type: [Number, String], default: '' }
})
</script>
