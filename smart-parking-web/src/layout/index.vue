<template>
  <el-container class="layout-container light">
    <el-aside :width="isCollapse ? '64px' : '220px'" class="aside">
      <div class="logo">
        <img src="@/assets/logo.svg" alt="logo" class="logo-img" />
        <span v-if="!isCollapse" class="logo-text">智慧星河</span>
      </div>
      <el-menu
        :default-active="route.path"
        :collapse="isCollapse"
        :collapse-transition="false"
        router
        class="sidebar-menu"
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.title }}</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="isCollapse = !isCollapse">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="route.meta.title">{{ route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-badge :value="unreadCount" class="notification-badge">
            <el-icon :size="20" @click="router.push('/notification')"><Bell /></el-icon>
          </el-badge>
          <el-dropdown trigger="click">
            <span class="user-info">
              <el-avatar :size="32" :src="userInfo?.avatar">
                {{ userInfo?.realName?.charAt(0) || 'U' }}
              </el-avatar>
              <span class="username">{{ userInfo?.realName || '管理员' }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push('/profile')">个人中心</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ElMessageBox } from 'element-plus'
import { getUnreadCount } from '@/api/notify'
import { getUserId, getRole } from '@/api/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isCollapse = ref(false)
const unreadCount = ref(0)
const userInfo = computed(() => userStore.userInfo)

const allMenuItems = [
  { path: '/dashboard', title: '首页', icon: 'DataBoard', roles: [0, 1, 2] },
  { path: '/reserve', title: '车位预约', icon: 'Position', roles: [0] },
  { path: '/parking', title: '车位管理', icon: 'Location', roles: [1, 2] },
  { path: '/vehicle', title: '车辆管理', icon: 'Van', roles: [0, 1, 2] },
  { path: '/my-orders', title: '我的订单', icon: 'Tickets', roles: [0] },
  { path: '/invite', title: '访客邀请', icon: 'Promotion', roles: [0] },
  { path: '/order', title: '订单管理', icon: 'Document', roles: [1, 2] },
  { path: '/payment', title: '缴费管理', icon: 'Money', roles: [0, 1, 2] },
  { path: '/device', title: '设备管理', icon: 'Monitor', roles: [1, 2] },
  { path: '/statistics', title: '数据统计', icon: 'TrendCharts', roles: [1, 2] },
  { path: '/notification', title: '通知中心', icon: 'Bell', roles: [0, 1, 2] },
  { path: '/audit', title: '操作审计', icon: 'Memo', roles: [1, 2] },
  { path: '/user', title: '用户管理', icon: 'User', roles: [1, 2] },
  { path: '/profile', title: '个人中心', icon: 'Setting', roles: [0, 1, 2] }
]

// 按角色过滤菜单
const menuItems = computed(() => {
  const role = getRole()
  if (role == null) return []
  return allMenuItems.filter(item => item.roles.includes(role))
})

const loadUnreadCount = async () => {
  const userId = getUserId()
  if (!userId) return
  try {
    const res = await getUnreadCount(userId)
    unreadCount.value = res.data || 0
  } catch (e) {
    console.error('获取未读通知失败', e)
  }
}

watch(
  () => route.path,
  () => loadUnreadCount(),
  { immediate: true }
)

onMounted(async () => {
  try {
    await userStore.getUserInfoAction()
  } catch (e) {
    console.error('获取用户信息失败', e)
  }
})

const handleLogout = async () => {
  await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await userStore.logoutAction()
  router.push('/login')
}
</script>

<style lang="scss" scoped>
.layout-container {
  height: 100vh;
  background: var(--sp-bg);
}

.aside {
  background-color: var(--sp-sidebar-bg);
  border-right: 1px solid var(--sp-divider);
  transition: width 0.3s var(--sp-ease);
  overflow: hidden;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background-color: var(--sp-sidebar-logo-bg);
  border-bottom: 1px solid var(--sp-divider);

  .logo-img {
    width: 32px;
    height: 32px;
  }
}

.logo-text {
  color: var(--sp-text);
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 2px;
  white-space: nowrap;
}

.sidebar-menu {
  border-right: none;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: var(--sp-text-2);
  --el-menu-hover-bg-color: var(--sp-hover-bg);
  --el-menu-active-color: var(--sp-gold);

  :deep(.el-menu-item) {
    margin: 2px 8px;
    border-radius: var(--sp-radius-btn);

    &:hover {
      background-color: var(--sp-hover-bg);
      color: var(--sp-text);
    }

    &.is-active {
      background: var(--sp-active-grad);
      color: var(--sp-gold);
      box-shadow: inset 0 0 0 1px var(--sp-card-border);
    }
  }
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--sp-header-bg);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border-bottom: 1px solid var(--sp-divider);
  padding: 0 20px;
  height: 60px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;

  :deep(.el-breadcrumb__inner),
  :deep(.el-breadcrumb__separator) {
    color: var(--sp-text-3);
  }

  :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
    color: var(--sp-text-2);
  }
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: var(--sp-text-2);
  transition: color 0.2s var(--sp-ease);

  &:hover {
    color: var(--sp-teal);
  }
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;

  .el-icon {
    color: var(--sp-text-2);
    transition: color 0.2s var(--sp-ease);

    &:hover {
      color: var(--sp-gold);
    }
  }
}

.notification-badge {
  cursor: pointer;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: var(--sp-text);

  .username {
    font-size: 14px;
    color: var(--sp-text-2);
  }
}

.main {
  background: transparent;
  padding: 20px;
  overflow-y: auto;
}
</style>
