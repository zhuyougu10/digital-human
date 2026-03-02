import service from './request'

/**
 * 管理员 - 分页查询预约列表
 * @param {Object} params queryDTO + pageQuery
 */
export function getAppointmentList(params) {
  return service({
    url: '/api/appointment/appointment/list',
    method: 'get',
    params
  })
}

/**
 * 获取预约详情
 * @param {Long} id 
 */
export function getAppointmentById(id) {
  return service({
    url: `/api/appointment/appointment/${id}`,
    method: 'get'
  })
}

/**
 * 创建预约
 * @param {Object} data 
 */
export function createAppointment(data) {
  return service({
    url: '/api/appointment/appointment',
    method: 'post',
    data
  })
}

/**
 * 取消预约
 * @param {Long} id 
 */
export function cancelAppointment(id) {
  return service({
    url: `/api/appointment/appointment/${id}/cancel`,
    method: 'put'
  })
}

/**
 * 获取预约统计数据
 * @param {Object} params { startDate, endDate }
 */
export function getAppointmentStats(params) {
  return service({
    url: '/api/appointment/appointment/statistics',
    method: 'get',
    params
  })
}

/**
 * 获取预约统计数据 (管理员)
 */
export function getStatistics() {
  return service({
    url: '/api/appointment/appointment/statistics',
    method: 'get'
  })
}

/**
 * 获取医生今日预约列表
 */
export function getDoctorTodayAppointments() {
  return service({
    url: '/api/appointment/appointment/doctor',
    method: 'get',
    params: {
      date: new Date().toISOString().slice(0, 10)
    }
  })
}

/**
 * 获取医生预约列表
 * @param {Object} params queryDTO + pageQuery
 */
export function getDoctorAppointments(params) {
  return service({
    url: '/api/appointment/appointment/doctor',
    method: 'get',
    params
  })
}

/**
 * 获取我的预约列表
 * @param {Object} params pageQuery
 */
export function getMyAppointments(params) {
  return service({
    url: '/api/appointment/appointment/my',
    method: 'get',
    params
  })
}
