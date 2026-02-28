<template>
  <div class="sidebar-container">
    <div class="logo">
      <img src="@/assets/vue.svg" alt="logo" />
      <span v-if="!appStore.sidebarCollapsed">医疗AI后台</span>
    </div>
    <el-menu
      :default-active="activeMenu"
      :collapse="appStore.sidebarCollapsed"
      background-color="#304156"
      text-color="#bfcbd9"
      active-text-color="#409EFF"
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
          <span>预约患者</span>
        </el-menu-item>
        <el-menu-item index="/doctor/assistant">
          <el-icon><Help /></el-icon>
          <span>百科助手</span>
        </el-menu-item>
      </template>
    </el-menu>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import {
  DataBoard, User, OfficeBuilding, Avatar, Calendar, 
  Reading, ChatDotRound, Setting, Postcard, Watch, List, Help
} from '@element-plus/icons-vue'

const route = useRoute()
const userStore = useUserStore()
const appStore = useAppStore()

const activeMenu = computed(() => {
  const { path } = route
  return path
})
</script>

<style scoped>
.sidebar-container {
  height: 100%;
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: bold;
  font-size: 18px;
  background-color: #2b2f3a;
}
.logo img {
  width: 30px;
  margin-right: 10px;
}
.el-menu-vertical {
  border: none;
}
.el-menu-vertical:not(.el-menu--collapse) {
  width: 220px;
}
</style>
