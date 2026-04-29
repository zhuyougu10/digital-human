import { resolveApiBase } from '../../shared/runtime-config'

const BASE_URL = resolveApiBase()

export const getStoredToken = () => uni.getStorageSync('token') || ''

export const buildAuthHeader = (header = {}) => {
  const token = getStoredToken()
  return {
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...header
  }
}

const request = (options) => {
  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE_URL + options.url,
      method: options.method || 'GET',
      data: options.data || {},
      header: buildAuthHeader(options.header),
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          const result = res.data
          if (result && typeof result === 'object' && 'code' in result) {
            if (result.code === 200) {
              resolve(result.data)
            } else {
              const msg = result.msg || '请求失败'
              uni.showToast({
                title: msg,
                icon: 'none'
              })
              reject(result)
            }
            return
          }
          resolve(result)
        } else if (res.statusCode === 401) {
          uni.removeStorageSync('token')
          uni.removeStorageSync('userInfo')
          uni.showToast({
            title: '登录过期，请重新登录',
            icon: 'none'
          })
          setTimeout(() => {
            uni.reLaunch({
              url: '/pages/login/login'
            })
          }, 1500)
          reject(res)
        } else {
          const msg = res.data?.msg || '请求失败'
          uni.showToast({
            title: msg,
            icon: 'none'
          })
          reject(res)
        }
      },
      fail: (err) => {
        uni.showToast({
          title: '网络错误，请稍后重试',
          icon: 'none'
        })
        reject(err)
      }
    })
  })
}

export default request
