import request from '@/utils/request'

/**
 * 我的车辆列表
 * 后端 GET /api/vehicle/list?userId= 直接返回 List<Vehicle>（不是分页 PageResult），
 * 因此这里只接受 userId 一个参数，调用方按数组处理 res.data。
 */
export function getVehicleList(userId) {
  return request({
    url: '/vehicle/list',
    method: 'get',
    params: { userId }
  })
}

// 绑定车辆
export function bindVehicle(data) {
  return request({
    url: '/vehicle/bind',
    method: 'post',
    data
  })
}

// 解绑车辆
export function unbindVehicle(id, userId) {
  return request({
    url: `/vehicle/unbind/${id}`,
    method: 'delete',
    params: { userId }
  })
}
