<template>
  <el-container class="layout-container">
    <el-aside 
      :width="appStore.sidebarCollapsed ? '64px' : '220px'" 
      class="aside"
      :class="{ 'doctor-aside': userStore.isDoctor }"
    >
      <Sidebar />
    </el-aside>
    <el-container class="right-container">
      <el-header class="header">
        <Navbar />
      </el-header>
      <el-main class="main">
        <router-view v-slot="{ Component }">
          <transition name="fade-transform" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import Sidebar from './Sidebar.vue'
import Navbar from './Navbar.vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'

const appStore = useAppStore()
const userStore = useUserStore()
</script>

<style scoped>
.layout-container {
  height: 100vh;
  width: 100%;
}

.aside {
  background: linear-gradient(to bottom, var(--sidebar-bg-start), var(--sidebar-bg-end));
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
  box-shadow: 2px 0 8px 0 rgba(0, 0, 0, 0.05);
  z-index: 10;
}

.doctor-aside {
  background: linear-gradient(to bottom, var(--doctor-sidebar-bg-start), var(--doctor-sidebar-bg-end));
}

.right-container {
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.header {
  height: 60px;
  background-color: var(--bg-card);
  padding: 0;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  z-index: 9;
}

.main {
  background-color: var(--bg-page);
  padding: 24px;
  overflow-y: auto;
  flex: 1;
}

/* fade-transform transition (shorter, smoother) */
.fade-transform-enter-active,
.fade-transform-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateX(-20px);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateX(20px);
}
</style>
