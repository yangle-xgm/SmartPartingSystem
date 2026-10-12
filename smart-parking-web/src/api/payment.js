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

// 支付宝支付（模拟）
export function alipayPay(payNo) {
  return request({
    url: '/payment/alipay/pay',
    method: 'post',
    params: { payNo }
  })
}
