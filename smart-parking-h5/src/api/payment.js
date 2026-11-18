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
 * 我的车位共享收益（汇总 + 明细）。
 * 返回 { total, grossTotal, count, records: [{ spaceId, ownerAmount, totalAmount, ... }] }
 */
export function getMyShareIncome() {
  return request({
    url: '/payment/share-income/my',
    method: 'get'
  })
}
