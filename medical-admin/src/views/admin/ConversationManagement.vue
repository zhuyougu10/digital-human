<template>
  <div class="conversation-management">
    <el-card shadow="hover" class="main-card">
      <template #header>
        <div class="card-header">
          <div class="left-panel">
            <span class="title">会话管理</span>
          </div>
          <div class="right-panel">
            <el-form :inline="true" :model="searchForm" class="search-form">
              <el-form-item>
                <el-input 
                  v-model="searchForm.userId" 
                  placeholder="搜索用户ID" 
                  clearable 
                  @keyup.enter="handleSearch"
                  class="search-input"
                >
                  <template #prefix><el-icon><Search /></el-icon></template>
                </el-input>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleSearch">查询</el-button>
                <el-button @click="resetSearch">重置</el-button>
              </el-form-item>
            </el-form>
          </div>
        </div>
      </template>

      <el-table 
        :data="conversationList" 
        v-loading="loading" 
        style="width: 100%"
        :header-cell-style="{ background: '#F7F8FA', color: '#1F2937', fontWeight: '600' }"
      >
        <el-table-column prop="sessionId" label="会话ID" width="180" show-overflow-tooltip />
        <el-table-column prop="userId" label="用户ID" width="120" />
        <el-table-column prop="title" label="会话标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="viewHistory(row)">
              <el-icon class="mr-1"><ChatLineRound /></el-icon>查看记录
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="searchForm.pageNum"
          v-model:page-size="searchForm.pageSize"
          layout="total, sizes, prev, pager, next"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          background
        />
      </div>
    </el-card>

    <!-- Chat History Drawer -->
    <el-drawer
      v-model="historyDrawer.visible"
      title="会话详情"
      size="500px"
      class="history-drawer"
    >
      <div v-loading="loadingHistory" class="chat-container">
        <div v-for="(msg, index) in historyDrawer.messages" :key="index" class="message-item" :class="msg.role">
          <div class="message-avatar">
            <el-avatar :size="32" :src="msg.role === 'user' ? userAvatar : aiAvatar">
              {{ msg.role === 'user' ? 'U' : 'AI' }}
            </el-avatar>
          </div>
          <div class="message-content">
            <div class="message-bubble">{{ msg.content }}</div>
            <div class="message-time">{{ formatTime(msg.createTime) }}</div>
          </div>
        </div>
        <el-empty v-if="!loadingHistory && historyDrawer.messages.length === 0" description="暂无聊天记录" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getSessionList, getMessageList } from '@/api/chat'
import { Search, ChatLineRound } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
// import aiAvatarImg from '@/assets/ai-avatar.png' 
const aiAvatar = '' // aiAvatarImg || ''
const userAvatar = '' // Placeholder

const loading = ref(false)
const conversationList = ref([])
const total = ref(0)

const searchForm = reactive({
  pageNum: 1,
  pageSize: 10,
  userId: ''
})

const historyDrawer = reactive({
  visible: false,
  messages: []
})
const loadingHistory = ref(false)

const fetchConversations = async () => {
  loading.value = true
  try {
    const res = await getSessionList(searchForm)
    conversationList.value = res.data.records
    total.value = res.data.total
  } catch (error) {
    console.error('Failed to fetch conversations:', error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  searchForm.pageNum = 1
  fetchConversations()
}

const resetSearch = () => {
  searchForm.userId = ''
  handleSearch()
}

const handleSizeChange = (val) => {
  searchForm.pageSize = val
  fetchConversations()
}

const handleCurrentChange = (val) => {
  searchForm.pageNum = val
  fetchConversations()
}

const viewHistory = async (row) => {
  historyDrawer.visible = true
  loadingHistory.value = true
  try {
    const res = await getMessageList(row.sessionId)
    historyDrawer.messages = res.data
  } catch (error) {
    console.error('Failed to fetch history:', error)
  } finally {
    loadingHistory.value = false
  }
}

const formatTime = (time) => {
  return dayjs(time).format('MM-DD HH:mm')
}

onMounted(() => {
  fetchConversations()
})
</script>

<style scoped>
.conversation-management {
}

.main-card {
  border: none;
  border-radius: var(--radius-md);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.search-form {
  display: flex;
  gap: 10px;
}

.search-form .el-form-item {
  margin-bottom: 0;
  margin-right: 0;
}

.search-input {
  width: 200px;
}

.pagination-container {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
}

.chat-container {
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message-item {
  display: flex;
  gap: 12px;
  max-width: 90%;
}

.message-item.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.message-item.assistant {
  align-self: flex-start;
}

.message-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.message-bubble {
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
}

.message-item.user .message-bubble {
  background-color: var(--primary-color);
  color: #fff;
  border-top-right-radius: 2px;
}

.message-item.assistant .message-bubble {
  background-color: #F3F4F6;
  color: var(--text-primary);
  border-top-left-radius: 2px;
}

.message-time {
  font-size: 12px;
  color: var(--text-placeholder);
  padding: 0 4px;
}

.message-item.user .message-time {
  text-align: right;
}
</style>
