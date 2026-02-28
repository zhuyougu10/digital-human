import service from './request'

/**
 * 分页查询医生列表
 * @param {Object} params { departmentId, pageNum, pageSize }
 */
export function getDoctorList(params) {
  return service({
    url: '/api/doctor/doctor/list',
    method: 'get',
    params
  })
}

/**
 * 获取医生详情
 * @param {Long} id 
 */
export function getDoctorById(id) {
  return service({
    url: `/api/doctor/doctor/${id}`,
    method: 'get'
  })
}

/**
 * 创建医生信息
 * @param {Object} data 
 */
export function createDoctor(data) {
  return service({
    url: '/api/doctor/doctor',
    method: 'post',
    data
  })
}

/**
 * 更新医生信息
 * @param {Long} id 
 * @param {Object} data 
 */
export function updateDoctor(id, data) {
  return service({
    url: `/api/doctor/doctor/${id}`,
    method: 'put',
    data
  })
}

/**
 * 获取医生排班模板
 * @param {Long} doctorId 
 */
export function getScheduleTemplates(doctorId) {
  return service({
    url: `/api/doctor/schedule/template/${doctorId}`,
    method: 'get'
  })
}

/**
 * 保存排班模板
 * @param {Long} doctorId 
 * @param {Object} data 
 */
export function createScheduleTemplate(doctorId, data) {
  return service({
    url: `/api/doctor/schedule/template/${doctorId}`,
    method: 'post',
    data
  })
}

/**
 * 生成排班号源
 * @param {Object} params { startDate, endDate }
 */
export function generateSlots(params) {
  return service({
    url: '/api/doctor/schedule/generate',
    method: 'post',
    params
  })
}

/**
 * 获取可用号源列表
 * @param {Object} params { doctorId, date }
 */
export function getAvailableSlots(params) {
  return service({
    url: '/api/doctor/schedule/slots',
    method: 'get',
    params
  })
}
