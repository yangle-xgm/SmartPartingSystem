import { createRouter, createWebHistory } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { useUserStore } from '@/store/user'
import { getRole } from '@/api/user'

/** 深色品牌页：欢迎 / 登录 / 注册（移动端引导页由 meta.theme 声明，不在此列） */
const BRAND_PATHS = ['/welcome', '/login', '/register']

/**
 * 主题同步到 <html>。
 * 管理端的 .light 若只挂在 Layout 的 div 上，所有通过 teleport 渲染到 body 的弹层
 * （el-drawer 在 append-to-body=false 时仍留在原位，而 ElMessageBox / 下拉 popper 会挂 body）
 * 就会拿不到浅色变量，且继承 body 按 :root 深色算出的浅色文字，导致白底浅字的“空白弹窗”。
 *
 * 必须定义在下面任何调用之前：const 声明存在暂时性死区，
 * 在定义前引用会抛 ReferenceError，且 vite build 是静态分析，查不出来。
 */
const applyTheme = (to) => {
  const isBrand = to.meta.theme === 'dark' || BRAND_PATHS.includes(to.path)
  document.documentElement.classList.toggle('light', !isBrand)
}

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
    // 移动端落地页：业主端是独立工程 smart-parking-h5（端口 3001），
    // 管理端不再内嵌第二套 H5。手机上打开管理端域名会被守卫送到这里，
    // 由页面给出业主端地址与「仍要使用管理端」的一次性放行。
    path: '/mobile-guide',
    name: 'MobileGuide',
    component: () => import('@/views/mobile-guide/index.vue'),
    meta: {
      title: '业主端',
      requiresAuth: false,
      theme: 'dark',
      skipMobileRedirect: true
    }
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
        path: 'billing',
        name: 'Billing',
        component: () => import('@/views/billing/index.vue'),
        meta: { title: '计费规则', icon: 'SetUp', roles: [1, 2] }
      },
      {
        path: 'payment',
        name: 'Payment',
        component: () => import('@/views/payment/index.vue'),
        meta: { title: '缴费管理', icon: 'Money' }
      },
      {
        path: 'invoice',
        name: 'Invoice',
        component: () => import('@/views/invoice/index.vue'),
        meta: { title: '电子发票', icon: 'Tickets', roles: [1, 2] }
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
        meta: { title: '数据统计', icon: 'TrendCharts', roles: [1, 2] }
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
  },
  {
    // 404 兜底：必须放最后。否则未命中的地址 Vue Router 不会渲染任何组件，
    // 页面只剩 <!---->（E2E 报告 P1-9 整页纯白）。
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/NotFound.vue'),
    meta: {
      title: '页面不存在',
      requiresAuth: false,
      theme: 'dark',            // 404 是深色品牌页，不套管理端浅色令牌
      skipMobileRedirect: true  // 移动端也展示 404，而不是跳 H5 首页掩盖错误地址
    }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 首屏在守卫执行前先按地址预置主题，避免管理端打开瞬间闪一下深色
applyTheme({ path: window.location.pathname, meta: {} })

router.beforeEach((to, from, next) => {
  NProgress.start()
  applyTheme(to)
  document.title = to.meta.title ? `${to.meta.title} - 智慧停车` : '智慧停车'

  // 移动端 UA 落到引导页：业主端是独立工程 smart-parking-h5（3001），管理端不再内嵌第二套 H5。
  // 登录/注册/欢迎/404/引导页自身除外；用户点过「仍要使用管理端」后写 sessionStorage 标记，
  // 本次会话内放行（用 session 不用 local，避免手机上永久绕过后再也回不到引导页）。
  const isMobile = /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent)
  if (
    isMobile &&
    !to.meta.skipMobileRedirect &&
    !['/login', '/register', '/welcome'].includes(to.path) &&
    sessionStorage.getItem('sp-mobile-bypass') !== '1'
  ) {
    next({ path: '/mobile-guide', query: { to: to.fullPath } })
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
