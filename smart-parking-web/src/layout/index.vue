<template>
  <div class="layout light">
    <!-- ===== 侧边栏 ===== -->
    <aside class="aside" :class="{ 'is-collapse': isCollapse }">
      <router-link to="/dashboard" class="brand">
        <span class="brand__mark"><b>P</b><i>✦</i></span>
        <span v-if="!isCollapse" class="brand__text">
          <b>智慧<em>星河</em></b>
          <i>SMART GALAXY</i>
        </span>
      </router-link>

      <nav class="nav">
        <div v-for="group in menuGroups" :key="group.name" class="nav__group">
          <p v-if="!isCollapse" class="nav__label">{{ group.name }}</p>
          <router-link
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            class="nav__item"
            :class="{ 'is-active': isActive(item.path) }"
            :title="isCollapse ? item.title : ''"
          >
            <el-icon class="nav__icon"><component :is="item.icon" /></el-icon>
            <span v-if="!isCollapse" class="nav__text">{{ item.title }}</span>
            <span v-if="!isCollapse && item.path === '/notification' && unreadCount" class="nav__badge">
              {{ unreadCount > 99 ? '99+' : unreadCount }}
            </span>
          </router-link>
        </div>
      </nav>

      <div v-if="!isCollapse" class="aside__foot">
        <span class="aside__dot" />
        <div>
          <b>系统运行正常</b>
          <i>11 个微服务在线</i>
        </div>
      </div>
    </aside>

    <!-- ===== 右侧主区 ===== -->
    <div class="body">
      <header class="header">
        <div class="header__left">
          <button class="icon-btn" @click="isCollapse = !isCollapse" :title="isCollapse ? '展开菜单' : '收起菜单'">
            <el-icon><Fold v-if="!isCollapse" /><Expand v-else /></el-icon>
          </button>
          <div class="crumb">
            <router-link to="/dashboard" class="crumb__home">智慧星河</router-link>
            <span class="crumb__sep">/</span>
            <span class="crumb__now">{{ route.meta.title || '首页' }}</span>
          </div>
        </div>

        <div class="header__right">
          <span class="role-chip">{{ roleText }}</span>
          <button class="icon-btn" @click="router.push('/notification')" title="消息通知">
            <el-badge :value="unreadCount" :hidden="!unreadCount" :max="99">
              <el-icon><Bell /></el-icon>
            </el-badge>
          </button>
          <el-dropdown trigger="click" @command="handleCommand">
            <span class="user">
              <el-avatar :size="30" :src="userInfo?.avatar" class="user__avatar">
                {{ avatarChar }}
              </el-avatar>
              <span class="user__name">{{ userInfo?.realName || userInfo?.username || '未登录' }}</span>
              <el-icon class="user__arrow"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="welcome">品牌首页</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="main">
        <router-view />
      </main>
    </div>

    <AiChatWidget />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { useNotifyStore } from '@/store/notify'
import { ElMessageBox } from 'element-plus'
import { getRole } from '@/api/user'
import AiChatWidget from '@/components/AiChatWidget.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
// D8：未读数放全局 store，通知中心标记已读后可立即同步角标
const notifyStore = useNotifyStore()

const isCollapse = ref(false)
const unreadCount = computed(() => notifyStore.unread)
const role = ref(getRole())
const userInfo = computed(() => userStore.userInfo)

const ROLE_TEXT = { 0: '业主', 1: '物业管理员', 2: '超级管理员' }
const roleText = computed(() => ROLE_TEXT[role.value] ?? '访客')
const avatarChar = computed(() => (userInfo.value?.realName || userInfo.value?.username || 'U').charAt(0))

/**
 * 菜单：按角色过滤 + 按业务域分组
 * roles：0 业主 / 1 物业 / 2 超管
 */
const MENU = [
  {
    name: '概览',
    items: [{ path: '/dashboard', title: '数据看板', icon: 'DataBoard', roles: [0, 1, 2] }]
  },
  {
    name: '停车运营',
    items: [
      { path: '/reserve', title: '车位预约', icon: 'Position', roles: [0] },
      { path: '/parking', title: '车位管理', icon: 'Location', roles: [1, 2] },
      { path: '/vehicle', title: '车辆管理', icon: 'Van', roles: [0, 1, 2] },
      { path: '/my-orders', title: '我的订单', icon: 'Tickets', roles: [0] },
      { path: '/order', title: '订单管理', icon: 'Document', roles: [1, 2] },
      { path: '/payment', title: '缴费管理', icon: 'Money', roles: [0, 1, 2] },
      { path: '/invite', title: '访客邀请', icon: 'Promotion', roles: [0] }
    ]
  },
  {
    name: '设备与智能',
    items: [
      { path: '/device', title: '设备管理', icon: 'Monitor', roles: [1, 2] },
      { path: '/statistics', title: '数据统计', icon: 'TrendCharts', roles: [1, 2] }
    ]
  },
  {
    name: '服务与治理',
    items: [
      { path: '/notification', title: '通知中心', icon: 'Bell', roles: [0, 1, 2] },
      { path: '/feedback', title: '意见反馈', icon: 'ChatDotRound', roles: [0, 1, 2] },
      { path: '/feedback-manage', title: '反馈处理', icon: 'ChatLineRound', roles: [1, 2] },
      { path: '/community', title: '小区管理', icon: 'OfficeBuilding', roles: [2] },
      { path: '/user', title: '用户管理', icon: 'User', roles: [2] },
      { path: '/audit', title: '操作审计', icon: 'Memo', roles: [2] },
      { path: '/profile', title: '个人中心', icon: 'Setting', roles: [0, 1, 2] }
    ]
  }
]

const menuGroups = computed(() =>
  MENU.map(g => ({ ...g, items: g.items.filter(i => role.value != null && i.roles.includes(role.value)) }))
    .filter(g => g.items.length)
)

const isActive = (path) => route.path === path

const loadUnread = () => notifyStore.refresh()

watch(() => route.path, () => loadUnread(), { immediate: true })

let timer = null
onMounted(async () => {
  timer = setInterval(loadUnread, 60000)
  try {
    await userStore.getUserInfoAction()
  } catch (e) {
    /* 用户信息失败不阻塞 */
  }
})
onUnmounted(() => clearInterval(timer))

const handleCommand = async (cmd) => {
  if (cmd === 'profile') {
    router.push('/profile')
    return
  }
  if (cmd === 'welcome') {
    router.push('/welcome')
    return
  }
  if (cmd === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '退出登录', {
        confirmButtonText: '退出',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch (e) {
      return
    }
    await userStore.logoutAction()
    notifyStore.reset()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout {
  display: flex;
  height: 100vh;
  background: var(--sp-bg);
  background-image: var(--sp-page-grad);
  background-attachment: fixed;
  overflow: hidden;
}

/* ================= 侧边栏 ================= */
.aside {
  width: 232px;
  flex: none;
  display: flex;
  flex-direction: column;
  background: var(--sp-surface);
  border-right: 1px solid var(--sp-divider);
  transition: width var(--sp-dur-comp) var(--sp-ease);
  overflow: hidden;
}

.aside.is-collapse {
  width: 72px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 64px;
  padding: 0 18px;
  text-decoration: none;
  border-bottom: 1px solid var(--sp-divider);
  flex: none;
}

.brand__mark {
  position: relative;
  width: 34px;
  height: 34px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: var(--sp-grad-brand);
  color: #fff;
  font-weight: 800;
  font-size: 17px;
  box-shadow: 0 6px 16px var(--sp-brand-soft);
}

.brand__mark i {
  position: absolute;
  top: -5px;
  right: -5px;
  font-style: normal;
  font-size: 11px;
  color: var(--sp-gold);
}

.brand__text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
  min-width: 0;
}

.brand__text b {
  font-size: 15px;
  font-weight: 800;
  color: var(--sp-text);
  letter-spacing: 0.04em;
  white-space: nowrap;
}

.brand__text em {
  font-style: normal;
  color: var(--sp-gold);
}

.brand__text i {
  font-style: normal;
  font-size: 9px;
  letter-spacing: 0.26em;
  color: var(--sp-text-3);
  margin-top: 2px;
  white-space: nowrap;
}

.nav {
  flex: 1;
  overflow-y: auto;
  padding: 12px 10px 20px;
}

.nav__group + .nav__group {
  margin-top: 6px;
}

.nav__label {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.18em;
  color: var(--sp-text-3);
  padding: 12px 12px 6px;
  text-transform: uppercase;
}

.nav__item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 40px;
  padding: 0 12px;
  margin-bottom: 2px;
  border-radius: 10px;
  color: var(--sp-text-2);
  text-decoration: none;
  font-size: 13.5px;
  font-weight: 500;
  transition: background var(--sp-dur-micro) var(--sp-ease), color var(--sp-dur-micro) var(--sp-ease);
}

.nav__item:hover {
  background: var(--sp-hover-bg);
  color: var(--sp-text);
}

.nav__item.is-active {
  background: var(--sp-active-grad);
  color: var(--sp-brand);
  font-weight: 700;
}

.nav__item.is-active::before {
  content: '';
  position: absolute;
  left: -10px;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 18px;
  border-radius: 0 3px 3px 0;
  background: var(--sp-grad-brand);
}

.nav__icon {
  font-size: 17px;
  flex: none;
}

.nav__text {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.nav__badge {
  margin-left: auto;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--sp-danger);
  color: #fff;
  font-size: 10.5px;
  font-weight: 700;
  display: grid;
  place-items: center;
}

.aside__foot {
  flex: none;
  margin: 10px 12px 14px;
  padding: 12px;
  border-radius: 12px;
  background: var(--sp-surface-2);
  display: flex;
  align-items: center;
  gap: 9px;
}

.aside__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--sp-teal);
  box-shadow: 0 0 0 4px var(--sp-teal-soft);
  flex: none;
}

.aside__foot b {
  display: block;
  font-size: 12px;
  color: var(--sp-text);
}

.aside__foot i {
  display: block;
  font-style: normal;
  font-size: 10.5px;
  color: var(--sp-text-3);
  margin-top: 2px;
}

/* ================= 主体 ================= */
.body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.header {
  flex: none;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 22px;
  background: var(--sp-header-bg);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border-bottom: 1px solid var(--sp-divider);
  position: sticky;
  top: 0;
  z-index: 10;
}

.header__left,
.header__right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.icon-btn {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  border: 1px solid var(--sp-card-border);
  background: var(--sp-surface);
  color: var(--sp-text-2);
  display: grid;
  place-items: center;
  cursor: pointer;
  transition: all var(--sp-dur-micro) var(--sp-ease);
}

.icon-btn:hover {
  color: var(--sp-brand);
  border-color: var(--sp-brand);
  background: var(--sp-brand-soft);
}

.crumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
  color: var(--sp-text-3);
}

.crumb__home {
  color: var(--sp-text-3);
  text-decoration: none;
}

.crumb__home:hover {
  color: var(--sp-brand);
}

.crumb__sep {
  opacity: 0.5;
}

.crumb__now {
  color: var(--sp-text);
  font-weight: 600;
}

.role-chip {
  padding: 5px 11px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--sp-gold);
  background: var(--sp-gold-soft);
  border: 1px solid var(--sp-card-border);
}

.user {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px 4px 4px;
  border-radius: 999px;
  transition: background var(--sp-dur-micro) var(--sp-ease);
}

.user:hover {
  background: var(--sp-hover-bg);
}

.user__avatar {
  background: var(--sp-grad-brand);
  color: #fff;
  font-weight: 700;
  font-size: 13px;
  flex: none;
}

.user__name {
  font-size: 13px;
  color: var(--sp-text);
  font-weight: 600;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user__arrow {
  font-size: 12px;
  color: var(--sp-text-3);
}

.main {
  flex: 1;
  padding: 22px;
  overflow-y: auto;
}

@media (max-width: 1100px) {
  .aside {
    width: 72px;
  }

  .brand__text,
  .nav__label,
  .nav__text,
  .nav__badge,
  .aside__foot {
    display: none;
  }

  .role-chip {
    display: none;
  }
}

@media (max-width: 760px) {
  .main {
    padding: 14px;
  }

  .user__name {
    display: none;
  }
}
</style>
