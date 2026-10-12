import request from '@/utils/request'

// 我的订单（按我的车辆ID过滤）
export function getOrderList(params) {
  return request({
    url: '/order/list',
    method: 'get',
    params
  })
}

// 取消订单
export function cancelOrder(orderNo) {
  return request({
    url: `/order/cancel/${orderNo}`,
    method: 'post'
  })
}

// 创建支付记录
export function createPayment(orderId, userId, amount, payType) {
  return request({
    url: '/payment/create',
    method: 'post',
    params: { orderId, userId, amount, payType }
  })
}

// 模拟支付（微信）
export function wechatPay(payNo) {
  return request({
    url: '/payment/wechat/pay',
    method: 'post',
    params: { payNo }
  })
}

// 模拟支付（支付宝）
export function alipayPay(payNo) {
  return request({
    url: '/payment/alipay/pay',
    method: 'post',
    params: { payNo }
  })
}

// 访客邀请：生成
export function createInvite(userId, plateNo, expireHours) {
  return request({
    url: '/order/invite/create',
    method: 'post',
    params: { userId, plateNo, expireHours }
  })
}

// 访客邀请：我的列表
export function getInviteList(params) {
  return request({
    url: '/order/invite/list',
    method: 'get',
    params
  })
}

// 访客邀请：作废
export function invalidateInvite(id, userId) {
  return request({
    url: '/order/invite/invalidate',
    method: 'post',
    params: { id, userId }
  })
}
