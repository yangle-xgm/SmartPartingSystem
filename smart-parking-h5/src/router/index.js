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
    path: '/orders/:orderNo',
    name: 'OrderDetail',
    component: () => import('@/views/OrderDetail.vue'),
    meta: { title: '订单详情' }
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
  },
  {
    path: '/monthly',
    name: 'Monthly',
    component: () => import('@/views/Monthly.vue'),
    meta: { title: '月卡充值' }
  },
  {
    path: '/spaces',
    name: 'Spaces',
    component: () => import('@/views/Spaces.vue'),
    meta: { title: '我的车位' }
  },
  {
    path: '/reserve',
    name: 'Reserve',
    component: () => import('@/views/Reserve.vue'),
    meta: { title: '车位预约' }
  },
  {
    path: '/payments',
    name: 'Payments',
    component: () => import('@/views/Payments.vue'),
    meta: { title: '支付记录' }
  },
  {
    path: '/feedback',
    name: 'Feedback',
    component: () => import('@/views/Feedback.vue'),
    meta: { title: '意见反馈' }
  },
  {
    path: '/profile-edit',
    name: 'ProfileEdit',
    component: () => import('@/views/ProfileEdit.vue'),
    meta: { title: '编辑资料' }
  },
  // 未知地址回首页，避免整页空白
  { path: '/:pathMatch(.*)*', redirect: '/home' }
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
