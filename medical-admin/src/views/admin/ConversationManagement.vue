<template>
  <div class="conversation-management">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>对话记录管理</span>
        </div>
      </template>

      <el-table :data="sessionList" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="会话ID" width="100" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="type" label="类型" width="150">
          <template #default="{ row }">
            <el-tag :type="row.type === 'ENCYCLOPEDIA' ? 'success' : 'primary'">
              {{ row.type === 'ENCYCLOPEDIA' ? '百科助手' : '智能导诊' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="开启时间" width="180" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="viewConversation(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Conversation Detail Drawer -->
    <el-drawer
      v-model="drawer.visible"
      :title="`对话详情: ${drawer.currentSession?.title || '新会话'}`"
      size="500px"
    >
      <div class="chat-history" v-loading="drawer.loading">
        <div
          v-for="(msg, index) in drawer.messages"
          :key="index"
          :class="['message-item', msg.role === 'user' ? 'user' : 'assistant']"
        >
          <div class="message-info">
            <span class="role-label">{{ msg.role === 'user' ? '用户' : 'AI助手' }}</span>
            <span class="msg-time">{{ msg.createTime }}</span>
          </div>
          <div class="message-bubble">{{ msg.content }}</div>
        </div>
        <el-empty v-if="drawer.messages.length === 0" description="暂无消息记录" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getSessionList, getMessageList } from '@/api/chat'

const loading = ref(false)
const sessionList = ref([])

const drawer = reactive({
  visible: false,
  loading: false,
  currentSession: null,
  messages: []
})

const fetchSessionList = async () => {
  loading.value = true
  try {
    const res = await getSessionList()
    sessionList.value = res.data
  } catch (error) {
    console.error('Failed to fetch sessions:', error)
  } finally {
    loading.value = false
  }
}

const viewConversation = async (session) => {
  drawer.currentSession = session
  drawer.visible = true
  drawer.loading = true
  try {
    const res = await getMessageList(session.id)
    drawer.messages = res.data.map(m => ({
      ...m,
      role: m.role.toLowerCase()
    }))
  } catch (error) {
    console.error('Failed to fetch messages:', error)
  } finally {
    drawer.loading = false
  }
}

onMounted(() => {
  fetchSessionList()
})
</script>

<style scoped>
.conversation-management {
  padding: 20px;
}
.chat-history {
  padding: 10px;
  background: #f9fbff;
  height: 100%;
  overflow-y: auto;
}
.message-item {
  margin-bottom: 20px;
  display: flex;
  flex-direction: column;
}
.message-item.user {
  align-items: flex-end;
}
.message-item.assistant {
  align-items: flex-start;
}
.message-info {
  margin-bottom: 5px;
  font-size: 12px;
  color: #909399;
}
.msg-time {
  margin-left: 10px;
}
.message-bubble {
  max-width: 85%;
  padding: 10px 15px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
}
.user .message-bubble {
  background: #409eff;
  color: #fff;
  border-top-right-radius: 2px;
}
.assistant .message-bubble {
  background: #fff;
  color: #303133;
  border: 1px solid #e4e7ed;
  border-top-left-radius: 2px;
}
</style>
