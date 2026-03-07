<template>
  <div class="encyclopedia-page">
    <div class="ep-header">
      <div class="ep-header-left">
        <div class="ep-logo-icon">
          <el-icon size="22" color="#fff"><Reading /></el-icon>
        </div>
        <span class="ep-title">医学百科助手</span>
        <el-tag size="small" type="info" effect="plain" class="ep-tag">面向医生</el-tag>
      </div>
      <div class="ep-header-right">
        <el-button text class="ep-close-btn" @click="closeWindow">
          <el-icon><Close /></el-icon>
          关闭
        </el-button>
      </div>
    </div>
    <div class="ep-body">
      <ChatPanel sessionType="ENCYCLOPEDIA" />
    </div>
  </div>
</template>

<script setup>
import { Reading, Close } from '@element-plus/icons-vue'
import ChatPanel from '@/components/ChatPanel.vue'

const closeWindow = () => window.close()
</script>

<style scoped>
.encyclopedia-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f5f7fa;
}

.ep-header {
  height: 56px;
  background: linear-gradient(135deg, #1677ff 0%, #0958d9 100%);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(22, 119, 255, 0.3);
}

.ep-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ep-logo-icon {
  display: flex;
  align-items: center;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 8px;
  padding: 5px;
}

.ep-title {
  font-size: 17px;
  font-weight: 600;
  color: #fff;
  letter-spacing: 0.5px;
}

.ep-tag {
  border-color: rgba(255, 255, 255, 0.4) !important;
  color: rgba(255, 255, 255, 0.85) !important;
  background: rgba(255, 255, 255, 0.1) !important;
}

.ep-header-right {
  display: flex;
  align-items: center;
}

.ep-close-btn {
  color: rgba(255, 255, 255, 0.85) !important;
  font-size: 13px;
}

.ep-close-btn:hover {
  color: #fff !important;
}

.ep-body {
  flex: 1;
  overflow: hidden;
  padding: 16px;
}

.ep-body :deep(.chat-container) {
  height: 100%;
  border-radius: 10px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08);
}
</style>
