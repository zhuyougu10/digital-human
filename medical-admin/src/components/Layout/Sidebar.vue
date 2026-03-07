<template>
  <div class="sidebar-container">
    <div class="logo">
      <div class="logo-icon">
        <svg viewBox="0 0 1024 1024" width="24" height="24">
          <path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64z m192 472c0 4.4-3.6 8-8 8H548v148c0 4.4-3.6 8-8 8h-56c-4.4 0-8-3.6-8-8V544H328c-4.4 0-8-3.6-8-8v-56c0-4.4 3.6-8 8-8h148V324c0-4.4 3.6-8 8-8h56c4.4 0 8 3.6 8 8v148h148c4.4 0 8 3.6 8 8v56z" fill="#FFFFFF"></path>
        </svg>
      </div>
      <span v-if="!appStore.sidebarCollapsed" class="logo-text">医疗AI后台</span>
    </div>

    <el-scrollbar>
      <el-menu
        :default-active="activeMenu"
        :collapse="appStore.sidebarCollapsed"
        background-color="transparent"
        text-color="rgba(255, 255, 255, 0.65)"
        active-text-color="#FFFFFF"
        unique-opened
        router
        class="el-menu-vertical"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataBoard /></el-icon>
          <span>数据看板</span>
        </el-menu-item>

        <!-- Admin Menu -->
        <template v-if="userStore.isAdmin">
          <el-menu-item index="/admin/users">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/departments">
            <el-icon><OfficeBuilding /></el-icon>
            <span>科室管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/doctors">
            <el-icon><Avatar /></el-icon>
            <span>医生管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/appointments">
            <el-icon><Calendar /></el-icon>
            <span>预约管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/knowledge">
            <el-icon><Reading /></el-icon>
            <span>知识库管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/conversations">
            <el-icon><ChatDotRound /></el-icon>
            <span>对话管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/config">
            <el-icon><Setting /></el-icon>
            <span>系统配置</span>
          </el-menu-item>
        </template>

        <!-- Doctor Menu -->
        <template v-if="userStore.isDoctor">
          <el-menu-item index="/doctor/profile">
            <el-icon><Postcard /></el-icon>
            <span>我的画像</span>
          </el-menu-item>
          <el-menu-item index="/doctor/schedule">
            <el-icon><Watch /></el-icon>
            <span>我的排班</span>
          </el-menu-item>
          <el-menu-item index="/doctor/appointments">
            <el-icon><List /></el-icon>
            <span>我的预约</span>
          </el-menu-item>
          <li
            class="custom-menu-item"
            :class="{ collapsed: appStore.sidebarCollapsed }"
            :title="appStore.sidebarCollapsed ? '百科助手' : ''"
            @click="openEncyclopedia"
          >
            <el-icon><Help /></el-icon>
            <span v-if="!appStore.sidebarCollapsed">百科助手 ↗</span>
          </li>
        </template>
      </el-menu>
    </el-scrollbar>

    <div v-if="!appStore.sidebarCollapsed" class="version-info">
      v1.0.0
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import {
  DataBoard,
  User,
  OfficeBuilding,
  Avatar,
  Calendar,
  Reading,
  ChatDotRound,
  Setting,
  Postcard,
  Watch,
  List,
  Help
} from '@element-plus/icons-vue'

const route = useRoute()
const userStore = useUserStore()
const appStore = useAppStore()

const openEncyclopedia = () => {
  window.open('/encyclopedia', '_blank')
}

const activeMenu = computed(() => {
  const { path } = route
  return path
})
</script>

<style scoped>
.sidebar-container {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  background-color: rgba(0, 0, 0, 0.1);
  overflow: hidden;
  white-space: nowrap;
}

.logo-icon {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12px;
  transition: margin 0.3s;
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  color: #FFFFFF;
  letter-spacing: 0.5px;
}

.el-menu-vertical {
  border: none;
  width: 100% !important;
}

/* Menu Items */
:deep(.el-menu-item) {
  height: 50px;
  line-height: 50px;
  margin: 4px 12px;
  border-radius: var(--radius-sm);
  width: auto;
}

:deep(.el-menu-item:hover) {
  background-color: var(--sidebar-item-hover) !important;
  color: #FFFFFF !important;
}

:deep(.el-menu-item.is-active) {
  background-color: var(--sidebar-item-active) !important;
  color: #FFFFFF !important;
  position: relative;
}

:deep(.el-menu-item.is-active)::before {
  content: "";
  position: absolute;
  left: -12px;
  top: 15%;
  height: 70%;
  width: 3px;
  background-color: var(--primary-color);
  border-radius: 0 4px 4px 0;
}

:deep(.el-menu-item .el-icon) {
  margin-right: 12px;
  font-size: 18px;
}

.custom-menu-item {
  height: 50px;
  line-height: 50px;
  margin: 4px 12px;
  border-radius: var(--radius-sm);
  width: auto;
  display: flex;
  align-items: center;
  color: rgba(255, 255, 255, 0.65);
  list-style: none;
  cursor: pointer;
  padding: 0 20px;
  box-sizing: border-box;
  transition: all 0.3s;
}

.custom-menu-item:hover {
  background-color: var(--sidebar-item-hover);
  color: #FFFFFF;
}

.custom-menu-item .el-icon {
  margin-right: 12px;
  font-size: 18px;
}

/* Collapse behavior */
.el-menu--collapse :deep(.el-menu-item) {
  margin: 4px 12px;
  padding: 0 !important;
  display: flex;
  justify-content: center;
}

.el-menu--collapse :deep(.el-menu-item .el-icon) {
  margin: 0;
}

.custom-menu-item.collapsed {
  margin: 4px 12px;
  padding: 0;
  justify-content: center;
}

.custom-menu-item.collapsed .el-icon {
  margin: 0;
}

.version-info {
  padding: 16px;
  text-align: center;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.3);
  border-top: 1px solid rgba(255, 255, 255, 0.05);
}

/* Sidebar scrollbar styling */
:deep(.el-scrollbar__thumb) {
  background-color: rgba(255, 255, 255, 0.15);
}

:deep(.el-scrollbar__thumb:hover) {
  background-color: rgba(255, 255, 255, 0.25);
}
</style>
