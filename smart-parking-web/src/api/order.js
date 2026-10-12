import request from '@/utils/request'

export function getOrderList(params) {
  return request({
    url: '/order/list',
    method: 'get',
    params
  })
}

export function getOrderDetail(orderNo) {
  return request({
    url: `/order/${orderNo}`,
    method: 'get'
  })
}

export function cancelOrder(orderNo) {
  return request({
    url: `/order/cancel/${orderNo}`,
    method: 'post'
  })
}

export function calculateAmount(orderNo) {
  return request({
    url: '/order/calculate',
    method: 'get',
    params: { orderNo }
  })
}

// 车辆入场，创建订单
export function createEntryOrder(data) {
  return request({
    url: '/order/entry',
    method: 'post',
    params: data
  })
}

// 车辆出场，完成订单
export function exitOrder(orderNo) {
  return request({
    url: `/order/exit/${orderNo}`,
    method: 'post'
  })
}

// 业主生成访客邀请码
export function createInvite(userId, plateNo, expireHours = 24) {
  return request({
    url: '/order/invite/create',
    method: 'post',
    params: { userId, plateNo, expireHours }
  })
}

// 我的访客邀请列表
export function getInviteList(params) {
  return request({
    url: '/order/invite/list',
    method: 'get',
    params
  })
}

// 访客凭码入场
export function inviteEntry(inviteCode) {
  return request({
    url: '/order/invite/entry',
    method: 'post',
    params: { inviteCode }
  })
}

// 作废邀请码
export function invalidateInvite(id, userId) {
  return request({
    url: '/order/invite/invalidate',
    method: 'post',
    params: { id, userId }
  })
}
