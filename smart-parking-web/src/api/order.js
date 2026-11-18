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

// 计费规则（只读）：出场结算时按车辆类型/车牌颜色匹配的那套规则
// silent：规则表属于辅助信息，接口不可用时页面显示空态即可，不弹全局错误提示
export function getBillingRules() {
  return request({
    url: '/order/billing-rules',
    method: 'get',
    silent: true
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
