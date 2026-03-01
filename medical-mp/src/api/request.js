const BASE_URL = import.meta.env.VITE_API_BASE || 'http://localhost:9090/api'

const request = (options) => {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync('token')
    
    uni.request({
      url: BASE_URL + options.url,
      method: options.method || 'GET',
      data: options.data || {},
      header: {
        'Authorization': token ? `Bearer ${token}` : '',
        ...options.header
      },
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data)
        } else if (res.statusCode === 401) {
          uni.removeStorageSync('token')
          uni.removeStorageSync('userInfo')
          uni.showToast({
            title: '登录过期，请重新登录',
            icon: 'none'
          })
          setTimeout(() => {
            uni.reLaunch({
              url: '/pages/index/index'
            })
          }, 1500)
          reject(res)
        } else {
          const msg = res.data?.message || '请求失败'
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
