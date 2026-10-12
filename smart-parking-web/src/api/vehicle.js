import request from '@/utils/request'

export function getVehicleList(userId) {
  // userId 为空 → 管理端查看全部车辆；非空 → 业主查看我的车辆
  const params = {}
  if (userId) params.userId = userId
  return request({
    url: '/vehicle/list',
    method: 'get',
    params
  })
}

export function bindVehicle(data) {
  return request({
    url: '/vehicle/bind',
    method: 'post',
    data
  })
}

export function unbindVehicle(id, userId) {
  return request({
    url: `/vehicle/unbind/${id}`,
    method: 'delete',
    params: { userId }
  })
}

// 车牌识别（模拟，含模糊识别兜底）
export function recognizePlate(plateNo, confidence = 100) {
  return request({
    url: '/vehicle/recognize',
    method: 'post',
    params: { plateNo, confidence }
  })
}

// 加入/移出黑名单
export function toggleBlacklist(id, status) {
  return request({
    url: `/vehicle/${id}/blacklist`,
    method: 'put',
    params: { status }
  })
}

// 月卡续费
export function renewMonthly(id) {
  return request({
    url: `/vehicle/${id}/renew-monthly`,
    method: 'post'
  })
}
