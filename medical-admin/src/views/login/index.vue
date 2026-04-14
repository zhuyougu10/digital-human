<template>
  <div class="login-container">
    <div class="login-content">
      <div class="brand-header">
        <div class="logo-icon">
          <svg viewBox="0 0 1024 1024" width="48" height="48">
            <path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64z m192 472c0 4.4-3.6 8-8 8H548v148c0 4.4-3.6 8-8 8h-56c-4.4 0-8-3.6-8-8V544H328c-4.4 0-8-3.6-8-8v-56c0-4.4 3.6-8 8-8h148V324c0-4.4 3.6-8 8-8h56c4.4 0 8 3.6 8 8v148h148c4.4 0 8 3.6 8 8v56z" fill="#1677FF"></path>
          </svg>
        </div>
        <h1 class="app-title">医疗AI后台系统</h1>
        <p class="app-subtitle">智能 · 专业 · 高效的医疗辅助平台</p>
      </div>

      <el-card class="login-card" shadow="always">
        <h2 class="form-title">账号登录</h2>
        <el-form :model="loginForm" class="login-form" size="large">
          <el-form-item>
            <el-input 
              v-model="loginForm.username" 
              placeholder="请输入用户名" 
              prefix-icon="User"
              class="custom-input"
            />
          </el-form-item>
          <el-form-item>
            <el-input 
              v-model="loginForm.password" 
              type="password" 
              placeholder="请输入密码" 
              prefix-icon="Lock" 
              show-password 
              class="custom-input"
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" class="login-button" @click="handleLogin" :loading="loading">
              登 录
            </el-button>
          </el-form-item>
        </el-form>
        
      </el-card>

      <div class="footer">
        © 2026 AI Medical Assistant System
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import { login, getUserInfo } from '@/api/auth'
import { User, Lock } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

const handleLogin = async () => {
  if (!loginForm.username || !loginForm.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  
  loading.value = true
  try {
    const res = await login({
      username: loginForm.username,
      password: loginForm.password
    })
    userStore.setToken(res.data.token)
    if (res.data.user) {
      userStore.setUserInfo(res.data.user)
    } else {
      const userRes = await getUserInfo()
      userStore.setUserInfo(userRes.data)
    }
    ElMessage.success('登录成功')
    router.push('/')
  } catch (error) {
    // Error is handled by request interceptor usually, but safe fallback
    console.error(error)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: var(--bg-page);
  background-image: radial-gradient(circle at 50% 30%, #E6F4FF 0%, #F7F8FA 60%);
  position: relative;
  overflow: hidden;
}

.login-container::before {
  content: '';
  position: absolute;
  top: -10%;
  right: -5%;
  width: 50%;
  height: 50%;
  background: radial-gradient(circle, rgba(22, 119, 255, 0.05) 0%, rgba(255, 255, 255, 0) 70%);
  border-radius: 50%;
  pointer-events: none;
}

.login-container::after {
  content: '';
  position: absolute;
  bottom: -10%;
  left: -5%;
  width: 40%;
  height: 40%;
  background: radial-gradient(circle, rgba(82, 196, 26, 0.05) 0%, rgba(255, 255, 255, 0) 70%);
  border-radius: 50%;
  pointer-events: none;
}

.login-content {
  width: 100%;
  max-width: 420px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  z-index: 1;
}

.brand-header {
  text-align: center;
  margin-bottom: 40px;
}

.logo-icon {
  margin-bottom: 16px;
  animation: float 6s ease-in-out infinite;
}

.app-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 8px;
  letter-spacing: 1px;
}

.app-subtitle {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0;
  font-weight: 400;
}

.login-card {
  width: 100%;
  border-radius: var(--radius-lg);
  border: 1px solid rgba(255, 255, 255, 0.6);
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
}

.form-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 24px;
  text-align: center;
}

.login-form {
  padding: 0 10px;
}

.custom-input :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--border-color) inset;
  background-color: #F9FAFB;
  padding: 8px 12px;
}

.custom-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--primary-color) inset;
  background-color: #FFFFFF;
}

.login-button {
  width: 100%;
  height: 44px;
  font-size: 16px;
  font-weight: 500;
  letter-spacing: 2px;
  margin-top: 8px;
  box-shadow: 0 4px 12px rgba(22, 119, 255, 0.2);
}

.login-button:hover {
  box-shadow: 0 6px 16px rgba(22, 119, 255, 0.3);
  transform: translateY(-1px);
}

.footer {
  margin-top: 40px;
  font-size: 12px;
  color: var(--text-disabled);
  text-align: center;
}

@keyframes float {
  0% { transform: translateY(0px); }
  50% { transform: translateY(-10px); }
  100% { transform: translateY(0px); }
}
</style>
