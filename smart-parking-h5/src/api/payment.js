import request from '@/utils/request'

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
