import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/home' },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/home',
    name: 'Home',
    component: () => import('@/views/Home.vue'),
    meta: { title: '首页', tab: true }
  },
  {
    path: '/vehicles',
    name: 'Vehicles',
    component: () => import('@/views/Vehicles.vue'),
    meta: { title: '我的车辆', tab: true }
  },
  {
    path: '/orders',
    name: 'Orders',
    component: () => import('@/views/Orders.vue'),
    meta: { title: '我的订单', tab: true }
  },
  {
    path: '/invite',
    name: 'Invite',
    component: () => import('@/views/Invite.vue'),
    meta: { title: '访客邀请', tab: true }
  },
  {
    path: '/mine',
    name: 'Mine',
    component: () => import('@/views/Mine.vue'),
    meta: { title: '我的', tab: true }
  },
  {
    path: '/notify',
    name: 'Notify',
    component: () => import('@/views/Notify.vue'),
    meta: { title: '通知中心' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 登录守卫
router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (to.name !== 'Login' && !token) {
    return { name: 'Login' }
  }
  if (to.name === 'Login' && token) {
    return { name: 'Home' }
  }
})

export default router
