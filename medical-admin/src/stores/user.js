import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

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
    // Mock login for now, will be replaced with real API call
    console.log('Login attempt:', username)
    // In real implementation:
    // const res = await request.post('/auth/login', { username, password })
    // setToken(res.data.token)
    // setUserInfo(res.data.user)
  }

  function logout() {
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
    logout
  }
})
