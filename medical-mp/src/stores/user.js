import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref(uni.getStorageSync('token') || '')
  const userInfo = ref(uni.getStorageSync('userInfo') || {})
  const roles = ref([])

  const setToken = (newToken) => {
    token.value = newToken
    uni.setStorageSync('token', newToken)
  }

  const setUserInfo = (newUserInfo) => {
    userInfo.value = newUserInfo
    uni.setStorageSync('userInfo', newUserInfo)
  }

  const clearAuth = () => {
    token.value = ''
    userInfo.value = {}
    roles.value = []
    uni.removeStorageSync('token')
    uni.removeStorageSync('userInfo')
  }

  return {
    token,
    userInfo,
    roles,
    setToken,
    setUserInfo,
    clearAuth
  }
})
