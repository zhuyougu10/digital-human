<template>
  <div class="login-container">
    <el-card class="login-card">
      <template #header>
        <h2 class="title">医疗AI后台系统</h2>
      </template>
      <el-form :model="loginForm" label-width="0">
        <el-form-item>
          <el-input v-model="loginForm.username" placeholder="用户名" prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="loginForm.password" type="password" placeholder="密码" prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="login-button" @click="handleLogin" :loading="loading">登录</el-button>
        </el-form-item>
      </el-form>
      <div class="tips">
        <p>演示账号：admin / doctor</p>
        <p>默认密码：admin123</p>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import { login, getUserInfo } from '@/api/auth'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)

const loginForm = reactive({
  username: 'admin',
  password: 'admin123'
})

const handleLogin = async () => {
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
    ElMessage.error('登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #2d3a4b;
}
.login-card {
  width: 400px;
}
.title {
  text-align: center;
  margin: 0;
  color: #333;
}
.login-button {
  width: 100%;
}
.tips {
  font-size: 14px;
  color: #999;
  margin-top: 20px;
}
</style>
