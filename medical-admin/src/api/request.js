import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'

// Create axios instance
const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 10000
})

// Request interceptor
service.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    console.error('Request error:', error)
    return Promise.reject(error)
  }
)

// Response interceptor
service.interceptors.response.use(
  (response) => {
    const res = response.data
    // Custom code logic, 200 is success
    if (res.code !== 200) {
      ElMessage.error(res.message || 'Error')
      
      // 401: Illegal token; 
      if (res.code === 401) {
        // to re-login
        localStorage.removeItem('token')
        window.location.href = '/login'
      }
      return Promise.reject(new Error(res.message || 'Error'))
    } else {
      return res
    }
  },
  (error) => {
    console.error('Response error:', error)
    ElMessage.error(error.message || 'Network Error')
    return Promise.reject(error)
  }
)

export default service
