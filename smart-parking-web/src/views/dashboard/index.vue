<template>
  <div class="dashboard">
    <!-- 业主视图 -->
    <template v-if="isOwner">
      <el-card shadow="hover" class="welcome-card">
        <div class="welcome-inner">
          <div>
            <h2 style="margin:0 0 6px;">欢迎回来，业主</h2>
            <p style="color:#909399;margin:0;">祝您停车顺利 · 今天是 {{ today }}</p>
          </div>
          <el-button type="primary" @click="$router.push('/reserve')">立即预约车位</el-button>
        </div>
      </el-card>

      <!-- 个人统计 -->
      <el-row :gutter="16" class="stat-row">
        <el-col :span="6">
          <el-card shadow="hover" class="mini-card">
            <div class="mini-num" style="color:#409eff">{{ myStats.vehicles || 0 }}</div>
            <div class="mini-label">我的车辆</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="mini-card">
            <div class="mini-num" style="color:#67c23a">{{ myStats.pendingOrders || 0 }}</div>
            <div class="mini-label">待支付订单</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="mini-card">
            <div class="mini-num" style="color:#e6a23c">{{ myStats.activeReserves || 0 }}</div>
            <div class="mini-label">进行中预约</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="mini-card">
            <div class="mini-num" style="color:#f56c6c">{{ myStats.invites || 0 }}</div>
            <div class="mini-label">有效邀请码</div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="16">
          <el-card shadow="hover">
            <template #header><span>快捷功能</span></template>
            <el-row :gutter="12">
              <el-col :span="8" v-for="item in ownerQuickLinks" :key="item.path">
                <div class="quick-item" @click="$router.push(item.path)">
                  <el-icon :size="28" :color="item.color"><component :is="item.icon" /></el-icon>
                  <div class="quick-title">{{ item.title }}</div>
                  <div class="quick-desc">{{ item.desc }}</div>
                </div>
              </el-col>
            </el-row>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="hover">
            <template #header><span>最新通知</span></template>
            <div v-if="notifications.length === 0" style="color:#909399;text-align:center;padding:30px 0;">暂无通知</div>
            <div v-for="n in notifications" :key="n.id" class="notice-item">
              <el-tag size="small" :type="n.type === 1 ? 'success' : 'warning'">
                {{ n.type === 1 ? '入场' : '出场' }}
              </el-tag>
              <div class="notice-text">{{ n.content }}</div>
              <div class="notice-time">{{ n.createTime }}</div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>

    <!-- 管理端视图 -->
    <template v-else>
      <el-row :gutter="20" class="stat-row">
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-content">
              <div class="stat-info">
                <div class="stat-title">今日入场</div>
                <div class="stat-value">{{ dashboardData.todayEntry || 0 }}</div>
              </div>
              <el-icon class="stat-icon" color="#409eff"><Bottom /></el-icon>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-content">
              <div class="stat-info">
                <div class="stat-title">今日出场</div>
                <div class="stat-value">{{ dashboardData.todayExit || 0 }}</div>
              </div>
              <el-icon class="stat-icon" color="#67c23a"><Top /></el-icon>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-content">
              <div class="stat-info">
                <div class="stat-title">今日收入</div>
                <div class="stat-value">¥{{ dashboardData.todayIncome || 0 }}</div>
              </div>
              <el-icon class="stat-icon" color="#e6a23c"><Money /></el-icon>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-content">
              <div class="stat-info">
                <div class="stat-title">在场车辆</div>
                <div class="stat-value">{{ dashboardData.currentParked || 0 }}</div>
              </div>
              <el-icon class="stat-icon" color="#f56c6c"><Van /></el-icon>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-card shadow="hover">
            <template #header><span>车位状态分布</span></template>
            <div ref="pieChartRef" class="chart-container"></div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="hover">
            <template #header><span>设备状态概览</span></template>
            <div class="device-overview">
              <div class="device-item">
                <span class="device-label">在线设备</span>
                <el-tag type="success">{{ deviceOverview.online || 0 }} 台</el-tag>
              </div>
              <div class="device-item">
                <span class="device-label">离线设备</span>
                <el-tag type="info">{{ deviceOverview.offline || 0 }} 台</el-tag>
              </div>
              <div class="device-item">
                <span class="device-label">故障设备</span>
                <el-tag type="danger">{{ deviceOverview.fault || 0 }} 台</el-tag>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="20" style="margin-top: 20px;">
        <el-col :span="24">
          <el-card shadow="hover">
            <template #header><span>本月收入统计</span></template>
            <div class="income-info">
              <div class="income-item">
                <span class="income-label">本月总收入</span>
                <span class="income-value">¥{{ dashboardData.monthIncome || 0 }}</span>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { getDashboardData } from '@/api/statistics'
import { getDeviceOverview } from '@/api/device'
import { getRole, getUserId } from '@/api/user'
import * as echarts from 'echarts'

const role = getRole()
const isOwner = computed(() => role === 0)
const today = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })

const ownerQuickLinks = [
  { path: '/reserve', title: '车位预约', desc: '预约空闲车位', icon: 'Position', color: '#409eff' },
  { path: '/my-orders', title: '我的订单', desc: '查看停车记录', icon: 'Tickets', color: '#67c23a' },
  { path: '/vehicle', title: '我的车辆', desc: '管理绑定车辆', icon: 'Van', color: '#e6a23c' },
  { path: '/invite', title: '访客邀请', desc: '生成访客码', icon: 'Promotion', color: '#f56c6c' },
  { path: '/payment', title: '缴费记录', desc: '查看账单', icon: 'Money', color: '#909399' },
  { path: '/notification', title: '通知消息', desc: '出入场提醒', icon: 'Bell', color: '#409eff' }
]

const myStats = ref({})
const notifications = ref([])
const dashboardData = ref({})
const deviceOverview = ref({})
const pieChartRef = ref(null)

onMounted(async () => {
  if (isOwner.value) {
    loadOwnerData()
    return
  }
  await loadDashboardData()
  await loadDeviceOverview()
  await nextTick()
  initPieChart()
})

const loadOwnerData = async () => {
  // 模拟个人统计（后续可对接真实接口）
  myStats.value = { vehicles: 1, pendingOrders: 0, activeReserves: 0, invites: 0 }
  notifications.value = []
}

const loadDashboardData = async () => {
  try {
    const res = await getDashboardData()
    dashboardData.value = res.data
  } catch (e) {
    console.error('加载看板数据失败', e)
  }
}

const loadDeviceOverview = async () => {
  try {
    const res = await getDeviceOverview()
    deviceOverview.value = res.data
  } catch (e) {
    console.error('加载设备数据失败', e)
  }
}

const initPieChart = () => {
  if (!pieChartRef.value) return
  const chart = echarts.init(pieChartRef.value)
  chart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: '5%' },
    series: [{
      name: '车位状态', type: 'pie', radius: ['40%', '70%'],
      itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
      data: [
        { value: 10, name: '空闲', itemStyle: { color: '#67c23a' } },
        { value: 5, name: '占用', itemStyle: { color: '#409eff' } },
        { value: 2, name: '预约', itemStyle: { color: '#e6a23c' } },
        { value: 1, name: '维护', itemStyle: { color: '#f56c6c' } }
      ]
    }]
  })
  window.addEventListener('resize', () => chart.resize())
}
</script>

<style lang="scss" scoped>
.dashboard {
  .welcome-card { margin-bottom: 16px; }
  .welcome-inner { display: flex; justify-content: space-between; align-items: center; }
  .stat-row { margin-bottom: 16px; }
  .mini-card { text-align: center; padding: 10px 0; }
  .mini-num { font-size: 30px; font-weight: bold; }
  .mini-label { color: #909399; font-size: 13px; margin-top: 4px; }
  .quick-item {
    padding: 16px; text-align: center; border-radius: 8px; cursor: pointer;
    transition: all .2s;
    &:hover { background: #f5f7fa; transform: translateY(-2px); }
  }
  .quick-title { font-weight: 600; margin-top: 8px; }
  .quick-desc { color: #909399; font-size: 12px; }
  .notice-item { padding: 10px 0; border-bottom: 1px solid #f0f0f0; }
  .notice-text { margin: 6px 0; font-size: 13px; }
  .notice-time { color: #c0c4cc; font-size: 12px; }
  .stat-card .stat-content { display: flex; justify-content: space-between; align-items: center; }
  .stat-card .stat-title { font-size: 14px; color: #909399; margin-bottom: 8px; }
  .stat-card .stat-value { font-size: 28px; font-weight: bold; }
  .stat-icon { font-size: 48px; opacity: 0.3; }
  .chart-container { height: 300px; }
  .device-overview { display: flex; flex-direction: column; gap: 20px; padding: 20px; }
  .device-item { display: flex; justify-content: space-between; align-items: center; }
  .income-info { display: flex; justify-content: space-around; padding: 20px; }
  .income-label { display: block; font-size: 14px; color: #909399; margin-bottom: 8px; }
  .income-value { font-size: 32px; font-weight: bold; color: #e6a23c; }
}
</style>
