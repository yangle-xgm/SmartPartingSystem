import request from '@/utils/request'

export function getSpaceList(params) {
  return request({
    url: '/parking/spaces',
    method: 'get',
    params
  })
}

export function getSpaceDetail(id) {
  return request({
    url: `/parking/space/${id}`,
    method: 'get'
  })
}

export function addSpace(data) {
  return request({
    url: '/parking/space',
    method: 'post',
    data
  })
}

export function updateSpace(id, data) {
  return request({
    url: `/parking/space/${id}`,
    method: 'put',
    data
  })
}

export function deleteSpace(id) {
  return request({
    url: `/parking/space/${id}`,
    method: 'delete'
  })
}

export function getAvailableSpaces(area) {
  return request({
    url: '/parking/available',
    method: 'get',
    params: { area }
  })
}

export function getAllSpaces(area) {
  return request({
    url: '/parking/all',
    method: 'get',
    params: { area }
  })
}

export function getSpaceStatistics() {
  return request({
    url: '/parking/status/statistics',
    method: 'get'
  })
}

// 预约车位（业主端）
export function reserveSpace(spaceId, userId, startTime, endTime) {
  return request({
    url: '/parking/reserve',
    method: 'post',
    params: { spaceId, userId, startTime, endTime }
  })
}

// 取消预约
export function cancelReserve(reservationId, userId) {
  return request({
    url: '/parking/reserve/cancel',
    method: 'post',
    params: { reservationId, userId }
  })
}

// 我的预约
export function myReservations(userId) {
  return request({
    url: '/parking/reserve/my',
    method: 'get',
    params: { userId }
  })
}
