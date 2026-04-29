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
 * 创建用户
 * @param {Object} data { username, password, nickname, phone, roleKey, ... }
 */
export function createUser(data) {
  return service({
    url: '/api/user/user/add',
    method: 'post',
    data
  })
}

/**
 * 根据ID获取用户信息
 * @param {Long} userId 
 */
export function getUserById(userId) {
  return service({
    url: `/api/user/user/${userId}`,
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

/**
 * 移除角色
 * @param {Long} userId
 * @param {String} roleKey
 */
export function removeRole(userId, roleKey) {
  return service({
    url: `/api/user/user/${userId}/role/${roleKey}`,
    method: 'delete'
  })
}
