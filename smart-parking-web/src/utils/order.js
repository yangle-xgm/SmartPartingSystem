/**
 * 订单状态口径统一（前端功能测试 DEF-06）
 *
 * 首页「最近停车记录」与「我的订单」原来各写一套判断：首页只看 status===0 与 payStatus===1，
 * 于是 status=2（已取消）和 0 元单都被标成「待缴费」，与列表页自相矛盾。
 * 这里收敛成一份口径，所有页面复用。
 */

/** 支付状态（与后端 Constants 对齐） */
export const PAY_STATUS_TEXT = {
  0: '待支付',
  1: '支付成功',
  2: '已退款',
  3: '已关闭',
  4: '支付失败'
}

export const ORDER_STATUS_TEXT = {
  0: '进行中',
  1: '已完成',
  2: '已取消'
}

/**
 * 0 元单：已完成、未产生支付、金额为 0（免费时长内出场）
 */
export function isFreeOrder(o) {
  return !!o && o.status === 1 && o.payStatus === 0 && Number(o.amount || 0) <= 0
}

/**
 * 是否待缴费：已完成、未支付、金额大于 0（0 元单不算待支付）
 */
export function isPendingPay(o) {
  return !!o && o.status === 1 && o.payStatus === 0 && Number(o.amount || 0) > 0
}

/**
 * 列表/首页统一的订单状态标签
 * @returns {{ text: string, tone: 'success'|'warning'|'danger'|'info'|'primary' }}
 */
export function orderStatusView(o) {
  if (!o) return { text: '—', tone: 'info' }
  if (o.status === 2) return { text: '已取消', tone: 'info' }
  if (o.status === 0) return { text: '停车中', tone: 'warning' }
  if (isFreeOrder(o)) return { text: '无需支付', tone: 'success' }
  switch (o.payStatus) {
    case 1:
      return { text: '已支付', tone: 'success' }
    case 2:
      return { text: '已退款', tone: 'info' }
    case 3:
      return { text: '已关闭', tone: 'info' }
    case 4:
      return { text: '支付失败', tone: 'danger' }
    case 0:
      return { text: '待缴费', tone: 'danger' }
    default:
      return { text: ORDER_STATUS_TEXT[o.status] || '—', tone: 'info' }
  }
}
