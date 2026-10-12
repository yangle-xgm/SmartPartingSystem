import { createRouter, createWebHistory } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { useUserStore } from '@/store/user'
import { getRole } from '@/api/user'

const routes = [
  {
    path: '/welcome',
    name: 'Welcome',
    component: () => import('@/views/welcome/index.vue'),
    meta: { title: '智慧星河', requiresAuth: false }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', requiresAuth: false }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/register/index.vue'),
    meta: { title: '业主注册', requiresAuth: false }
  },
  {
    path: '/h5',
    component: () => import('@/h5/layout.vue'),
    meta: { title: '移动业主端' },
    children: [
      { path: '', redirect: '/h5/home' },
      { path: 'home', name: 'H5Home', component: () => import('@/h5/home.vue'), meta: { title: '首页' } },
      { path: 'reserve', name: 'H5Reserve', component: () => import('@/h5/reserve.vue'), meta: { title: '车位预约' } },
      { path: 'orders', name: 'H5Orders', component: () => import('@/h5/orders.vue'), meta: { title: '我的订单' } },
      { path: 'confirm-plate', name: 'H5ConfirmPlate', component: () => import('@/h5/confirm-plate.vue'), meta: { title: '车牌确认' } },
      { path: 'profile', name: 'H5Profile', component: () => import('@/h5/profile.vue'), meta: { title: '我的' } }
    ]
  },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/welcome',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页', icon: 'DataBoard' }
      },
      {
        path: 'reserve',
        name: 'Reserve',
        component: () => import('@/views/reserve/index.vue'),
        meta: { title: '车位预约', icon: 'Position', roles: [0] }
      },
      {
        path: 'parking',
        name: 'Parking',
        component: () => import('@/views/parking/index.vue'),
        meta: { title: '车位管理', icon: 'Location', roles: [1, 2] }
      },
      {
        path: 'vehicle',
        name: 'Vehicle',
        component: () => import('@/views/vehicle/index.vue'),
        meta: { title: '车辆管理', icon: 'Van' }
      },
      {
        path: 'my-orders',
        name: 'MyOrders',
        component: () => import('@/views/my-orders/index.vue'),
        meta: { title: '我的订单', icon: 'Tickets', roles: [0] }
      },
      {
        path: 'invite',
        name: 'Invite',
        component: () => import('@/views/invite/index.vue'),
        meta: { title: '访客邀请', icon: 'Promotion', roles: [0] }
      },
      {
        path: 'order',
        name: 'Order',
        component: () => import('@/views/order/index.vue'),
        meta: { title: '订单管理', icon: 'Document', roles: [1, 2] }
      },
      {
        path: 'payment',
        name: 'Payment',
        component: () => import('@/views/payment/index.vue'),
        meta: { title: '缴费管理', icon: 'Money' }
      },
      {
        path: 'device',
        name: 'Device',
        component: () => import('@/views/device/index.vue'),
        meta: { title: '设备管理', icon: 'Monitor', roles: [1, 2] }
      },
      {
        path: 'statistics',
        name: 'Statistics',
        component: () => import('@/views/statistics/index.vue'),
        meta: { title: '数据统计', icon: 'TrendCharts', roles: [2] }
      },
      {
        path: 'notification',
        name: 'Notification',
        component: () => import('@/views/notify/index.vue'),
        meta: { title: '通知中心', icon: 'Bell' }
      },
      {
        path: 'feedback',
        name: 'Feedback',
        component: () => import('@/views/feedback/index.vue'),
        meta: { title: '意见反馈', icon: 'ChatDotRound' }
      },
      {
        path: 'feedback-manage',
        name: 'FeedbackManage',
        component: () => import('@/views/feedback-manage/index.vue'),
        meta: { title: '反馈管理', icon: 'ChatLineRound', roles: [1, 2] }
      },
      {
        path: 'audit',
        name: 'Audit',
        component: () => import('@/views/audit/index.vue'),
        meta: { title: '操作审计', icon: 'Memo', roles: [2] }
      },
      {
        path: 'user',
        name: 'User',
        component: () => import('@/views/user/index.vue'),
        meta: { title: '用户管理', icon: 'User', roles: [2] }
      },
      {
        path: 'community',
        name: 'Community',
        component: () => import('@/views/community/index.vue'),
        meta: { title: '小区管理', icon: 'OfficeBuilding', roles: [2] }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: '个人中心' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  NProgress.start()
  document.title = to.meta.title ? `${to.meta.title} - 智慧停车` : '智慧停车'

  // 移动端 UA 自动跳 H5 端（登录/注册/欢迎页除外）
  const isMobile = /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent)
  if (isMobile && !to.path.startsWith('/h5') && !['/login', '/register', '/welcome'].includes(to.path)) {
    next('/h5/home')
    return
  }

  const userStore = useUserStore()
  const token = userStore.token

  if (to.meta.requiresAuth !== false && !token) {
    next('/login')
    return
  }

  // 角色路由守卫：业主（0）不能进入管理端页面
  if (token && to.meta.roles) {
    const role = getRole()
    if (role == null || !to.meta.roles.includes(role)) {
      next('/dashboard')
      return
    }
  }

  next()
})

router.afterEach(() => {
  NProgress.done()
})

export default router
