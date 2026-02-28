import service from './request'

/**
 * 获取科室列表
 * @param {Object} params { keyword }
 */
export function getDepartmentList(params) {
  return service({
    url: '/api/doctor/department/list',
    method: 'get',
    params
  })
}

/**
 * 获取科室详情
 * @param {Long} id 
 */
export function getDepartmentById(id) {
  return service({
    url: `/api/doctor/department/${id}`,
    method: 'get'
  })
}

/**
 * 创建科室
 * @param {Object} data 
 */
export function createDepartment(data) {
  return service({
    url: '/api/doctor/department',
    method: 'post',
    data
  })
}

/**
 * 更新科室
 * @param {Long} id 
 * @param {Object} data 
 */
export function updateDepartment(id, data) {
  return service({
    url: `/api/doctor/department/${id}`,
    method: 'put',
    data
  })
}

/**
 * 启用/禁用科室
 * @param {Long} id 
 */
export function toggleDepartmentStatus(id) {
  return service({
    url: `/api/doctor/department/${id}/toggle-status`,
    method: 'put'
  })
}

/**
 * 删除科室
 * @param {Long} id 
 */
export function deleteDepartment(id) {
  return service({
    url: `/api/doctor/department/${id}`,
    method: 'delete'
  })
}
