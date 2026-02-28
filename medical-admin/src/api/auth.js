import service from './request'

/**
 * 登录
 * @param {Object} data { username, password }
 */
export function login(data) {
  return service({
    url: '/api/user/auth/login',
    method: 'post',
    data
  })
}

/**
 * 注册
 * @param {Object} data { username, password, nickname, phone }
 */
export function register(data) {
  return service({
    url: '/api/user/auth/register',
    method: 'post',
    data
  })
}

/**
 * 微信登录
 * @param {Object} data { code }
 */
export function wxLogin(data) {
  return service({
    url: '/api/user/auth/wx-login',
    method: 'post',
    data
  })
}

/**
 * 退出登录
 */
export function logout() {
  return service({
    url: '/api/user/auth/logout',
    method: 'post'
  })
}

/**
 * 获取当前登录用户信息
 */
export function getUserInfo() {
  return service({
    url: '/api/user/user/info',
    method: 'get'
  })
}
