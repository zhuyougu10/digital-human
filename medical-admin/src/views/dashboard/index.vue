<template>
  <div class="dashboard-container">
    <el-row :gutter="20">
      <el-col :span="24">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>欢迎使用医疗AI后台系统</span>
            </div>
          </template>
          <div class="welcome-text">
            <h2>你好, {{ userStore.userInfo.username }}</h2>
            <p>你的角色: {{ userStore.roles.join(', ') }}</p>
            <el-tag :type="userStore.isAdmin ? 'danger' : 'success'">
              {{ userStore.isAdmin ? '管理员权限' : '医生权限' }}
            </el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
</script>

<style scoped>
.dashboard-container {
  padding: 20px;
}
.welcome-text {
  padding: 20px 0;
}
</style>
