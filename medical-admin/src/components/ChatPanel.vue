<template>
  <div class="chat-container">
    <!-- Sidebar: Session List -->
    <div class="chat-sidebar">
      <div class="sidebar-header">
        <el-button type="primary" class="new-chat-btn" @click="handleCreateSession">
          <el-icon><Plus /></el-icon> 新建对话
        </el-button>
      </div>
      <div class="session-list" v-loading="loadingSessions">
        <div
          v-for="session in sessions"
          :key="session.id"
          :class="['session-item', { active: currentSession?.id === session.id }]"
          @click="selectSession(session)"
        >
          <div class="session-title">
            <el-icon><ChatDotRound /></el-icon>
            <span class="title-text">{{ session.title || '新对话' }}</span>
          </div>
          <el-button
            type="danger"
            link
            class="delete-btn"
            @click.stop="handleDeleteSession(session)"
          >
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
      </div>
    </div>

    <!-- Main: Chat Messages -->
    <div class="chat-main">
      <div class="chat-header" v-if="currentSession">
        <span>{{ currentSession.title || '对话中...' }}</span>
      </div>
      
      <div class="chat-messages" ref="messageBox">
        <div v-if="!currentSession" class="empty-state">
          <el-empty description="选择或创建一个对话开始聊天" />
        </div>
        <template v-else>
          <div
            v-for="(msg, index) in messages"
            :key="index"
            :class="['message-wrapper', msg.role === 'user' ? 'user' : 'assistant']"
          >
            <div class="message-content">
              <div class="message-bubble" v-html="renderMarkdown(msg.content)"></div>
            </div>
          </div>
          <div v-if="streamingContent" class="message-wrapper assistant">
            <div class="message-content">
              <div class="message-bubble" v-html="renderMarkdown(streamingContent)"></div>
            </div>
          </div>
        </template>
      </div>

      <div class="chat-input" v-if="currentSession">
        <el-input
          v-model="inputMessage"
          type="textarea"
          :rows="3"
          placeholder="请输入您的问题..."
          resize="none"
          @keyup.ctrl.enter="handleSend"
        />
        <div class="input-actions">
          <span class="hint">Ctrl + Enter 发送</span>
          <el-button type="primary" :disabled="!inputMessage.trim() || sending" @click="handleSend">
            发送
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch } from 'vue'
import { Plus, ChatDotRound, Delete } from '@element-plus/icons-vue'
import { 
  createSession, 
  getSessionList, 
  deleteSession, 
  getMessageList, 
  sendMessage, 
  createEncyclopediaSession, 
  encyclopediaChat 
} from '@/api/chat'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps({
  sessionType: {
    type: String,
    default: 'ENCYCLOPEDIA' // ENCYCLOPEDIA | TRIAGE
  }
})

const loadingSessions = ref(false)
const sessions = ref([])
const currentSession = ref(null)
const messages = ref([])
const inputMessage = ref('')
const sending = ref(false)
const streamingContent = ref('')
const messageBox = ref(null)

const fetchSessions = async () => {
  loadingSessions.value = true
  try {
    const res = await getSessionList()
    // Filter sessions by type if needed, but assuming API returns all and we can filter locally
    // If backend doesn't support filtering, we show all or those belonging to this type
    // In this implementation, we'll just show what the backend gives us
    sessions.value = res.data.filter(s => s.type === props.sessionType)
    if (sessions.value.length > 0 && !currentSession.value) {
      selectSession(sessions.value[0])
    }
  } catch (error) {
    console.error('Failed to fetch sessions:', error)
  } finally {
    loadingSessions.value = false
  }
}

const selectSession = async (session) => {
  currentSession.value = session
  messages.value = []
  streamingContent.value = ''
  try {
    const res = await getMessageList(session.id)
    messages.value = res.data.map(m => ({
      role: m.role.toLowerCase(),
      content: m.content
    }))
    scrollToBottom()
  } catch (error) {
    console.error('Failed to fetch messages:', error)
  }
}

const handleCreateSession = async () => {
  try {
    let res
    if (props.sessionType === 'ENCYCLOPEDIA') {
      res = await createEncyclopediaSession()
    } else {
      res = await createSession({ sessionType: props.sessionType })
    }
    const newSession = res.data
    sessions.value.unshift(newSession)
    selectSession(newSession)
  } catch (error) {
    ElMessage.error('创建会话失败')
  }
}

const handleDeleteSession = async (session) => {
  try {
    await ElMessageBox.confirm('确定要删除这个对话吗？', '提示', { type: 'warning' })
    await deleteSession(session.id)
    const index = sessions.value.findIndex(s => s.id === session.id)
    sessions.value.splice(index, 1)
    if (currentSession.value?.id === session.id) {
      currentSession.value = sessions.value[0] || null
      if (currentSession.value) selectSession(currentSession.value)
      else messages.value = []
    }
    ElMessage.success('删除成功')
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('删除失败')
  }
}

const handleSend = async () => {
  if (!inputMessage.value.trim() || sending.value) return

  const userMsg = inputMessage.value
  messages.value.push({ role: 'user', content: userMsg })
  inputMessage.value = ''
  sending.value = true
  streamingContent.value = ''
  scrollToBottom()

  try {
    const sendFn = props.sessionType === 'ENCYCLOPEDIA' ? encyclopediaChat : sendMessage
    const response = await sendFn({
      sessionId: currentSession.value.id,
      message: userMsg
    })

    if (!response.ok) throw new Error('Network response was not ok')

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      
      const chunk = decoder.decode(value, { stream: true })
      // SSE format: data: content\n\n
      const lines = chunk.split('\n')
      for (const line of lines) {
        if (line.startsWith('data:')) {
          const content = line.replace('data:', '').trim()
          if (content === '[DONE]') break
          try {
            const parsed = JSON.parse(content)
            if (parsed.content) {
              streamingContent.value += parsed.content
            }
          } catch (e) {
            streamingContent.value += content
          }
          scrollToBottom()
        }
      }
    }

    messages.value.push({ role: 'assistant', content: streamingContent.value })
    streamingContent.value = ''
  } catch (error) {
    console.error('SSE Error:', error)
    ElMessage.error('发送消息失败')
  } finally {
    sending.value = false
  }
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messageBox.value) {
      messageBox.value.scrollTop = messageBox.value.scrollHeight
    }
  })
}

const renderMarkdown = (content) => {
  if (!content) return ''
  // Simple markdown-like rendering
  return content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\n/g, '<br/>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
}

onMounted(() => {
  fetchSessions()
})

watch(() => props.sessionType, () => {
  currentSession.value = null
  fetchSessions()
})
</script>

<style scoped>
.chat-container {
  display: flex;
  height: 100%;
  background: #f5f7fa;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e4e7ed;
}

.chat-sidebar {
  width: 260px;
  background: #fff;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid #f0f2f5;
}

.new-chat-btn {
  width: 100%;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: background 0.2s;
}

.session-item:hover {
  background: #f0f2f5;
}

.session-item.active {
  background: #ecf5ff;
  color: #409eff;
}

.session-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  overflow: hidden;
}

.title-text {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 14px;
}

.delete-btn {
  display: none;
}

.session-item:hover .delete-btn {
  display: block;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.chat-header {
  padding: 16px 20px;
  border-bottom: 1px solid #f0f2f5;
  font-weight: bold;
  font-size: 16px;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #f9fbff;
}

.empty-state {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.message-wrapper {
  margin-bottom: 20px;
  display: flex;
}

.message-wrapper.user {
  justify-content: flex-end;
}

.message-content {
  max-width: 80%;
}

.message-bubble {
  padding: 12px 16px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.user .message-bubble {
  background: #409eff;
  color: #fff;
  border-bottom-right-radius: 2px;
}

.assistant .message-bubble {
  background: #fff;
  color: #303133;
  border: 1px solid #e4e7ed;
  border-bottom-left-radius: 2px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
}

.chat-input {
  padding: 20px;
  border-top: 1px solid #f0f2f5;
  background: #fff;
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
}

.hint {
  font-size: 12px;
  color: #909399;
}
</style>
