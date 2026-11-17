import request from '@/utils/request'

// 通知列表（分页）
export function getNotifyList(params) {
  return request({
    url: '/notify/list',
    method: 'get',
    params
  })
}

// 标记单条已读
export function markNotifyRead(id) {
  return request({
    url: `/notify/read/${id}`,
    method: 'put'
  })
}

// 全部已读
export function markAllNotifyRead(userId) {
  return request({
    url: '/notify/readAll',
    method: 'put',
    params: { userId }
  })
}

// 未读数量
export function getUnreadCount(userId) {
  return request({
    url: '/notify/unread/count',
    method: 'get',
    params: { userId }
  })
}

// 管理员群发通知（POST body：{title, content, type, target(0全体/1指定), userId}）
// 注意：这里不是 /notify/send（那是「给单个用户发一条」），群发走 /notify/admin/send，
// 网关对 /api/notify/admin/** 做了管理端角色校验。
export function sendAdminNotify(data) {
  return request({
    url: '/notify/admin/send',
    method: 'post',
    data
  })
}
