import request from '@/utils/request'

// 我的车辆列表
export function getVehicleList(params) {
  return request({
    url: '/vehicle/list',
    method: 'get',
    params
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
