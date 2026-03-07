<template>
  <div class="chat-container">
    <!-- Sidebar: Session List -->
    <div class="chat-sidebar">
      <div class="sidebar-header">
        <el-button type="primary" class="new-chat-btn" @click="handleCreateSession" :icon="Plus">
          新建对话
        </el-button>
      </div>
      <div class="session-list" v-loading="loadingSessions">
        <div
          v-for="session in sessions"
          :key="session.id"
          :class="['session-item', { active: currentSession?.id === session.id }]"
          @click="selectSession(session)"
        >
          <div class="session-icon">
            <el-icon><ChatDotRound /></el-icon>
          </div>
          <div class="session-info">
            <span class="title-text" :title="session.title">{{ session.title || '新对话' }}</span>
            <span class="time-text">{{ formatTime(session.createTime) }}</span>
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
        <el-empty v-if="!loadingSessions && sessions.length === 0" description="暂无历史对话" :image-size="60" />
      </div>
    </div>

    <!-- Main: Chat Messages -->
    <div class="chat-main">
      <div class="chat-header" v-if="currentSession">
        <div class="header-info">
          <span class="session-title">{{ currentSession.title || '对话中...' }}</span>
          <el-tag size="small" effect="plain" class="ml-2">{{ getSessionTypeLabel(sessionType) }}</el-tag>
        </div>
      </div>
      
      <div class="chat-messages" ref="messageBox">
        <div v-if="!currentSession" class="empty-state">
          <div class="empty-content">
            <el-icon class="empty-icon"><ChatLineRound /></el-icon>
            <h3>开始您的医疗咨询</h3>
            <p>选择左侧历史对话，或点击"新建对话"开始</p>
          </div>
        </div>
        <template v-else>
          <div v-if="messages.length === 0" class="empty-session">
            <p>你好！我是您的医疗AI助手，请问有什么可以帮您？</p>
          </div>
          <div
            v-for="(msg, index) in messages"
            :key="index"
            :class="['message-wrapper', msg.role === 'user' ? 'user' : 'assistant']"
          >
            <div class="avatar">
              <el-avatar :size="36" :src="msg.role === 'user' ? userAvatar : aiAvatar" :icon="msg.role === 'user' ? UserFilled : Service" :class="msg.role">
                {{ msg.role === 'user' ? 'U' : 'AI' }}
              </el-avatar>
            </div>
            <div class="message-content">
              <div class="message-bubble markdown-body" v-html="renderMarkdown(msg.content)"></div>
              <div class="message-time" v-if="msg.createTime">{{ formatTime(msg.createTime) }}</div>
            </div>
          </div>
          <div v-if="streamingContent" class="message-wrapper assistant">
            <div class="avatar">
              <el-avatar :size="36" :src="aiAvatar" :icon="Service" class="assistant">AI</el-avatar>
            </div>
            <div class="message-content">
              <div class="message-bubble markdown-body" v-html="renderMarkdown(streamingContent)"></div>
              <span class="typing-indicator">AI正在思考...</span>
            </div>
          </div>
        </template>
      </div>

      <div class="chat-input-area" v-if="currentSession">
        <div class="input-wrapper">
          <el-input
            v-model="inputMessage"
            type="textarea"
            :rows="3"
            placeholder="请输入您的问题... (Shift + Enter 换行)"
            resize="none"
            @keydown.enter.prevent="handleKeydown"
            class="custom-textarea"
          />
          <div class="input-footer">
            <span class="hint">内容由AI生成，仅供参考，不作为医疗诊断依据</span>
            <el-button type="primary" :disabled="!inputMessage.trim() || sending" :loading="sending" @click="handleSend">
              <el-icon class="mr-1"><Position /></el-icon> 发送
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch } from 'vue'
import { Plus, ChatDotRound, Delete, UserFilled, Service, ChatLineRound, Position } from '@element-plus/icons-vue'
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
import dayjs from 'dayjs'
// Assets
// import aiAvatarImg from '@/assets/ai-avatar.png' 

const props = defineProps({
  sessionType: {
    type: String,
    default: 'ENCYCLOPEDIA' // ENCYCLOPEDIA | TRIAGE
  }
})

// State
const loadingSessions = ref(false)
const sessions = ref([])
const currentSession = ref(null)
const messages = ref([])
const inputMessage = ref('')
const sending = ref(false)
const streamingContent = ref('')
const messageBox = ref(null)

// Assets
const aiAvatar = '' // aiAvatarImg || ''
const userAvatar = '' // Use default icon

// Methods
const formatTime = (time) => {
  if (!time) return ''
  const date = dayjs(time)
  const now = dayjs()
  if (date.isSame(now, 'day')) {
    return date.format('HH:mm')
  } else if (date.isSame(now.subtract(1, 'day'), 'day')) {
    return '昨天 ' + date.format('HH:mm')
  } else {
    return date.format('MM-DD HH:mm')
  }
}

const getSessionTypeLabel = (type) => {
  const map = {
    'ENCYCLOPEDIA': '医学百科',
    'TRIAGE': '智能导诊'
  }
  return map[type] || type
}

const fetchSessions = async () => {
  loadingSessions.value = true
  try {
    const res = await getSessionList()
    // Sort by createTime desc
    const allSessions = res.data || []
    sessions.value = allSessions
      .filter(s => s.sessionType === props.sessionType)
      .sort((a, b) => new Date(b.createTime) - new Date(a.createTime))
      
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
  if (currentSession.value?.id === session.id) return
  currentSession.value = session
  messages.value = []
  streamingContent.value = ''
  try {
    const res = await getMessageList(session.id)
    messages.value = (res.data || []).map(m => ({
      role: m.role.toLowerCase(),
      content: m.content,
      createTime: m.createTime
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
    ElMessage.success('新会话已创建')
  } catch (error) {
    ElMessage.error('创建会话失败')
  }
}

const handleDeleteSession = async (session) => {
  try {
    await ElMessageBox.confirm('确定要删除这个对话吗？', '提示', { type: 'warning' })
    await deleteSession(session.id)
    const index = sessions.value.findIndex(s => s.id === session.id)
    if (index > -1) sessions.value.splice(index, 1)
    
    if (currentSession.value?.id === session.id) {
      if (sessions.value.length > 0) {
        selectSession(sessions.value[0])
      } else {
        currentSession.value = null
        messages.value = []
      }
    }
    ElMessage.success('删除成功')
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('删除失败')
  }
}

const handleKeydown = (e) => {
  if (e.shiftKey) return // Allow new line
  handleSend()
}

const handleSend = async () => {
  if (!inputMessage.value.trim() || sending.value) return

  const userMsg = inputMessage.value
  const tempMsg = { role: 'user', content: userMsg, createTime: new Date().toISOString() }
  messages.value.push(tempMsg)
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
    
    let buffer = ''
    while (true) {
      const { value, done } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      const events = buffer.split('\n\n')
      buffer = events.pop() ?? ''

      for (const event of events) {
        let eventType = ''
        let dataStr = ''
        for (const line of event.split('\n')) {
          if (line.startsWith('event:')) {
            eventType = line.slice(6).trim()
          } else if (line.startsWith('data:')) {
            dataStr = line.slice(5).trim()
          }
        }
        if (!dataStr || dataStr === '[DONE]') continue
        if (eventType === 'complete') continue
        try {
          const parsed = JSON.parse(dataStr)
          if (parsed.content) {
            streamingContent.value += parsed.content
            scrollToBottom()
          }
        } catch (e) {
          console.warn('SSE partial JSON skipped:', dataStr.slice(0, 50))
        }
      }
    }

    messages.value.push({ role: 'assistant', content: streamingContent.value, createTime: new Date().toISOString() })
    streamingContent.value = ''
    
    // Refresh session title if it was '新对话' and this is the first message
    if (currentSession.value.title === '新对话' || !currentSession.value.title) {
       // Ideally fetch session info again or update locally
       currentSession.value.title = userMsg.slice(0, 10) + '...'
    }
  } catch (error) {
    console.error('SSE Error:', error)
    ElMessage.error('发送消息失败，请重试')
    messages.value.push({ role: 'assistant', content: '**发送失败**：网络错误或服务不可用。', createTime: new Date().toISOString() })
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messageBox.value) {
      messageBox.value.scrollTo({
        top: messageBox.value.scrollHeight,
        behavior: 'smooth'
      })
    }
  })
}

const renderMarkdown = (content) => {
  if (!content) return ''
  // Basic markdown rendering
  let html = content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    
  // Code blocks
  html = html.replace(/```(\w*)([\s\S]*?)```/g, '<pre><code class="language-$1">$2</code></pre>')
  
  // Inline code
  html = html.replace(/`([^`]+)`/g, '<code>$1</code>')
  
  // Bold
  html = html.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
  
  // Lists
  html = html.replace(/^\s*-\s+(.*)$/gm, '<li>$1</li>')
  html = html.replace(/(<li>.*<\/li>)/s, '<ul>$1</ul>') // Very naive list wrapping
  
  // Line breaks (convert \n to <br> but preserve pre blocks)
  // This is tricky with regex only. 
  // Simplified: just replace \n with <br> if not in pre
  html = html.replace(/\n/g, '<br/>')
  
  return html
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
  background: #fff;
  border-radius: var(--radius-md);
  overflow: hidden;
  border: 1px solid var(--border-color-light);
  box-shadow: var(--shadow-sm);
}

.chat-sidebar {
  width: 280px;
  background: #F9FAFB;
  border-right: 1px solid var(--border-color-light);
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid var(--border-color-light);
}

.new-chat-btn {
  width: 100%;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.session-item {
  display: flex;
  align-items: center;
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 8px;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.session-item:hover {
  background: #fff;
  border-color: var(--border-color-light);
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}

.session-item.active {
  background: #E6F4FF;
  border-color: #BAE0FF;
}

.session-icon {
  margin-right: 10px;
  color: var(--text-secondary);
  font-size: 18px;
  display: flex;
}

.session-item.active .session-icon {
  color: var(--primary-color);
}

.session-info {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.title-text {
  font-size: 14px;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-weight: 500;
}

.time-text {
  font-size: 11px;
  color: var(--text-placeholder);
  margin-top: 2px;
}

.delete-btn {
  opacity: 0;
  transition: opacity 0.2s;
  padding: 4px;
}

.session-item:hover .delete-btn {
  opacity: 1;
}

/* Chat Main Area */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.chat-header {
  padding: 16px 24px;
  border-bottom: 1px solid var(--border-color-light);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.session-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  background: #F9FAFB;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.empty-state {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
}

.empty-content {
  text-align: center;
}

.empty-icon {
  font-size: 48px;
  color: #DCDFE6;
  margin-bottom: 16px;
}

.empty-session {
  text-align: center;
  color: var(--text-secondary);
  font-size: 13px;
  padding: 20px;
}

.message-wrapper {
  display: flex;
  gap: 16px;
  max-width: 85%;
}

.message-wrapper.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.message-wrapper.assistant {
  align-self: flex-start;
}

.avatar {
  flex-shrink: 0;
}

.avatar .user {
  background-color: var(--primary-color);
}

.avatar .assistant {
  background-color: #fff;
  border: 1px solid #E4E7ED;
  color: var(--primary-color);
}

.message-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0; /* Fix overflow */
}

.message-bubble {
  padding: 12px 16px;
  font-size: 14px;
  line-height: 1.6;
  word-wrap: break-word;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.user .message-bubble {
  background: var(--primary-color);
  color: #fff;
  border-radius: 12px 12px 2px 12px;
}

.assistant .message-bubble {
  background: #fff;
  border: 1px solid var(--border-color-light);
  color: var(--text-primary);
  border-radius: 12px 12px 12px 2px;
}

.message-time {
  font-size: 11px;
  color: var(--text-placeholder);
  padding: 0 4px;
}

.user .message-time {
  text-align: right;
}

.typing-indicator {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 4px;
}

/* Chat Input */
.chat-input-area {
  padding: 20px 24px;
  background: #fff;
  border-top: 1px solid var(--border-color-light);
}

.input-wrapper {
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 12px;
  transition: border-color 0.2s;
}

.input-wrapper:focus-within {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 2px rgba(22, 119, 255, 0.1);
}

.custom-textarea :deep(.el-textarea__inner) {
  border: none;
  box-shadow: none;
  padding: 0;
  resize: none;
  background: transparent;
}

.input-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}

.hint {
  font-size: 12px;
  color: var(--text-placeholder);
}

/* Markdown Styles */
.markdown-body :deep(p) {
  margin: 0 0 8px;
}
.markdown-body :deep(p:last-child) {
  margin: 0;
}
.markdown-body :deep(pre) {
  background: #f6f8fa;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 8px 0;
}
.markdown-body :deep(code) {
  background: rgba(0, 0, 0, 0.05);
  padding: 2px 4px;
  border-radius: 4px;
  font-family: monospace;
}
.user .markdown-body :deep(code) {
  background: rgba(255, 255, 255, 0.2);
}
</style>
