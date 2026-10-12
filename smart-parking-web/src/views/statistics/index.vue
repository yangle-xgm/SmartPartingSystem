<template>
  <div class="statistics-page">
    <el-row :gutter="20">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>收入统计</span>
              <div>
                <el-date-picker
                  v-model="dateRange"
                  type="daterange"
                  range-separator="至"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  value-format="YYYY-MM-DD"
                  style="margin-right: 12px;"
                  @change="loadIncomeStats"
                />
                <el-button type="success" :loading="exporting" @click="handleExport">导出报表</el-button>
              </div>
            </div>
          </template>
          <div ref="incomeChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>高峰时段分析</span>
              <el-date-picker
                v-model="peakDate"
                type="date"
                placeholder="选择日期"
                value-format="YYYY-MM-DD"
                @change="loadPeakHours"
              />
            </div>
          </template>
          <div ref="peakChartRef" class="chart-container"></div>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span>车位利用率</span>
          </template>
          <div ref="occupancyChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { getIncomeStats, getPeakHours, getOccupancyStats, exportStats } from '@/api/statistics'
import * as echarts from 'echarts'

const incomeChartRef = ref(null)
const peakChartRef = ref(null)
const occupancyChartRef = ref(null)

const today = new Date().toISOString().split('T')[0]
const monthStart = today.substring(0, 7) + '-01'

const dateRange = ref([monthStart, today])
const peakDate = ref(today)
const exporting = ref(false)

let incomeChart = null
let peakChart = null
let occupancyChart = null

onMounted(async () => {
  await nextTick()
  await loadIncomeStats()
  await loadPeakHours()
  await loadOccupancyStats()

  window.addEventListener('resize', () => {
    incomeChart?.resize()
    peakChart?.resize()
    occupancyChart?.resize()
  })
})

const loadIncomeStats = async () => {
  if (!dateRange.value) return
  try {
    const res = await getIncomeStats(dateRange.value[0], dateRange.value[1])
    const data = res.data

    if (!incomeChart) {
      incomeChart = echarts.init(incomeChartRef.value)
    }

    incomeChart.setOption({
      tooltip: { trigger: 'item' },
      xAxis: { type: 'category', data: ['总收入', '日均收入'] },
      yAxis: { type: 'value' },
      series: [{
        type: 'bar',
        data: [
          { value: data.totalIncome, name: '总收入', itemStyle: { color: '#409eff' } },
          { value: data.avgDailyIncome, name: '日均收入', itemStyle: { color: '#67c23a' } }
        ],
        barWidth: '40%',
        label: { show: true, position: 'top', formatter: '{c}' }
      }]
    })
  } catch (e) {
    console.error('加载收入统计失败', e)
  }
}

const loadPeakHours = async () => {
  if (!peakDate.value) return
  try {
    const res = await getPeakHours(peakDate.value)
    const hours = res.data.map(item => item.hour + ':00')
    const counts = res.data.map(item => item.count)

    if (!peakChart) {
      peakChart = echarts.init(peakChartRef.value)
    }

    peakChart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: hours },
      yAxis: { type: 'value', name: '入场数' },
      series: [{
        type: 'line',
        data: counts,
        smooth: true,
        areaStyle: { opacity: 0.3 },
        itemStyle: { color: '#e6a23c' }
      }]
    })
  } catch (e) {
    console.error('加载高峰时段失败', e)
  }
}

const loadOccupancyStats = async () => {
  try {
    const res = await getOccupancyStats(monthStart, today)
    const data = res.data

    if (!occupancyChart) {
      occupancyChart = echarts.init(occupancyChartRef.value)
    }

    const dates = data.dates?.map(d => d.substring(5)) || []
    const total = data.totalSpaces || []
    const occupied = data.occupiedSpaces || []

    occupancyChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['总车位', '已占用'] },
      xAxis: { type: 'category', data: dates },
      yAxis: { type: 'value' },
      series: [
        { name: '总车位', type: 'bar', data: total, itemStyle: { color: '#909399' } },
        { name: '已占用', type: 'bar', data: occupied, itemStyle: { color: '#409eff' } }
      ]
    })
  } catch (e) {
    console.error('加载车位利用率失败', e)
  }
}

// ===== 报表导出（CSV，UTF-8 BOM 兼容 Excel） =====
const handleExport = async () => {
  if (!dateRange.value) {
    ElMessage.warning('请先选择日期范围')
    return
  }
  exporting.value = true
  try {
    const res = await exportStats(dateRange.value[0], dateRange.value[1])
    const income = res.data.income || {}
    const occupancy = res.data.occupancy || {}

    const rows = []
    rows.push(['小区智慧停车管理系统 - 数据报表'])
    rows.push(['统计周期', `${dateRange.value[0]} 至 ${dateRange.value[1]}`])
    rows.push(['导出时间', new Date().toLocaleString('zh-CN')])
    rows.push([])
    rows.push(['【收入统计】'])
    rows.push(['总收入（元）', income.totalIncome ?? ''])
    rows.push(['日均收入（元）', income.avgDailyIncome ?? ''])
    rows.push(['统计天数', income.days ?? ''])
    rows.push([])
    rows.push(['【车位利用率】'])
    rows.push(['平均利用率', occupancy.avgOccupancy != null ? (occupancy.avgOccupancy * 100).toFixed(2) + '%' : ''])
    rows.push(['日期', '总车位数', '已占用车位数', '利用率'])
    const dates = occupancy.dates || []
    for (let i = 0; i < dates.length; i++) {
      const total = occupancy.totalSpaces?.[i] ?? ''
      const occupied = occupancy.occupiedSpaces?.[i] ?? ''
      const rate = total ? ((occupied / total) * 100).toFixed(2) + '%' : ''
      rows.push([dates[i], total, occupied, rate])
    }

    const csv = rows.map(r => r.map(csvEscape).join(',')).join('\r\n')
    downloadCsv(csv, `停车数据报表_${dateRange.value[0]}_${dateRange.value[1]}.csv`)
    ElMessage.success('报表已导出')
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

// CSV 字段转义（含逗号/引号/换行时加引号）
const csvEscape = (val) => {
  const s = String(val ?? '')
  return /[",\r\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
}

// 下载 CSV（带 UTF-8 BOM，Excel 打开中文不乱码）
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
</script>

<style lang="scss" scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.chart-container {
  height: 350px;
}
</style>
