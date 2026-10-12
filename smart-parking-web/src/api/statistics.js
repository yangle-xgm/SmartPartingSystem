import request from '@/utils/request'

export function getDashboardData() {
  return request({
    url: '/stats/dashboard',
    method: 'get'
  })
}

export function getOccupancyStats(startDate, endDate) {
  return request({
    url: '/stats/occupancy',
    method: 'get',
    params: { startDate, endDate }
  })
}

export function getIncomeStats(startDate, endDate) {
  return request({
    url: '/stats/income',
    method: 'get',
    params: { startDate, endDate }
  })
}

export function getPeakHours(date) {
  return request({
    url: '/stats/peak',
    method: 'get',
    params: { date }
  })
}

// 报表导出（返回 income + occupancy 汇总数据）
export function exportStats(startDate, endDate) {
  return request({
    url: '/stats/export',
    method: 'get',
    params: { startDate, endDate }
  })
}
