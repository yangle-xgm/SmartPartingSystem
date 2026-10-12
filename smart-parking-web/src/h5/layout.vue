<template>
  <div class="h5-shell">
    <router-view />
    <van-tabbar route safe-area-inset-bottom>
      <van-tabbar-item replace to="/h5/home" icon="wap-home-o">首页</van-tabbar-item>
      <van-tabbar-item replace to="/h5/reserve" icon="newspaper-o">预约</van-tabbar-item>
      <van-tabbar-item replace to="/h5/orders" icon="orders-o">订单</van-tabbar-item>
      <van-tabbar-item replace to="/h5/profile" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<style scoped>
.h5-shell { min-height: 100vh; background: #f7f8fa; padding-bottom: 50px; }
</style>
