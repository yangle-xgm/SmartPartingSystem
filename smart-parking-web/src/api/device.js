import request from '@/utils/request'

export function getDeviceList(params) {
  return request({
    url: '/device/list',
    method: 'get',
    params
  })
}

export function getDeviceDetail(id) {
  return request({
    url: `/device/${id}`,
    method: 'get'
  })
}

export function registerDevice(data) {
  return request({
    url: '/device',
    method: 'post',
    data
  })
}

export function updateDevice(id, data) {
  return request({
    url: `/device/${id}`,
    method: 'put',
    data
  })
}

export function deleteDevice(id) {
  return request({
    url: `/device/${id}`,
    method: 'delete'
  })
}

export function openGate(deviceNo) {
  return request({
    url: '/device/gate/open',
    method: 'post',
    params: { deviceNo }
  })
}

export function closeGate(deviceNo) {
  return request({
    url: '/device/gate/close',
    method: 'post',
    params: { deviceNo }
  })
}

export function getDeviceOverview() {
  return request({
    url: '/device/status/overview',
    method: 'get'
  })
}

// 模拟摄像头识别（发布MQTT，触发自动入场+道闸联动）
export function mockCamera(deviceNo, plateNo, confidence) {
  return request({
    url: '/device/mock/camera',
    method: 'post',
    params: { deviceNo, plateNo, confidence }
  })
}
