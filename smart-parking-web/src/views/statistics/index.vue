<template>
  <div class="sp-page">
    <PageHeader
      eyebrow="Analytics"
      title="数据统计"
      desc="收入、车位利用率与高峰时段三维分析，数据来自订单与支付服务的汇总接口，可按日期区间导出 CSV 报表。"
    >
      <template #actions>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          style="width: 260px"
          @change="loadAll"
        />
        <el-button type="primary" :loading="exporting" @click="handleExport">
          <el-icon><Download /></el-icon>导出报表
        </el-button>
      </template>
    </PageHeader>

    <div class="sp-stat-grid">
      <StatCard label="本期总收入" :value="money(income.totalIncome)" unit="元" icon="Money" tone="gold" :hint="`统计 ${income.days || days} 天`" />
      <StatCard label="日均收入" :value="money(income.avgDailyIncome)" unit="元" icon="TrendCharts" tone="brand" hint="总收入 / 统计天数" />
      <StatCard label="平均利用率" :value="avgRate" unit="%" icon="PieChart" tone="teal" hint="按日车位占用均值" />
      <StatCard label="高峰时段" :value="peakLabel" icon="Clock" tone="warning" hint="当日入场最集中的一小时" />
    </div>

    <div class="grid-2">
      <section class="sp-card">
        <div class="sp-card__head">
          <span class="sp-card__title">收入汇总</span>
          <span class="sp-card__sub">{{ dateRange?.[0] }} ~ {{ dateRange?.[1] }}</span>
        </div>
        <div ref="incomeRef" class="chart" />
      </section>

      <section class="sp-card">
        <div class="sp-card__head">
          <span class="sp-card__title">车位利用率趋势</span>
          <span class="sp-card__sub">按日统计占用情况</span>
        </div>
        <div ref="occupancyRef" class="chart" />
      </section>
    </div>

    <section class="sp-card">
      <div class="sp-card__head">
        <span class="sp-card__title">高峰时段分析</span>
        <div class="peak-tools">
          <span class="sp-card__sub">按入场小时统计车流</span>
          <el-date-picker
            v-model="peakDate"
            type="date"
            placeholder="选择日期"
            value-format="YYYY-MM-DD"
            style="width: 150px"
            @change="loadPeak"
          />
        </div>
      </div>
      <div ref="peakRef" class="chart chart--peak" />
    </section>

    <section class="sp-card">
      <div class="sp-card__head">
        <span class="sp-card__title">平台核心能力</span>
        <span class="sp-card__sub">数据链路：订单 → 支付 → 统计（MQ 最终一致）</span>
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
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { toastOnce } from '@/utils/request'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import { getIncomeStats, getPeakHours, getOccupancyStats, exportStats } from '@/api/statistics'

const palette = {
  brand: '#4C6FA8',
  teal: '#2E9E7C',
  gold: '#B07B24',
  warning: '#C98F2F',
  grid: 'rgba(35,40,56,0.08)',
  text2: '#5A6577',
  text3: '#5F6B85'
}

// 本地时区当天（toISOString 走 UTC，在 UTC+8 凌晨会少一天）
const now = new Date()
const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
const monthStart = today.slice(0, 7) + '-01'

const dateRange = ref([monthStart, today])
const peakDate = ref(today)
const exporting = ref(false)
const income = ref({})
const occupancy = ref({})
const peak = ref([])

const incomeRef = ref(null)
const occupancyRef = ref(null)
const peakRef = ref(null)
let incomeChart = null
let occupancyChart = null
let peakChart = null

const money = (v) => (v === null || v === undefined || v === '' ? '0.00' : Number(v).toFixed(2))
const days = computed(() => income.value.days || 0)
const avgRate = computed(() => {
  // 后端 avgOccupancy 是 0~1 的小数（occupied/total 的均值），指标卡带 % 单位，需乘 100
  const r = Number(occupancy.value.avgOccupancy || 0)
  return (r * 100).toFixed(1)
})
const peakLabel = computed(() => {
  if (!peak.value.length) return '—'
  const top = peak.value.reduce((a, b) => (Number(b.count) > Number(a.count) ? b : a))
  return `${String(top.hour).padStart(2, '0')}:00`
})

onMounted(async () => {
  await nextTick()
  await loadAll()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  incomeChart?.dispose()
  occupancyChart?.dispose()
  peakChart?.dispose()
})

const onResize = () => {
  incomeChart?.resize()
  occupancyChart?.resize()
  peakChart?.resize()
}

const loadAll = async () => {
  if (!dateRange.value || dateRange.value.length !== 2) return
  const [start, end] = dateRange.value
  await Promise.all([loadIncome(start, end), loadOccupancy(start, end), loadPeak()])
}

const loadIncome = async (start, end) => {
  try {
    const res = await getIncomeStats(start, end)
    income.value = res.data || {}
    if (!incomeRef.value) return
    // D13：区间内没有任何收入时，图表只剩 0~1 的坐标轴与两根 0 柱，
    // 看起来像"加载失败"。与另外两张图一致地给出空态文案。
    const noIncome = Number(income.value.totalIncome || 0) === 0
      && Number(income.value.avgDailyIncome || 0) === 0
    incomeChart = incomeChart || echarts.init(incomeRef.value)
    incomeChart.setOption({
      grid: { left: 8, right: 16, top: 30, bottom: 4, containLabel: true },
      graphic: emptyGraphic(noIncome, '当前区间暂无收入数据'),
      tooltip: { trigger: 'axis', valueFormatter: (v) => '¥' + Number(v).toFixed(2) },
      xAxis: {
        type: 'category',
        data: ['总收入', '日均收入'],
        axisLine: { lineStyle: { color: palette.grid } },
        axisTick: { show: false },
        axisLabel: { color: palette.text2, fontSize: 12 }
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: palette.grid, type: 'dashed' } },
        axisLabel: { color: palette.text3, fontSize: 11 }
      },
      series: [
        {
          type: 'bar',
          barWidth: 64,
          data: [
            {
              value: Number(income.value.totalIncome || 0),
              itemStyle: {
                borderRadius: [8, 8, 0, 0],
                color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                  { offset: 0, color: '#E3B96B' },
                  { offset: 1, color: '#B07B24' }
                ])
              }
            },
            {
              value: Number(income.value.avgDailyIncome || 0),
              itemStyle: {
                borderRadius: [8, 8, 0, 0],
                color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                  { offset: 0, color: '#6B89C4' },
                  { offset: 1, color: '#4C6FA8' }
                ])
              }
            }
          ],
          label: {
            show: true,
            position: 'top',
            color: palette.text2,
            fontSize: 12,
            formatter: (p) => '¥' + Number(p.value).toFixed(2)
          }
        }
      ]
    })
  } catch (e) {
    income.value = {}
  }
}

/* DEF-11：图表空态 —— 原来接口返回空数组时只剩坐标轴，看不出是"没数据"还是"加载失败" */
const emptyGraphic = (visible, text = '暂无数据') => (visible
  ? [{
      type: 'text',
      left: 'center',
      top: 'middle',
      style: { text, fill: palette.text3, fontSize: 13, fontFamily: 'inherit' }
    }]
  : [])

const loadOccupancy = async (start, end) => {
  try {
    const res = await getOccupancyStats(start, end)
    occupancy.value = res.data || {}
    if (!occupancyRef.value) return
    const dates = (occupancy.value.dates || []).map(d => String(d).slice(5))
    const hasData = dates.length > 0
    occupancyChart = occupancyChart || echarts.init(occupancyRef.value)
    occupancyChart.setOption({
      grid: { left: 8, right: 16, top: 30, bottom: 4, containLabel: true },
      graphic: emptyGraphic(!hasData),
      tooltip: { trigger: 'axis' },
      legend: { right: 0, top: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { color: palette.text2, fontSize: 11 } },
      xAxis: {
        type: 'category',
        data: dates,
        axisLine: { lineStyle: { color: palette.grid } },
        axisTick: { show: false },
        axisLabel: { color: palette.text3, fontSize: 11 }
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: palette.grid, type: 'dashed' } },
        axisLabel: { color: palette.text3, fontSize: 11 }
      },
      series: [
        {
          name: '总车位',
          type: 'bar',
          barWidth: 12,
          data: occupancy.value.totalSpaces || [],
          itemStyle: { color: 'rgba(76,111,168,0.25)', borderRadius: [6, 6, 0, 0] }
        },
        {
          name: '已占用',
          type: 'line',
          smooth: true,
          symbol: 'circle',
          symbolSize: 6,
          data: occupancy.value.occupiedSpaces || [],
          lineStyle: { width: 2.5, color: palette.teal },
          itemStyle: { color: palette.teal },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(46,158,124,0.22)' },
              { offset: 1, color: 'rgba(46,158,124,0)' }
            ])
          }
        }
      ]
    })
  } catch (e) {
    occupancy.value = {}
    if (occupancyChart) {
      occupancyChart.setOption({
        xAxis: { data: [] },
        series: [{ data: [] }, { data: [] }],
        graphic: emptyGraphic(true, '加载失败，请稍后重试')
      })
    }
  }
}

const loadPeak = async () => {
  if (!peakDate.value) return
  try {
    // GET /api/stats/peak?date=yyyy-MM-dd → [{ hour, count }]
    const res = await getPeakHours(peakDate.value)
    peak.value = Array.isArray(res.data) ? res.data : []
    if (!peakRef.value) return
    const hours = peak.value.map(i => String(i.hour).padStart(2, '0') + ':00')
    const counts = peak.value.map(i => Number(i.count || 0))
    peakChart = peakChart || echarts.init(peakRef.value)
    peakChart.setOption({
      grid: { left: 8, right: 16, top: 24, bottom: 4, containLabel: true },
      graphic: emptyGraphic(hours.length === 0, '当日暂无入场记录'),
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: hours,
        axisLine: { lineStyle: { color: palette.grid } },
        axisTick: { show: false },
        axisLabel: { color: palette.text3, fontSize: 11 }
      },
      yAxis: {
        type: 'value',
        name: '入场车次',
        nameTextStyle: { color: palette.text3, fontSize: 11 },
        splitLine: { lineStyle: { color: palette.grid, type: 'dashed' } },
        axisLabel: { color: palette.text3, fontSize: 11 }
      },
      series: [
        {
          type: 'bar',
          barWidth: '46%',
          data: counts,
          itemStyle: {
            borderRadius: [6, 6, 0, 0],
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#7BC8B0' },
              { offset: 1, color: '#2E9E7C' }
            ])
          }
        }
      ]
    })
  } catch (e) {
    peak.value = []
  }
}

/* ---------------- 报表导出（CSV + BOM） ---------------- */
const csvEscape = (val) => {
  const s = String(val ?? '')
  return /[",\r\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
}

const downloadCsv = (content, filename) => {
  const blob = new Blob(['\ufeff' + content], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(link.href)
}

const handleExport = async () => {
  if (!dateRange.value) {
    ElMessage.warning('请先选择日期范围')
    return
  }
  exporting.value = true
  try {
    const res = await exportStats(dateRange.value[0], dateRange.value[1])
    const inc = res.data?.income || {}
    const occ = res.data?.occupancy || {}

    const rows = [
      ['智慧星河 · 小区智慧停车管理系统 数据报表'],
      ['统计周期', `${dateRange.value[0]} 至 ${dateRange.value[1]}`],
      ['导出时间', new Date().toLocaleString('zh-CN')],
      [],
      ['【收入统计】'],
      ['总收入（元）', inc.totalIncome ?? ''],
      ['日均收入（元）', inc.avgDailyIncome ?? ''],
      ['统计天数', inc.days ?? ''],
      [],
      ['【车位利用率】'],
      ['平均利用率', occ.avgOccupancy != null ? (Number(occ.avgOccupancy) * 100).toFixed(2) + '%' : ''],
      ['日期', '总车位数', '已占用车位数', '利用率']
    ]
    const dates = occ.dates || []
    for (let i = 0; i < dates.length; i++) {
      const total = occ.totalSpaces?.[i] ?? ''
      const occupied = occ.occupiedSpaces?.[i] ?? ''
      const rate = total ? ((occupied / total) * 100).toFixed(2) + '%' : ''
      rows.push([dates[i], total, occupied, rate])
    }
    rows.push([])
    rows.push(['【高峰时段】', peakDate.value])
    rows.push(['时段', '入场车次'])
    peak.value.forEach(p => rows.push([String(p.hour).padStart(2, '0') + ':00', p.count]))

    const csv = rows.map(r => r.map(csvEscape).join(',')).join('\r\n')
    downloadCsv(csv, `停车数据报表_${dateRange.value[0]}_${dateRange.value[1]}.csv`)
    ElMessage.success('报表已导出')
  } catch (e) {
    toastOnce(e, '导出失败')
  } finally {
    exporting.value = false
  }
}

const features = [
  { icon: 'Money', title: '收入口径统一', desc: '以支付成功流水为准，退款自动冲减' },
  { icon: 'PieChart', title: '利用率按日落库', desc: 'Redis 实时计数 + 每日快照' },
  { icon: 'Clock', title: '高峰时段洞察', desc: '按小时聚合入场车流' },
  { icon: 'Share', title: '共享分润可追溯', desc: '共享车位收益按比例分账' }
]
</script>

<style scoped>
.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sp-4);
}

.chart {
  height: 300px;
}

.chart--peak {
  height: 280px;
}

.peak-tools {
  display: flex;
  align-items: center;
  gap: 12px;
}

@media (max-width: 1100px) {
  .grid-2 {
    grid-template-columns: 1fr;
  }
}
</style>
