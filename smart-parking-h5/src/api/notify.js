import request from '@/utils/request'

// 通知列表
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

/**
 * 提交意见反馈。
 * 后端以网关注入的 X-User-Id 为准（role=0 时忽略 body 的 userId），
 * 这里带上 userId 只是为了直连服务调试时可用。
 * @param {{userId:number,type:number,title?:string,content:string}} data
 */
export function submitFeedback(data) {
  return request({
    url: '/notify/feedback',
    method: 'post',
    data
  })
}

// 我的反馈列表（含物业回复）
export function getMyFeedback(params) {
  return request({
    url: '/notify/feedback/my',
    method: 'get',
    params
  })
}
