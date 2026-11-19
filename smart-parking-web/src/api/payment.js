import request from '@/utils/request'

export function getPaymentList(params) {
  return request({
    url: '/payment/list',
    method: 'get',
    params
  })
}

export function getPaymentByOrder(orderId) {
  return request({
    url: '/payment/bill',
    method: 'get',
    params: { orderId }
  })
}

// 创建支付订单（生成待支付流水）
export function createPayment(data) {
  return request({
    url: '/payment/create',
    method: 'post',
    params: data
  })
}

// 微信支付（模拟）
export function wechatPay(payNo) {
  return request({
    url: '/payment/wechat/pay',
    method: 'post',
    params: { payNo }
  })
}

// 支付宝支付（模拟；沙箱开启后该端点会被后端拒绝）
export function alipayPay(payNo) {
  return request({
    url: '/payment/alipay/pay',
    method: 'post',
    params: { payNo }
  })
}

// ---- 支付渠道（mock / alipay-sandbox）----
// 前端据此决定：点一下即成功的模拟支付，还是跳沙箱收银台 + 轮询查询
export function getPayChannels() {
  return request({
    url: '/payment/channels',
    method: 'get'
  })
}

// 支付宝收银台下单：返回自动提交表单 HTML，写入新窗口即跳转沙箱收银台
export function alipayCashier(payNo, scene = 'pc', returnUrl) {
  return request({
    url: '/payment/alipay/cashier',
    method: 'post',
    params: { payNo, scene, returnUrl }
  })
}

// 主动查询支付宝交易并结算（无公网回调时的闭环手段）
export function alipayQuery(payNo) {
  return request({
    url: '/payment/alipay/query',
    method: 'post',
    params: { payNo }
  })
}

// ---- 电子发票（管理端）----
export function getInvoiceList(params) {
  return request({
    url: '/payment/invoice/admin/list',
    method: 'get',
    params
  })
}

export function issueInvoice(id) {
  return request({
    url: `/payment/invoice/admin/${id}/issue`,
    method: 'post'
  })
}

export function redFlushInvoice(id, reason) {
  return request({
    url: `/payment/invoice/admin/${id}/red-flush`,
    method: 'post',
    data: { reason }
  })
}
