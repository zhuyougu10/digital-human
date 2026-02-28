<template>
  <div class="doctor-assistant">
    <el-card shadow="never" class="assistant-card">
      <template #header>
        <div class="card-header">
          <div>
            <div class="title">百科助手</div>
            <div class="desc">
              面向医生的医学知识问答助手，支持多会话、SSE 流式回复与 Markdown 展示
            </div>
          </div>
        </div>
      </template>

      <div class="chat-wrapper">
        <ChatPanel sessionType="ENCYCLOPEDIA" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import ChatPanel from '@/components/ChatPanel.vue'
</script>

<style scoped>
.doctor-assistant {
  padding: 20px;
}

.assistant-card {
  border-radius: 8px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.desc {
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
}

.chat-wrapper {
  height: calc(100vh - 260px);
  min-height: 520px;
}

.chat-wrapper :deep(.chat-container) {
  height: 100%;
}
</style>
