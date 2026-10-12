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

// 发送通知（管理员）
export function sendNotify(data) {
  return request({
    url: '/notify/send',
    method: 'post',
    params: data
  })
}
