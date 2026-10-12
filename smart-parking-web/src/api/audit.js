import request from '@/utils/request'

// 分页查询操作审计日志
export function getAuditList(params) {
  return request({
    url: '/audit/list',
    method: 'get',
    params
  })
}
