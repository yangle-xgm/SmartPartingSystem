import request from '@/utils/request'

/**
 * 我的支付记录（分页）。
 * 后端按 X-User-Role=0 强制只查本人，前端无需传 userId。
 * @param {{current?:number,size?:number,payStatus?:number}} params
 */
export function getPaymentList(params) {
  return request({
    url: '/payment/list',
    method: 'get',
    params
  })
}

/**
 * 创建月卡支付流水。
 *
 * 月卡与停车订单共用 payment_record（bizType=2），这样收入统计、发票、对账只有一个记账口径。
 * 金额与归属以车辆服务的月卡订单为准，前端不传金额。
 * @param {number|string} monthlyOrderId 月卡订单ID（不是 orderNo）
 * @param {1|2|3} payType 1微信 2支付宝 3现金
 */
export function createMonthlyPayment(monthlyOrderId, payType) {
  return request({
    url: '/payment/monthly/create',
    method: 'post',
    params: { monthlyOrderId, payType }
  })
}

/**
 * 我的车位共享收益（汇总 + 明细）。
 * 返回 { total, grossTotal, count, records: [{ spaceId, ownerAmount, totalAmount, ... }] }
 */
export function getMyShareIncome() {
  return request({
    url: '/payment/share-income/my',
    method: 'get'
  })
}

/**
 * 申请电子发票（仅限本人已支付订单，一单一票）。
 * @param {{orderId:number,titleType:1|2,title:string,taxNo?:string,email?:string}} data
 */
export function applyInvoice(data) {
  return request({
    url: '/payment/invoice/apply',
    method: 'post',
    data
  })
}

/** 我的发票列表 */
export function getMyInvoices() {
  return request({
    url: '/payment/invoice/my',
    method: 'get',
    silent: true
  })
}
