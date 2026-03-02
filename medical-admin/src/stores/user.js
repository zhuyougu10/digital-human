import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, getUserInfo, logout as logoutApi } from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || '{}'))

  const isLogin = computed(() => !!token.value)
  const roles = computed(() => userInfo.value.roles || [])
  const isAdmin = computed(() => roles.value.includes('ADMIN'))
  const isDoctor = computed(() => roles.value.includes('DOCTOR'))

  function setToken(newToken) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  function setUserInfo(info) {
    userInfo.value = info
    localStorage.setItem('userInfo', JSON.stringify(info))
  }

  async function login(username, password) {
    const res = await loginApi({ username, password })
    setToken(res.data.token)
    setUserInfo(res.data.user)
  }

  async function fetchUserInfo() {
    const res = await getUserInfo()
    setUserInfo(res.data)
  }

  async function logout() {
    try {
      await logoutApi()
    } catch (error) {
      // Ignore logout request errors and continue local cleanup
    }
    token.value = ''
    userInfo.value = {}
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    window.location.href = '/login'
  }

  return {
    token,
    userInfo,
    isLogin,
    roles,
    isAdmin,
    isDoctor,
    setToken,
    setUserInfo,
    login,
    fetchUserInfo,
    logout
  }
})
