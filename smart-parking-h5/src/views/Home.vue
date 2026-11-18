<template>
  <div class="h-page">
    <van-pull-refresh v-model="refreshing" @refresh="loadData">
      <!-- 星河 Hero -->
      <header class="h-hero">
        <div class="h-hero__top">
          <div>
            <span class="h-hero__eyebrow">SMART GALAXY · 业主服务</span>
            <h1 class="h-hero__title">{{ greeting }}，{{ userInfo.realName || userInfo.username || '业主' }}</h1>
            <p class="h-hero__desc">
              已绑定 {{ vehicles.length }} 辆车 · {{ unread }} 条未读通知
            </p>
          </div>
          <button class="h-hero__action" type="button" @click="router.push('/notify')">
            <van-icon name="bell" />
            <span v-if="unread">通知 {{ unread > 99 ? '99+' : unread }}</span>
            <span v-else>通知</span>
          </button>
        </div>

        <div class="h-hero__stats">
          <div class="h-hero__stat" @click="router.push('/vehicles')">
            <template v-if="loading">
              <span class="h-sk h-sk--num" />
            </template>
            <template v-else>
              <b>{{ vehicles.length }}</b>
              <span>绑定车辆</span>
            </template>
          </div>
          <div class="h-hero__stat" data-tone="teal" @click="router.push('/orders')">
            <template v-if="loading">
              <span class="h-sk h-sk--num" />
            </template>
            <template v-else>
              <b>{{ ongoingOrders.length }}</b>
              <span>停车中</span>
            </template>
          </div>
          <div class="h-hero__stat" data-tone="gold" @click="router.push('/orders')">
            <template v-if="loading">
              <span class="h-sk h-sk--num" />
            </template>
            <template v-else>
              <b>{{ pendingPayOrders.length }}</b>
              <span>待缴费</span>
            </template>
          </div>
        </div>
      </header>

      <div class="h-body">
        <!-- 待缴提醒 -->
        <div v-if="pendingPayOrders.length" class="notice" @click="router.push('/orders')">
          <van-icon name="gold-coin-o" class="notice__icon" />
          <div class="notice__text">
            你有 {{ pendingPayOrders.length }} 笔订单待缴费，合计
            <b>¥{{ pendingAmount }}</b>
          </div>
          <van-icon name="arrow" class="notice__arrow" />
        </div>

        <!-- 快捷入口 -->
        <div class="h-grid entry">
          <div class="h-grid__item" @click="router.push('/vehicles')">
            <span class="h-grid__icon"><van-icon name="logistics" /></span>
            <span class="h-grid__text">我的车辆</span>
          </div>
          <div class="h-grid__item" @click="router.push('/orders')">
            <span class="h-grid__icon" data-tone="teal"><van-icon name="orders-o" /></span>
            <span class="h-grid__text">停车订单</span>
            <span v-if="pendingPayOrders.length" class="h-grid__badge">{{ pendingPayOrders.length }}</span>
          </div>
          <div class="h-grid__item" @click="router.push('/invite')">
            <span class="h-grid__icon" data-tone="gold"><van-icon name="friends-o" /></span>
            <span class="h-grid__text">访客邀请</span>
          </div>
          <div class="h-grid__item" @click="router.push('/notify')">
            <span class="h-grid__icon" data-tone="danger"><van-icon name="bell" /></span>
            <span class="h-grid__text">通知中心</span>
            <span v-if="unread" class="h-grid__badge">{{ unread > 99 ? '99+' : unread }}</span>
          </div>
        </div>

        <!-- 车主服务：月卡、车位、预约 -->
        <div class="h-section">
          <span class="h-section__title">车主服务</span>
        </div>
        <div class="h-grid entry3">
          <div class="h-grid__item" @click="router.push('/monthly')">
            <span class="h-grid__icon" data-tone="gold"><van-icon name="balance-o" /></span>
            <span class="h-grid__text">月卡充值</span>
          </div>
          <div class="h-grid__item" @click="router.push('/spaces')">
            <span class="h-grid__icon" data-tone="teal"><van-icon name="wap-home-o" /></span>
            <span class="h-grid__text">我的车位</span>
          </div>
          <div class="h-grid__item" @click="router.push('/reserve')">
            <span class="h-grid__icon"><van-icon name="clock-o" /></span>
            <span class="h-grid__text">车位预约</span>
          </div>
        </div>

        <!-- 进行中订单 -->
        <div class="h-section">
          <span class="h-section__title">进行中的停车</span>
          <span class="h-section__more" @click="router.push('/orders')">全部订单 ›</span>
        </div>

        <div class="h-card">
          <template v-if="loading">
            <div class="h-sk h-sk--row" />
            <div class="h-sk h-sk--row" style="margin-bottom: 0" />
          </template>
          <template v-else-if="ongoingOrders.length">
            <div v-for="o in ongoingOrders" :key="o.orderNo" class="run" @click="router.push('/orders')">
              <div class="run__main">
                <span class="h-plate" :data-color="o.plateColor || '蓝'">{{ displayPlate(o) }}</span>
                <div class="run__meta">
                  车位 {{ o.spaceNo || '-' }} · 入场 {{ shortTime(o.entryTime) }}
                </div>
              </div>
              <div class="run__right">
                <span class="h-pill" data-tone="teal">停车中</span>
                <b class="run__dur">{{ minutesSince(o.entryTime) }}</b>
              </div>
            </div>
          </template>
          <div v-else class="h-empty">
            <van-icon name="park" class="h-empty__icon" />
            <div class="h-empty__title">当前没有正在进行的停车</div>
            <div class="h-empty__hint">
              {{ vehicles.length ? '车辆入场后将自动生成订单' : '先绑定车辆，入场自动识别抬杆' }}
            </div>
            <button
              v-if="!vehicles.length"
              class="h-empty__cta"
              type="button"
              @click="router.push('/vehicles')"
            >
              <van-icon name="plus" /> 去绑定车辆
            </button>
          </div>
        </div>

        <!-- 最近记录 -->
        <div class="h-section">
          <span class="h-section__title">最近停车记录</span>
          <span class="h-section__more" @click="router.push('/orders')">查看更多 ›</span>
        </div>

        <div class="h-card">
          <template v-if="loading">
            <div class="h-sk h-sk--row" />
            <div class="h-sk h-sk--row" />
            <div class="h-sk h-sk--row" style="margin-bottom: 0" />
          </template>
          <template v-else-if="recentOrders.length">
            <div v-for="o in recentOrders" :key="o.orderNo" class="rec" @click="router.push('/orders')">
              <div>
                <div class="rec__plate">{{ displayPlate(o) }} · 车位 {{ o.spaceNo || '-' }}</div>
                <div class="rec__time">{{ shortTime(o.entryTime) }} → {{ shortTime(o.exitTime) }}</div>
              </div>
              <div class="rec__right">
                <b class="rec__amount">¥{{ money(o.amount) }}</b>
                <span class="h-pill" :data-tone="o.payStatus === 1 ? 'muted' : 'danger'">
                  {{ o.payStatus === 1 ? '已支付' : '待缴费' }}
                </span>
              </div>
            </div>
          </template>
          <div v-else class="h-empty">
            <van-icon name="records" class="h-empty__icon" />
            <div class="h-empty__title">还没有停车记录</div>
            <div class="h-empty__hint">完成一次停车后这里会显示明细</div>
            <button class="h-empty__cta" type="button" @click="router.push('/orders')">
              查看全部订单
            </button>
          </div>
        </div>

        <!-- 平台能力 -->
        <div class="h-section">
          <span class="h-section__title">智慧星河能力</span>
        </div>
        <div class="caps">
          <div class="caps__item">
            <van-icon name="scan" />
            <b>AI 车牌识别</b>
            <span>闸口自动抬杆入场</span>
          </div>
          <div class="caps__item">
            <van-icon name="cluster-o" />
            <b>IoT 设备联动</b>
            <span>道闸 · 地磁 · 摄像头</span>
          </div>
          <div class="caps__item">
            <van-icon name="balance-pay" />
            <b>在线缴费</b>
            <span>离场自动结算账单</span>
          </div>
        </div>

        <p class="foot">智慧星河 SmartGalaxy · 智慧社区停车服务平台</p>
      </div>
    </van-pull-refresh>

    <TabBar :order-badge="pendingPayOrders.length" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getUserInfo, getUserId } from '@/api/user'
import { getVehicleList } from '@/api/vehicle'
import { getOrderList } from '@/api/order'
import { getUnreadCount } from '@/api/notify'
import { displayPlate } from '@/utils/plate'
import TabBar from '@/components/TabBar.vue'

const router = useRouter()
const userId = getUserId()

const refreshing = ref(false)
// 首屏骨架：数据回来前不渲染 0 值，避免「0 → 4」数字跳变和列表闪空态
const loading = ref(true)
const userInfo = ref({})
const vehicles = ref([])
const allOrders = ref([])
const unread = ref(0)

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 11) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const ongoingOrders = computed(() => allOrders.value.filter(o => o.status === 0))
const pendingPayOrders = computed(() => allOrders.value.filter(o => o.status === 1 && o.payStatus === 0))
const pendingAmount = computed(() =>
  pendingPayOrders.value.reduce((s, o) => s + (Number(o.amount) || 0), 0).toFixed(2))
const recentOrders = computed(() => allOrders.value.filter(o => o.status === 1).slice(0, 3))

const money = (v) => (v == null ? '0.00' : Number(v).toFixed(2))

function shortTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(5, 16)
}

function minutesSince(t) {
  if (!t) return ''
  const start = new Date(String(t).replace(' ', 'T')).getTime()
  if (Number.isNaN(start)) return ''
  const mins = Math.max(0, Math.floor((Date.now() - start) / 60000))
  if (mins < 60) return `已停 ${mins} 分钟`
  return `已停 ${Math.floor(mins / 60)} 小时 ${mins % 60} 分`
}

onMounted(() => loadData())

async function loadData() {
  refreshing.value = true
  try {
    const [userRes, vRes, nRes] = await Promise.allSettled([
      getUserInfo(userId),
      getVehicleList(userId),          // 后端 /api/vehicle/list 返回 List<Vehicle>
      getUnreadCount(userId)
    ])
    if (userRes.status === 'fulfilled' && userRes.value.code === 200) {
      userInfo.value = userRes.value.data || {}
    }
    if (vRes.status === 'fulfilled' && vRes.value.code === 200) {
      vehicles.value = Array.isArray(vRes.value.data) ? vRes.value.data : []
    }
    if (nRes.status === 'fulfilled' && nRes.value.code === 200) {
      unread.value = Number(nRes.value.data) || 0
    }
  } finally {
    refreshing.value = false
  }

  try {
    // 业主（role=0）由后端按本人车辆强制过滤，前端无需再传 vehicleIds
    const oRes = await getOrderList({ current: 1, size: 50 })
    if (oRes.code === 200) allOrders.value = oRes.data?.records || []
  } catch {
    allOrders.value = []
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.notice {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-top: var(--sp-gap-card);
  padding: 11px 13px;
  background: var(--sp-danger-soft);
  border: 1px solid rgba(194, 94, 94, 0.18);
  border-radius: var(--sp-radius-btn);
  color: var(--sp-danger);
  font-size: 13px;
}
.notice__icon { font-size: 16px; }
.notice__text { flex: 1; }
.notice__text b { font-family: var(--sp-font-num); }
.notice__arrow { opacity: 0.6; }

.entry { margin-top: var(--sp-gap-card); }
/* 车主服务三宫格：与上方四宫格同款式，只是列数改成 3 */
.entry3 {
  grid-template-columns: repeat(3, 1fr);
  margin-bottom: var(--sp-2);
}

.run {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  padding: 11px 0;
}
.run + .run { border-top: 1px dashed var(--sp-divider); }
.run__meta { margin-top: 6px; font-size: 12px; color: var(--sp-text-3); }
.run__right { display: flex; flex-direction: column; align-items: flex-end; gap: 4px; }
.run__dur { font-size: 12px; font-weight: 600; color: var(--sp-text-2); }

.rec {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  padding: 10px 0;
}
.rec + .rec { border-top: 1px dashed var(--sp-divider); }
.rec__plate { font-size: 13px; font-weight: 600; }
.rec__time { margin-top: 4px; font-size: 11px; color: var(--sp-text-3); }
.rec__right { display: flex; flex-direction: column; align-items: flex-end; gap: 4px; }
.rec__amount { font-family: var(--sp-font-num); font-size: 14px; color: var(--sp-text); }

.caps {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--sp-2);
}
.caps__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 14px 6px;
  text-align: center;
  background: var(--sp-card);
  border: 1px solid var(--sp-card-border);
  border-radius: var(--sp-radius-card);
  box-shadow: var(--sp-shadow);
  font-size: 18px;
  color: var(--sp-brand);
}
.caps__item b { font-size: 12px; color: var(--sp-text); }
.caps__item span { font-size: 10px; color: var(--sp-text-3); line-height: 1.4; }

.foot {
  margin: var(--sp-5) 0 0;
  text-align: center;
  font-size: 11px;
  color: var(--sp-text-3);
}

/* 列表行整行可点：负边距让按压底色铺满卡片内宽，点下去立刻有反馈 */
.run, .rec {
  margin: 0 -8px;
  padding-left: 8px;
  padding-right: 8px;
  border-radius: 10px;
  transition: background-color var(--sp-dur-micro) var(--sp-ease);
}
.run:active, .rec:active { background: var(--sp-surface-2); }

/* Hero 深色底上的骨架屏改用半透明白，避免浅灰块突兀 */
.h-hero .h-sk { background: rgba(255, 255, 255, 0.16); }
.h-hero .h-sk::after {
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.32), transparent);
}
</style>
