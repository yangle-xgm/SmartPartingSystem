import request from '@/utils/request'

/**
 * 我的车辆列表
 * 后端 GET /api/vehicle/list?userId= 直接返回 List<Vehicle>（不是分页 PageResult），
 * 因此这里只接受 userId 一个参数，调用方按数组处理 res.data。
 */
export function getVehicleList(userId) {
  return request({
    url: '/vehicle/list',
    method: 'get',
    params: { userId }
  })
}

// 绑定车辆
export function bindVehicle(data) {
  return request({
    url: '/vehicle/bind',
    method: 'post',
    data
  })
}

// 解绑车辆
export function unbindVehicle(id, userId) {
  return request({
    url: `/vehicle/unbind/${id}`,
    method: 'delete',
    params: { userId }
  })
}

// ==================== 月卡充值 ====================

/**
 * 月卡试算：选月数实时报价。
 * 返回 { unitPrice, amount, expireBefore, expireAfter, isRenewal, plateNo, plateNoMasked }
 */
export function quoteMonthly(vehicleId, months) {
  return request({
    url: '/vehicle/monthly/quote',
    method: 'get',
    params: { vehicleId, months }
  })
}

// 创建月卡订单（待支付），返回 { orderNo, amount, months, expireAfter, ... }
export function createMonthlyOrder(vehicleId, months, payType = 1) {
  return request({
    url: '/vehicle/monthly/create',
    method: 'post',
    params: { vehicleId, months, payType }
  })
}

// 模拟支付：成功后月卡立即生效
export function payMonthly(orderNo) {
  return request({
    url: '/vehicle/monthly/pay',
    method: 'post',
    params: { orderNo }
  })
}

// 我的月卡订单
export function getMonthlyOrders() {
  return request({
    url: '/vehicle/monthly/orders',
    method: 'get'
  })
}
