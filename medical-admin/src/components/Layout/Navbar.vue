<template>
  <div class="navbar-container">
    <div class="left">
      <div class="collapse-btn-wrapper" @click="appStore.toggleSidebar">
        <el-icon class="collapse-btn">
          <Expand v-if="appStore.sidebarCollapsed" />
          <Fold v-else />
        </el-icon>
      </div>
      
      <el-breadcrumb separator="/" class="breadcrumb">
        <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item v-if="route.meta.title">
          <span class="current-page">{{ route.meta.title }}</span>
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <div class="right">
      <div class="divider"></div>
      
      <el-dropdown @command="handleCommand" trigger="click">
        <div class="user-info-pill">
          <el-avatar 
            :size="28" 
            :src="userStore.userInfo.avatar || defaultAvatar" 
            class="user-avatar"
          />
          <span class="username">{{ userStore.userInfo.username || 'Admin' }}</span>
          <el-icon class="arrow-down"><ArrowDown /></el-icon>
        </div>
        
        <template #dropdown>
          <el-dropdown-menu class="user-dropdown">
            <el-dropdown-item command="profile">
              <el-icon><User /></el-icon>
              个人中心
            </el-dropdown-item>
            <el-dropdown-item command="logout" divided>
              <el-icon><SwitchButton /></el-icon>
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { 
  Expand, Fold, ArrowDown, User, SwitchButton 
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const defaultAvatar = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'

const handleCommand = (command) => {
  if (command === 'logout') {
    userStore.logout()
  } else if (command === 'profile') {
    router.push('/doctor/profile')
  }
}
</script>

<style scoped>
.navbar-container {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  background-color: var(--bg-card);
}

.left {
  display: flex;
  align-items: center;
}

.collapse-btn-wrapper {
  padding: 8px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background-color 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16px;
}

.collapse-btn-wrapper:hover {
  background-color: var(--border-color-light);
}

.collapse-btn {
  font-size: 20px;
  color: var(--text-primary);
}

.breadcrumb :deep(.el-breadcrumb__inner) {
  color: var(--text-secondary);
  font-weight: 400;
}

.breadcrumb :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: var(--text-primary);
  font-weight: 600;
}

.current-page {
  color: var(--primary-color);
}

.right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.divider {
  width: 1px;
  height: 24px;
  background-color: var(--border-color);
  margin-right: 4px;
}

.user-info-pill {
  display: flex;
  align-items: center;
  padding: 4px 8px 4px 4px;
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.2s;
  background-color: transparent;
  border: 1px solid transparent;
}

.user-info-pill:hover {
  background-color: var(--primary-bg);
  border-color: var(--primary-border);
}

.user-avatar {
  border: 1px solid var(--border-color);
}

.username {
  margin: 0 8px;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.arrow-down {
  font-size: 12px;
  color: var(--text-secondary);
}

.user-dropdown :deep(.el-dropdown-menu__item) {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
}
</style>
