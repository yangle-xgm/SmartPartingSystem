import request from '@/utils/request'

// 我的订单（按我的车辆ID过滤）
export function getOrderList(params) {
  return request({
    url: '/order/list',
    method: 'get',
    params
  })
}

// 订单详情（后端按 role=0 校验只能看本人车辆订单）
export function getOrderDetail(orderNo) {
  return request({
    url: `/order/${orderNo}`,
    method: 'get'
  })
}

// 计费规则列表（用于订单详情展示计费口径）
export function getBillingRules() {
  return request({
    url: '/order/billing-rules',
    method: 'get'
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

// 模拟支付（支付宝；沙箱开启后该端点会被后端拒绝）
export function alipayPay(payNo) {
  return request({
    url: '/payment/alipay/pay',
    method: 'post',
    params: { payNo }
  })
}

// 支付渠道模式（mock / alipay-sandbox）
export function getPayChannels() {
  return request({
    url: '/payment/channels',
    method: 'get'
  })
}

// 支付宝沙箱收银台下单（手机网站支付），返回自动提交表单
// returnUrl：付款后支付宝跳回的地址。不传则用后端配置的固定值（本机 localhost），
// 手机上会跳到手机自己的 localhost 导致「支付页异常退出」，所以前端必须传当前 origin。
export function alipayCashier(payNo, scene = 'h5', returnUrl) {
  return request({
    url: '/payment/alipay/cashier',
    method: 'post',
    params: { payNo, scene, returnUrl }
  })
}

// 主动查询支付宝交易并结算
export function alipayQuery(payNo) {
  return request({
    url: '/payment/alipay/query',
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
