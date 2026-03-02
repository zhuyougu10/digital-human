import request from './request'

export const wxLogin = () => {
  return new Promise((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: async (res) => {
        if (res.code) {
          try {
            const result = await request({
              url: '/user/auth/wx-login',
              method: 'POST',
              data: { code: res.code }
            })
            if (result.token) {
              uni.setStorageSync('token', result.token)
              uni.setStorageSync('userInfo', result.userInfo || {})
            }
            resolve(result)
          } catch (error) {
            reject(error)
          }
        } else {
          reject(new Error('WeChat login failed'))
        }
      },
      fail: (err) => {
        reject(err)
      }
    })
  })
}

export const getUserInfo = () => {
  return request({
    url: '/user/user/info',
    method: 'GET'
  })
}

export const logout = () => {
  uni.removeStorageSync('token')
  uni.removeStorageSync('userInfo')
  uni.reLaunch({
    url: '/pages/index/index'
  })
}
