<template>
  <div class="sp-page dashboard">
    <!-- ============ 管理端视图 ============ -->
    <template v-if="!isOwner">
      <PageHeader
        eyebrow="Control Center"
        title="运营数据看板"
        desc="实时掌握车流、车位、设备与收入；所有指标均来自各微服务实时接口，5 秒级缓存刷新。"
      >
        <template #actions>
          <el-button :loading="refreshing" @click="loadAdmin">
            <el-icon><Refresh /></el-icon>刷新数据
          </el-button>
          <el-button type="primary" @click="$router.push('/statistics')">
            数据分析<el-icon class="ml-1"><ArrowRight /></el-icon>
          </el-button>
        </template>
      </PageHeader>

      <div class="sp-stat-grid">
        <StatCard label="今日入场" :value="dashboard.todayEntry" unit="车次" icon="Bottom" tone="brand" hint="车牌照识别自动抬杆" />
        <StatCard label="今日出场" :value="dashboard.todayExit" unit="车次" icon="Top" tone="teal" hint="出场自动结算" />
        <StatCard label="当前在场" :value="dashboard.currentParked" unit="辆" icon="Van" tone="warning" hint="含跨日未出场车辆" />
        <StatCard label="今日收入" :value="money(dashboard.todayIncome)" unit="元" icon="Money" tone="gold" hint="以支付成功流水为准" />
        <StatCard label="本月收入" :value="money(dashboard.monthIncome)" unit="元" icon="TrendCharts" tone="gold" hint="自然月累计" />
      </div>

      <div class="grid-2">
        <section class="sp-card">
          <div class="sp-card__head">
            <span class="sp-card__title">车位实时分布 <span class="sp-card__sub">共 {{ spaceTotal }} 个车位</span></span>
            <div class="sp-legend">
              <span class="sp-legend__item"><i class="sp-dot" data-tone="free" />空闲</span>
              <span class="sp-legend__item"><i class="sp-dot" data-tone="used" />占用</span>
              <span class="sp-legend__item"><i class="sp-dot" data-tone="book" />预约</span>
              <span class="sp-legend__item"><i class="sp-dot" data-tone="fix" />维护</span>
            </div>
          </div>
          <div class="space-body">
            <div ref="pieRef" class="chart chart--pie" />
            <div class="space-side">
              <div class="rate">
                <b class="sp-num">{{ occupancyRate }}<i>%</i></b>
                <span>实时占用率</span>
              </div>
              <div class="sp-bar">
                <div class="sp-bar__fill" :style="{ width: occupancyRate + '%' }" />
              </div>
              <ul class="space-list">
                <li v-for="s in spaceLegend" :key="s.key">
                  <i class="sp-dot" :data-tone="s.tone" />
                  <span>{{ s.label }}</span>
                  <b class="sp-num">{{ s.value }}</b>
                </li>
              </ul>
            </div>
          </div>
        </section>

        <section class="sp-card">
          <div class="sp-card__head">
            <span class="sp-card__title">设备健康度 <span class="sp-card__sub">道闸 / 摄像头 / 地磁 / 显示屏</span></span>
            <el-tag :type="device.fault ? 'danger' : 'success'" size="small">
              {{ device.fault ? '存在故障设备' : '全部正常' }}
            </el-tag>
          </div>
          <div class="device-grid">
            <div class="device-item" data-tone="teal">
              <b class="sp-num">{{ device.online || 0 }}</b>
              <span>在线</span>
            </div>
            <div class="device-item" data-tone="brand">
              <b class="sp-num">{{ device.offline || 0 }}</b>
              <span>离线</span>
            </div>
            <div class="device-item" data-tone="danger">
              <b class="sp-num">{{ device.fault || 0 }}</b>
              <span>故障</span>
            </div>
            <div class="device-item" data-tone="gold">
              <b class="sp-num">{{ device.total || 0 }}</b>
              <span>总数</span>
            </div>
          </div>
          <div class="type-bars">
            <div v-for="t in deviceTypes" :key="t.key" class="type-row">
              <span class="type-name">{{ t.label }}</span>
              <div class="sp-bar"><div class="sp-bar__fill" :style="{ width: t.pct + '%' }" /></div>
              <b class="sp-num">{{ t.count }}</b>
            </div>
          </div>
          <el-button class="device-link" text type="primary" @click="$router.push('/device')">
            进入设备管理
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </section>
      </div>

      <div class="grid-2">
        <section class="sp-card">
          <div class="sp-card__head">
            <span class="sp-card__title">近 7 日车位使用趋势</span>
            <span class="sp-card__sub">数据来源：/api/stats/occupancy</span>
          </div>
          <div ref="trendRef" class="chart chart--trend" />
        </section>

        <section class="sp-card">
          <div class="sp-card__head">
            <span class="sp-card__title">最新通知</span>
            <el-button text type="primary" size="small" @click="$router.push('/notification')">全部</el-button>
          </div>
          <ul v-if="notices.length" class="notice-list">
            <li v-for="n in notices" :key="n.id">
              <el-tag size="small" :type="noticeTone[n.type]" effect="light">{{ noticeText[n.type] }}</el-tag>
              <div class="notice-main">
                <b>{{ n.title }}</b>
                <span>{{ n.content }}</span>
              </div>
              <time>{{ shortTime(n.createTime) }}</time>
            </li>
          </ul>
          <div v-else class="sp-empty">
            <span class="sp-empty__star">✦</span>
            <span class="sp-empty__text">暂无通知，系统运行安静如星河</span>
          </div>
        </section>
      </div>

      <section class="sp-card">
        <div class="sp-card__head">
          <span class="sp-card__title">平台核心能力</span>
          <span class="sp-card__sub">智慧星河 · 11 个微服务协同</span>
        </div>
        <div class="sp-feature-strip">
          <div v-for="f in features" :key="f.title" class="sp-feature">
            <span class="sp-feature__icon"><el-icon><component :is="f.icon" /></el-icon></span>
            <div>
              <p class="sp-feature__title">{{ f.title }}</p>
              <p class="sp-feature__desc">{{ f.desc }}</p>
            </div>
          </div>
        </div>
      </section>
    </template>

    <!-- ============ 业主端视图 ============ -->
    <template v-else>
      <section class="owner-hero">
        <div class="owner-hero__main">
          <span class="sp-eyebrow">WELCOME BACK</span>
          <h1 class="sp-title">{{ greeting }}，{{ userInfo?.realName || userInfo?.username || '业主' }}</h1>
          <p class="sp-desc">
            {{ today }}
            <template v-if="myPlates.length">
              · 已绑定 {{ myPlates.slice(0, 3).join('、') }}<template v-if="myPlates.length > 3"> 等 {{ myPlates.length }} 辆</template>
            </template>
            <template v-else> · 还没有绑定车牌，绑定后即可无感通行</template>
          </p>
          <div class="owner-hero__actions">
            <el-button type="primary" @click="$router.push('/reserve')">
              <el-icon><Position /></el-icon>预约车位
            </el-button>
            <el-button @click="$router.push('/invite')">
              <el-icon><Promotion /></el-icon>邀请访客
            </el-button>
            <el-button v-if="!myPlates.length" text type="primary" @click="$router.push('/vehicle')">去绑定车牌</el-button>
          </div>
        </div>
        <div class="owner-hero__badge">
          <span>当前停车</span>
          <b class="sp-num">{{ currentOrder ? (currentOrder.duration || 0) : 0 }}</b>
          <i>分钟</i>
          <p v-if="currentOrder">车位 {{ currentOrder.spaceNo }} · 预计 ¥{{ money(currentOrder.amount) }}</p>
          <p v-else>暂无进行中的停车</p>
        </div>
      </section>

      <div class="sp-stat-grid">
        <StatCard label="我的车辆" :value="myStats.vehicles" unit="辆" icon="Van" tone="brand" hint="车牌 AES 加密存储" />
        <StatCard label="待支付订单" :value="myStats.pending" unit="笔" icon="Wallet" tone="gold" hint="出场后 15 分钟内完成支付" />
        <StatCard label="进行中预约" :value="myStats.reserves" unit="个" icon="Position" tone="teal" hint="超时未入场自动释放" />
        <StatCard label="有效访客码" :value="myStats.invites" unit="个" icon="Promotion" tone="warning" hint="有效期内可多次进出" />
      </div>

      <div class="grid-2">
        <section class="sp-card">
          <div class="sp-card__head">
            <span class="sp-card__title">最近停车记录</span>
            <el-button text type="primary" size="small" @click="$router.push('/my-orders')">全部订单</el-button>
          </div>
          <ul v-if="recentOrders.length" class="owner-orders">
            <li v-for="o in recentOrders" :key="o.orderNo">
              <span class="sp-plate">{{ displayPlate(o) }}</span>
              <div class="owner-orders__main">
                <b>{{ o.spaceNo }} 车位</b>
                <span>入场 {{ shortTime(o.entryTime) }}<template v-if="o.exitTime"> · 出场 {{ shortTime(o.exitTime) }}</template></span>
              </div>
              <div class="owner-orders__right">
                <el-tag size="small" :type="orderStatusView(o).tone">
                  {{ orderStatusView(o).text }}
                </el-tag>
                <b class="sp-num">{{ o.amount != null ? '¥' + money(o.amount) : '—' }}</b>
              </div>
            </li>
          </ul>
          <div v-else class="sp-empty">
            <span class="sp-empty__star">✦</span>
            <span class="sp-empty__text">还没有停车记录</span>
          </div>
        </section>

        <section class="sp-card">
          <div class="sp-card__head">
            <span class="sp-card__title">快捷入口</span>
            <span class="sp-card__sub">常用功能一步直达</span>
          </div>
          <div class="quick-grid">
            <button v-for="q in quickLinks" :key="q.path" class="quick" @click="$router.push(q.path)">
              <span class="quick__icon"><el-icon><component :is="q.icon" /></el-icon></span>
              <b>{{ q.title }}</b>
              <i>{{ q.desc }}</i>
            </button>
          </div>
        </section>
      </div>

      <section class="sp-card">
        <div class="sp-card__head">
          <span class="sp-card__title">最新消息</span>
          <el-button text type="primary" size="small" @click="$router.push('/notification')">消息中心</el-button>
        </div>
        <ul v-if="notices.length" class="notice-list">
          <li v-for="n in notices" :key="n.id">
            <el-tag size="small" :type="noticeTone[n.type]" effect="light">{{ noticeText[n.type] }}</el-tag>
            <div class="notice-main">
              <b>{{ n.title }}</b>
              <span>{{ n.content }}</span>
            </div>
            <time>{{ shortTime(n.createTime) }}</time>
          </li>
        </ul>
        <div v-else class="sp-empty">
          <span class="sp-empty__star">✦</span>
          <span class="sp-empty__text">暂无消息</span>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import * as echarts from 'echarts'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import { getDashboardData, getOccupancyStats } from '@/api/statistics'
import { getDeviceOverview } from '@/api/device'
import { getAllSpaces, myReservations } from '@/api/parking'
import { getOrderList, getInviteList } from '@/api/order'
import { getNotifyList } from '@/api/notify'
import { getVehicleList } from '@/api/vehicle'
import { getRole, getUserId } from '@/api/user'
import { displayPlate } from '@/utils/plate'
import { isPendingPay, orderStatusView } from '@/utils/order'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const role = getRole()
const isOwner = computed(() => role === 0)
const userInfo = computed(() => userStore.userInfo)

const refreshing = ref(false)
const dashboard = ref({})
const device = ref({})
const spaces = ref([])
const notices = ref([])

const myStats = reactive({ vehicles: 0, pending: 0, reserves: 0, invites: 0 })
const myPlates = ref([])
const recentOrders = ref([])
const currentOrder = computed(() => recentOrders.value.find(o => o.status === 0) || null)

const today = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })
const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 11) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const noticeText = { 1: '入场', 2: '出场', 3: '缴费', 4: '公告' }
const noticeTone = { 1: 'success', 2: 'warning', 3: 'danger', 4: 'primary' }

const features = [
  { icon: 'Camera', title: 'AI 车牌识别', desc: '百度 OCR 真实接入，失败自动降级' },
  { icon: 'Promotion', title: '无感通行', desc: '识别成功即抬杆，异常转人工确认' },
  { icon: 'Money', title: '智能计费', desc: '新能源优惠、月卡、访客分规则' },
  { icon: 'Friends', title: '访客白名单', desc: '一次登记，有效期内多次进出' },
  { icon: 'ChatDotRound', title: '豆包 AI 客服', desc: '大模型问答，缺省降级本地知识库' },
  { icon: 'Share', title: '共享车位分润', desc: '闲置车位开放临停并按比例分润' }
]

const quickLinks = [
  { path: '/reserve', title: '车位预约', desc: '实时地图选位', icon: 'Position' },
  { path: '/my-orders', title: '我的订单', desc: '停车与缴费记录', icon: 'Tickets' },
  { path: '/vehicle', title: '我的车辆', desc: '绑定 / 解绑车牌', icon: 'Van' },
  { path: '/invite', title: '访客邀请', desc: '生成访客核验码', icon: 'Promotion' },
  { path: '/payment', title: '缴费记录', desc: '账单与退款', icon: 'Money' },
  { path: '/notification', title: '消息中心', desc: '出入场提醒', icon: 'Bell' }
]

const money = (v) => (v === null || v === undefined || v === '' ? '0.00' : Number(v).toFixed(2))
const shortTime = (t) => (t ? String(t).replace('T', ' ').slice(5, 16) : '—')

/* ================= 管理端数据 ================= */
const spaceTotal = computed(() => spaces.value.length)
const spaceCount = computed(() => {
  const c = { 0: 0, 1: 0, 2: 0, 3: 0 }
  spaces.value.forEach(s => { c[s.status] = (c[s.status] || 0) + 1 })
  return c
})
const spaceLegend = computed(() => [
  { key: 'free', label: '空闲车位', tone: 'free', value: spaceCount.value[0] || 0 },
  { key: 'used', label: '已占用', tone: 'used', value: spaceCount.value[1] || 0 },
  { key: 'book', label: '预约中', tone: 'book', value: spaceCount.value[2] || 0 },
  { key: 'fix', label: '维护中', tone: 'fix', value: spaceCount.value[3] || 0 }
])
const occupancyRate = computed(() => {
  if (!spaceTotal.value) return 0
  return Math.round(((spaceCount.value[1] || 0) / spaceTotal.value) * 100)
})

const deviceTypes = computed(() => {
  const labels = { 1: '道闸', 2: '摄像头', 3: '地磁', 4: '显示屏' }
  const tc = device.value.typeCount || {}
  const max = Math.max(1, ...Object.values(tc).map(Number))
  return [1, 2, 3, 4].map(t => {
    const count = Number(tc['type_' + t] || 0)
    return { key: t, label: labels[t], count, pct: Math.round((count / max) * 100) }
  })
})

const loadAdmin = async () => {
  refreshing.value = true
  try {
    // 各接口互不阻塞：单个失败不影响整体看板
    const [dash, dev, sp, nt] = await Promise.allSettled([
      getDashboardData(),
      getDeviceOverview(),
      getAllSpaces(),
      getNotifyList({ current: 1, size: 5 })
    ])
    if (dash.status === 'fulfilled') dashboard.value = dash.value.data || {}
    if (dev.status === 'fulfilled') device.value = dev.value.data || {}
    if (sp.status === 'fulfilled') spaces.value = sp.value.data || []
    if (nt.status === 'fulfilled') notices.value = nt.value.data?.records || []

    await nextTick()
    renderPie()
    renderTrend()
  } finally {
    refreshing.value = false
  }
}

const renderTrend = async () => {
  if (!trendRef.value) return
  const end = new Date()
  const start = new Date()
  start.setDate(end.getDate() - 6)
  // 用本地时区格式化，避免 toISOString 在 UTC+8 凌晨把日期前移一天
  const fmt = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  try {
    const res = await getOccupancyStats(fmt(start), fmt(end))
    const d = res.data || {}
    const dates = (d.dates || []).map(x => String(x).slice(5))
    trendChart = trendChart || echarts.init(trendRef.value)
    trendChart.setOption({
      grid: { left: 8, right: 12, top: 28, bottom: 4, containLabel: true },
      tooltip: { trigger: 'axis' },
      legend: { right: 0, top: 0, itemWidth: 10, itemHeight: 10, textStyle: { color: palette.text2 } },
      xAxis: {
        type: 'category',
        data: dates.length ? dates : ['暂无数据'],
        axisLine: { lineStyle: { color: palette.line } },
        axisLabel: { color: palette.text3, fontSize: 11 },
        axisTick: { show: false }
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: palette.line, type: 'dashed' } },
        axisLabel: { color: palette.text3, fontSize: 11 }
      },
      series: [
        {
          name: '总车位', type: 'line', smooth: true, symbol: 'circle', symbolSize: 6,
          data: d.totalSpaces || [], lineStyle: { width: 2, color: palette.brand }, itemStyle: { color: palette.brand },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(76,111,168,0.22)' },
              { offset: 1, color: 'rgba(76,111,168,0)' }
            ])
          }
        },
        {
          name: '已占用', type: 'line', smooth: true, symbol: 'circle', symbolSize: 6,
          data: d.occupiedSpaces || [], lineStyle: { width: 2, color: palette.teal }, itemStyle: { color: palette.teal },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(46,158,124,0.18)' },
              { offset: 1, color: 'rgba(46,158,124,0)' }
            ])
          }
        }
      ]
    })
  } catch (e) {
    /* 统计服务未就绪时保持空图 */
  }
}

/* ================= 业主端数据 ================= */
const loadOwner = async () => {
  const userId = getUserId()
  if (!userId) return
  refreshing.value = true
  try {
    const [vehicles, orders, reserves, invites, nt] = await Promise.allSettled([
      getVehicleList(userId),
      getOrderList({ current: 1, size: 5 }),
      myReservations(userId),
      getInviteList({ userId, current: 1, size: 50 }),
      getNotifyList({ current: 1, size: 5 })
    ])

    const vlist = vehicles.status === 'fulfilled' ? (vehicles.value.data || []) : []
    myStats.vehicles = vlist.length
    // D7：保留全部车牌，展示时只显示前 3 个并标注总数，避免"绑定 4 辆却只看到 3 个"的误解
    myPlates.value = vlist.map(v => v.plateNoMasked || '已加密')

    const olist = orders.status === 'fulfilled' ? (orders.value.data?.records || []) : []
    recentOrders.value = olist
    // 待支付口径与「我的订单」一致：0 元单已完成，不算待支付（DEF-06）
    myStats.pending = olist.filter(isPendingPay).length

    const rlist = reserves.status === 'fulfilled' ? (reserves.value.data || []) : []
    myStats.reserves = rlist.filter(r => r.status === 0).length

    const ilist = invites.status === 'fulfilled' ? (invites.value.data?.records || []) : []
    const now = Date.now()
    myStats.invites = ilist.filter(i => i.status === 0 && (!i.expireTime || new Date(i.expireTime).getTime() > now)).length

    notices.value = nt.status === 'fulfilled' ? (nt.value.data?.records || []) : []
  } finally {
    refreshing.value = false
  }
}

/* ================= 图表 ================= */
const pieRef = ref(null)
const trendRef = ref(null)
let pieChart = null
let trendChart = null

const palette = {
  brand: '#4C6FA8',
  teal: '#2E9E7C',
  gold: '#B07B24',
  danger: '#C25E5E',
  warning: '#C98F2F',
  line: 'rgba(35,40,56,0.08)',
  text2: '#5A6577',
  text3: '#5F6B85'
}

const renderPie = () => {
  if (!pieRef.value) return
  pieChart = pieChart || echarts.init(pieRef.value)
  const legend = spaceLegend.value
  pieChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}：{c} 个（{d}%）' },
    series: [
      {
        type: 'pie',
        radius: ['58%', '82%'],
        center: ['50%', '50%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 3 },
        label: { show: false },
        emphasis: { scale: true, scaleSize: 6 },
        data: [
          { value: legend[0].value, name: '空闲', itemStyle: { color: palette.teal } },
          { value: legend[1].value, name: '占用', itemStyle: { color: palette.brand } },
          { value: legend[2].value, name: '预约', itemStyle: { color: palette.warning } },
          { value: legend[3].value, name: '维护', itemStyle: { color: palette.danger } }
        ]
      }
    ]
  })
}

const onResize = () => {
  pieChart?.resize()
  trendChart?.resize()
}

onMounted(async () => {
  window.addEventListener('resize', onResize)
  if (isOwner.value) {
    await loadOwner()
  } else {
    await loadAdmin()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  pieChart?.dispose()
  trendChart?.dispose()
  pieChart = null
  trendChart = null
})
</script>

<style scoped>
.grid-2 {
  display: grid;
  grid-template-columns: 1.15fr 1fr;
  gap: var(--sp-4);
}

.ml-1 {
  margin-left: 4px;
}

/* ---------- 车位分布 ---------- */
.space-body {
  display: grid;
  grid-template-columns: 210px 1fr;
  gap: var(--sp-4);
  align-items: center;
}

.chart--pie {
  height: 200px;
}

.chart--trend {
  height: 244px;
}

.space-side {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rate b {
  font-size: 34px;
  font-weight: 800;
  color: var(--sp-text);
  letter-spacing: -0.02em;
}

.rate b i {
  font-style: normal;
  font-size: 15px;
  color: var(--sp-text-3);
  margin-left: 2px;
}

.rate span {
  display: block;
  font-size: 12px;
  color: var(--sp-text-3);
  margin-top: 2px;
}

.space-list {
  list-style: none;
  margin-top: 6px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.space-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
  color: var(--sp-text-2);
  padding: 6px 10px;
  border-radius: 10px;
  background: var(--sp-surface-2);
}

.space-list b {
  margin-left: auto;
  color: var(--sp-text);
  font-weight: 700;
}

/* ---------- 设备 ---------- */
.device-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.device-item {
  padding: 12px 8px;
  border-radius: 12px;
  background: var(--sp-surface-2);
  text-align: center;
}

.device-item b {
  display: block;
  font-size: 22px;
  font-weight: 800;
  color: var(--sp-text);
}

.device-item span {
  font-size: 11.5px;
  color: var(--sp-text-3);
}

.device-item[data-tone='teal'] b { color: var(--sp-teal); }
.device-item[data-tone='brand'] b { color: var(--sp-brand); }
.device-item[data-tone='danger'] b { color: var(--sp-danger); }
.device-item[data-tone='gold'] b { color: var(--sp-gold); }

.type-bars {
  margin-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.type-row {
  display: grid;
  grid-template-columns: 54px 1fr 30px;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: var(--sp-text-2);
}

.type-row b {
  text-align: right;
  color: var(--sp-text);
}

.device-link {
  margin-top: 8px;
}

/* ---------- 通知 ---------- */
.notice-list {
  list-style: none;
  display: flex;
  flex-direction: column;
}

.notice-list li {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 11px 0;
  border-bottom: 1px dashed var(--sp-divider);
}

.notice-list li:last-child {
  border-bottom: none;
}

.notice-main {
  flex: 1;
  min-width: 0;
}

.notice-main b {
  display: block;
  font-size: 13px;
  color: var(--sp-text);
  font-weight: 600;
}

.notice-main span {
  display: block;
  font-size: 12px;
  color: var(--sp-text-3);
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-list time {
  font-size: 11px;
  color: var(--sp-text-3);
  white-space: nowrap;
}

/* ---------- 业主端 ---------- */
.owner-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-6);
  padding: 26px 28px;
  border-radius: var(--sp-radius-card);
  border: 1px solid var(--sp-card-border);
  background:
    radial-gradient(700px at 100% 0%, var(--sp-gold-soft) 0%, transparent 58%),
    radial-gradient(500px at 0% 120%, var(--sp-brand-soft) 0%, transparent 62%),
    var(--sp-surface);
}

.owner-hero__actions {
  margin-top: 16px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.owner-hero__badge {
  flex: none;
  width: 190px;
  padding: 16px;
  border-radius: 14px;
  background: var(--sp-surface-2);
  border: 1px dashed var(--sp-card-border);
  text-align: center;
}

.owner-hero__badge span {
  font-size: 11.5px;
  color: var(--sp-text-3);
  letter-spacing: 0.1em;
}

.owner-hero__badge b {
  display: block;
  font-size: 38px;
  font-weight: 800;
  line-height: 1.1;
  color: var(--sp-brand);
  margin: 4px 0 0;
}

.owner-hero__badge i {
  font-style: normal;
  font-size: 12px;
  color: var(--sp-text-3);
}

.owner-hero__badge p {
  margin-top: 8px;
  font-size: 11.5px;
  color: var(--sp-text-2);
  line-height: 1.5;
}

.owner-orders {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.owner-orders li {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 12px;
  background: var(--sp-surface-2);
}

.owner-orders__main {
  flex: 1;
  min-width: 0;
}

.owner-orders__main b {
  display: block;
  font-size: 13px;
  color: var(--sp-text);
}

.owner-orders__main span {
  font-size: 11.5px;
  color: var(--sp-text-3);
}

.owner-orders__right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

.owner-orders__right b {
  font-size: 14px;
  color: var(--sp-gold);
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.quick {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 14px;
  border-radius: 12px;
  border: 1px solid var(--sp-card-border);
  background: var(--sp-surface);
  cursor: pointer;
  text-align: left;
  transition: all var(--sp-dur-micro) var(--sp-ease);
}

.quick:hover {
  border-color: var(--sp-brand);
  background: var(--sp-brand-soft);
  transform: translateY(-2px);
}

.quick__icon {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  background: var(--sp-brand-soft);
  color: var(--sp-brand);
  font-size: 15px;
}

.quick b {
  font-size: 13px;
  color: var(--sp-text);
}

.quick i {
  font-style: normal;
  font-size: 11px;
  color: var(--sp-text-3);
}

@media (max-width: 1200px) {
  .grid-2 {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .space-body {
    grid-template-columns: 1fr;
  }

  .owner-hero {
    flex-direction: column;
    align-items: flex-start;
  }

  .owner-hero__badge {
    width: 100%;
  }

  .quick-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
