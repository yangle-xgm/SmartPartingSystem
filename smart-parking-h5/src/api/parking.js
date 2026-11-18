import request from '@/utils/request'

/**
 * 我的车位（按产权人查询）。
 * 后端 GET /api/parking/space/my 在业主身份下会忽略 ownerId，直接用网关注入的身份。
 */
export function getMySpaces() {
  return request({
    url: '/parking/space/my',
    method: 'get'
  })
}

/**
 * 开启/关闭车位共享。
 * 开启后他人临停该车位产生的费用按 7:3 分润给业主。
 */
export function toggleSpaceShare(id, isShared) {
  return request({
    url: `/parking/space/${id}/share`,
    method: 'put',
    params: { isShared }
  })
}

// 可预约车位（area 为空则全部区域）
export function getAvailableSpaces(area) {
  return request({
    url: '/parking/available',
    method: 'get',
    params: { area }
  })
}

// 预约车位（userId 由网关按登录身份覆盖，前端无需传）
export function reserveSpace(spaceId, startTime, endTime) {
  return request({
    url: '/parking/reserve',
    method: 'post',
    params: { spaceId, startTime, endTime }
  })
}

// 取消预约
export function cancelReserve(reservationId) {
  return request({
    url: '/parking/reserve/cancel',
    method: 'post',
    params: { reservationId }
  })
}

// 我的预约
export function getMyReservations() {
  return request({
    url: '/parking/reserve/my',
    method: 'get'
  })
}
