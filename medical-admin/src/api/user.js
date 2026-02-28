import service from './request'

/**
 * 分页查询用户列表
 * @param {Object} params { pageNum, pageSize, keyword }
 */
export function getUserList(params) {
  return service({
    url: '/api/user/user/list',
    method: 'get',
    params
  })
}

/**
 * 根据ID获取用户信息 (内部调用/详情)
 * @param {Long} userId 
 */
export function getUserById(userId) {
  return service({
    url: `/api/user/user/inner/${userId}`,
    method: 'get'
  })
}

/**
 * 更新当前用户信息
 * @param {Object} data 
 */
export function updateUser(data) {
  return service({
    url: '/api/user/user/info',
    method: 'put',
    data
  })
}

/**
 * 禁用/启用用户
 * @param {Long} userId 
 */
export function toggleUserStatus(userId) {
  return service({
    url: `/api/user/user/${userId}/toggle-status`,
    method: 'put'
  })
}

/**
 * 分配角色
 * @param {Long} userId 
 * @param {String} roleKey 
 */
export function assignRole(userId, roleKey) {
  return service({
    url: `/api/user/user/${userId}/role/${roleKey}`,
    method: 'post'
  })
}
