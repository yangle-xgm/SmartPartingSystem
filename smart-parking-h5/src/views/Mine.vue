<template>
  <div class="h-page">
    <header class="h-hero">
      <div class="profile">
        <div class="profile__avatar">{{ avatarText }}</div>
        <div class="profile__info">
          <template v-if="loading">
            <span class="h-sk h-sk--line sk-name" />
            <span class="h-sk h-sk--line sk-account" />
          </template>
          <template v-else>
            <b class="profile__name">{{ userInfo.realName || userInfo.username || '业主' }}</b>
            <span class="profile__account">@{{ userInfo.username || '-' }}</span>
          </template>
        </div>
        <span class="h-pill profile__role" data-tone="gold">{{ roleMap[userInfo.role] || '业主' }}</span>
      </div>

      <div class="h-hero__stats">
        <div class="h-hero__stat">
          <span v-if="loading" class="h-sk h-sk--num" />
          <b v-else>{{ vehicleCount }}</b>
          <span>绑定车辆</span>
        </div>
        <div class="h-hero__stat" data-tone="teal">
          <span v-if="loading" class="h-sk h-sk--num" />
          <b v-else>{{ orderCount }}</b>
          <span>停车订单</span>
        </div>
        <div class="h-hero__stat" data-tone="gold">
          <span v-if="loading" class="h-sk h-sk--num" />
          <b v-else>{{ unread }}</b>
          <span>未读通知</span>
        </div>
      </div>
    </header>

    <div class="h-body">
      <div class="h-card" style="margin-top: var(--sp-gap-card);">
        <div class="h-card__head">
          <span class="h-card__title">账号信息</span>
          <span class="h-card__extra">数据已加密存储</span>
        </div>
        <div class="h-kv">
          <span class="h-kv__k">手机号</span>
          <span v-if="!loading" class="h-kv__v">{{ userInfo.phoneMasked || userInfo.phone || '未绑定' }}</span>
          <span v-else class="h-sk h-sk--line sk-val" />
        </div>
        <div class="h-kv">
          <span class="h-kv__k">角色</span>
          <span class="h-kv__v">{{ roleMap[userInfo.role] || '业主' }}</span>
        </div>
        <div class="h-kv">
          <span class="h-kv__k">注册时间</span>
          <span v-if="!loading" class="h-kv__v">{{ shortTime(userInfo.createTime) }}</span>
          <span v-else class="h-sk h-sk--line sk-val" />
        </div>
        <div class="h-kv">
          <span class="h-kv__k">所属小区</span>
          <span v-if="!loading" class="h-kv__v">{{ communityName || '未绑定小区' }}</span>
          <span v-else class="h-sk h-sk--line sk-val" />
        </div>
      </div>

      <div class="h-card">
        <div class="h-card__head">
          <span class="h-card__title">常用功能</span>
        </div>
        <div class="links">
          <div class="links__item" @click="router.push('/vehicles')">
            <span class="links__icon"><van-icon name="logistics" /></span>
            <span class="links__text">我的车辆</span>
            <van-icon name="arrow" class="links__arrow" />
          </div>
          <div class="links__item" @click="router.push('/orders')">
            <span class="links__icon" data-tone="teal"><van-icon name="orders-o" /></span>
            <span class="links__text">停车订单</span>
            <van-icon name="arrow" class="links__arrow" />
          </div>
          <div class="links__item" @click="router.push('/invite')">
            <span class="links__icon" data-tone="gold"><van-icon name="friends-o" /></span>
            <span class="links__text">访客邀请</span>
            <van-icon name="arrow" class="links__arrow" />
          </div>
          <div class="links__item" @click="router.push('/notify')">
            <span class="links__icon" data-tone="danger"><van-icon name="bell" /></span>
            <span class="links__text">通知中心</span>
            <span v-if="unread" class="links__badge">{{ unread > 99 ? '99+' : unread }}</span>
            <van-icon name="arrow" class="links__arrow" />
          </div>
        </div>
      </div>

      <button class="h-btn h-btn--ghost logout" type="button" @click="handleLogout">
        <van-icon name="revoke" />退出登录
      </button>

      <p class="foot">
        智慧星河 SmartGalaxy · v1.0.0<br />
        微服务架构 · AI 识别 · IoT 联动
      </p>
    </div>

    <TabBar />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { getUserInfo, getUserId, logout, getCommunityList } from '@/api/user'
import { getVehicleList } from '@/api/vehicle'
import { getOrderList } from '@/api/order'
import { getUnreadCount } from '@/api/notify'
import TabBar from '@/components/TabBar.vue'

const router = useRouter()
const userId = getUserId()

const userInfo = ref({})
const communityName = ref('')
const vehicleCount = ref(0)
const orderCount = ref(0)
const unread = ref(0)
/** 首屏加载态：资料与统计一次并发拉取，未回来前用骨架占位 */
const loading = ref(true)

const roleMap = { 0: '业主', 1: '管理员', 2: '超级管理员' }

const avatarText = computed(() => {
  const name = userInfo.value.realName || userInfo.value.username || '业'
  return String(name).slice(0, 1).toUpperCase()
})

function shortTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 16)
}

onMounted(async () => {
  const [u, v, o, n, c] = await Promise.allSettled([
    getUserInfo(userId),
    getVehicleList(userId),
    getOrderList({ current: 1, size: 1 }),
    getUnreadCount(userId),
    getCommunityList()
  ])
  if (u.status === 'fulfilled' && u.value.code === 200) userInfo.value = u.value.data || {}
  if (v.status === 'fulfilled' && v.value.code === 200) {
    vehicleCount.value = Array.isArray(v.value.data) ? v.value.data.length : 0
  }
  if (o.status === 'fulfilled' && o.value.code === 200) {
    orderCount.value = Number(o.value.data?.total) || 0
  }
  if (n.status === 'fulfilled' && n.value.code === 200) unread.value = Number(n.value.data) || 0
  // 用社区档案把 communityId 翻译成真实小区名（此前写死「默认小区」）。
  // 用户未绑定 communityId 时，网关会按缺省 X-Community-Id=1 处理，
  // 因此回退到列表首条（后端 ORDER BY id，1 号即默认小区），与后端口径一致。
  if (c.status === 'fulfilled' && c.value.code === 200) {
    const list = Array.isArray(c.value.data) ? c.value.data : []
    const cid = userInfo.value.communityId
    const hit = cid != null ? list.find((x) => Number(x.id) === Number(cid)) : null
    communityName.value = (hit || list[0])?.name || ''
  }
  loading.value = false
})

async function handleLogout() {
  try {
    await showConfirmDialog({
      title: '退出登录',
      message: '确定退出当前账号吗？'
    })
  } catch {
    return
  }
  try {
    await logout(userId)
  } catch { /* 后端会话清理失败不阻塞本地登出 */ }
  localStorage.removeItem('token')
  localStorage.removeItem('refreshToken')
  localStorage.removeItem('userId')
  localStorage.removeItem('role')
  showToast('已退出登录')
  router.replace('/login')
}
</script>

<style scoped>
.profile {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 12px;
}
.profile__avatar {
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 700;
  color: #2A2214;
  background: var(--sp-grad-gold);
  border-radius: 50%;
  box-shadow: 0 8px 20px rgba(240, 201, 135, 0.28);
}
.profile__info { flex: 1; min-width: 0; }
.profile__name {
  display: block;
  font-size: 18px;
  font-weight: 800;
  color: var(--sp-hero-text);
}
.profile__account {
  font-size: 12px;
  color: var(--sp-hero-text-2);
}
.profile__role { flex-shrink: 0; }

/* 资料骨架：宽度贴近真实文案，加载完不跳版 */
.sk-name { display: block; width: 92px; height: 15px; margin-bottom: 8px; }
.sk-account { display: block; width: 62px; height: 11px; }
.sk-val { display: block; width: 96px; height: 12px; }

.links__item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 0;
  transition: opacity 0.16s var(--sp-ease);
}
.links__item:active { opacity: 0.6; }
.links__item + .links__item { border-top: 1px solid var(--sp-divider); }
.links__icon {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  color: var(--sp-brand);
  background: var(--sp-brand-soft);
  border-radius: 9px;
}
.links__icon[data-tone='teal'] { color: var(--sp-teal); background: var(--sp-teal-soft); }
.links__icon[data-tone='gold'] { color: var(--sp-gold); background: var(--sp-gold-soft); }
.links__icon[data-tone='danger'] { color: var(--sp-danger); background: var(--sp-danger-soft); }
.links__text { flex: 1; font-size: 14px; color: var(--sp-text); }
.links__badge {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  font-size: 10px;
  line-height: 18px;
  text-align: center;
  color: #fff;
  background: var(--sp-danger);
  border-radius: var(--sp-radius-pill);
}
.links__arrow { font-size: 13px; color: var(--sp-text-3); }

.logout { margin-top: var(--sp-4); height: 42px; }

.foot {
  margin: var(--sp-5) 0 0;
  text-align: center;
  font-size: 11px;
  line-height: 1.8;
  color: var(--sp-text-3);
}
</style>
