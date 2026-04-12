<template>
  <view class="chat-page">
    <!-- 顶部 Live2D 区域 -->
    <view class="live2d-area" :style="{ height: live2dHeight + 'px' }">
      <canvas
        type="webgl"
        id="live2d-canvas"
        class="live2d-canvas"
        :style="{ width: '100%', height: live2dHeight + 'px' }"
      ></canvas>
      <view class="live2d-overlay">
        <view class="status-bar">
          <view class="status-dot" :class="{ active: isThinking }"></view>
          <text class="status-text">{{ statusText }}</text>
        </view>
        <view class="new-chat-btn" @click="handleNewChat">
          <text>新对话</text>
        </view>
      </view>
    </view>

    <!-- 聊天消息区域 -->
    <scroll-view
      class="chat-area"
      scroll-y
      :scroll-top="scrollTop"
      :style="{ height: chatAreaHeight + 'px' }"
      @scrolltoupper="loadMoreMessages"
    >
      <view v-if="hasMoreHistory" class="load-more-tip">
        <text>{{ isLoadingHistory ? '加载中...' : '上滑加载历史消息' }}</text>
      </view>
      <view v-else-if="messages.length > 0" class="load-more-tip">
        <text>已加载全部历史消息</text>
      </view>

      <ChatMessage
        v-for="(msg, index) in messages"
        :key="index"
        :message="msg"
      />
    </scroll-view>

    <!-- 底部输入区域 -->
    <view class="input-area">
      <input
        class="chat-input"
        v-model="inputText"
        placeholder="描述您的症状..."
        confirm-type="send"
        @confirm="handleSend"
      />
      <view class="send-btn" :class="{ disabled: !inputText.trim() || isSending }" @click="handleSend">
        <text>发送</text>
      </view>
    </view>

    <!-- 隐藏的 TTS 播放器 -->
    <view style="display: none;">
      <TtsPlayer v-if="currentTtsUrl" :tts-url="currentTtsUrl" />
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { onLoad, onHide, onShow } from '@dcloudio/uni-app'
import { createSession, getMessageList } from '@/api/chat'
import { createSSERequest } from '@/utils/sse'
import ChatMessage from '@/components/ChatMessage.vue'
import TtsPlayer from '@/components/TtsPlayer.vue'
import { createCubismRenderer } from '@/lib/cubism-renderer'
import { Live2dLipSync } from '@/lib/live2d-lip-sync'

// ---- 布局 ----
const systemInfo = uni.getSystemInfoSync()
const screenHeight = systemInfo.windowHeight
const live2dHeight = ref(Math.floor(screenHeight * 0.4))
const inputAreaHeight = 100 // 底部输入栏高度 rpx -> 约 50px
const chatAreaHeight = computed(() => screenHeight - live2dHeight.value - 50)

// ---- Live2D ----
let renderer = null
let lipSync = null

// ---- 聊天状态 ----
const sessionId = ref('')
const messages = ref([])
const inputText = ref('')
const scrollTop = ref(0)
const statusText = ref('正在为您服务...')
const isThinking = ref(false)
const isSending = ref(false)
const currentTtsUrl = ref('')

// ---- 历史消息 ----
const allHistoryMessages = ref([])
const hasMoreHistory = ref(false)
const isLoadingHistory = ref(false)
const BATCH_SIZE = 20

// ---- TTS 队列 ----
let ttsQueue = []
let ttsPlaying = false
let ttsTotalSegments = 0
let currentPlayIndex = 0
let currentAbortController = null

// ---- SSE 流式状态 ----
let currentAiMessageIndex = -1
let currentFullText = ''

const apiBase = import.meta.env.VITE_API_BASE || 'http://localhost:8080/api'

// =============== Live2D 初始化 ===============

const initLive2D = () => {
  const query = uni.createSelectorQuery()
  query.select('#live2d-canvas').node().exec((res) => {
    const canvasNode = res?.[0]?.node
    if (!canvasNode) {
      console.error('[Chat] 无法获取 canvas 节点')
      return
    }

    const dpr = systemInfo.pixelRatio || 1
    canvasNode.width = Math.floor(systemInfo.windowWidth * dpr)
    canvasNode.height = Math.floor(live2dHeight.value * dpr)

    renderer = createCubismRenderer()
    renderer
      .loadModel(canvasNode, 'models/doctor', 'wariza.model3.json')
      .then(() => {
        console.log('[Chat] Live2D 模型加载成功')
        lipSync = new Live2dLipSync((value) => renderer?.setMouthOpenY(value))
        // 启动口型同步 ticker
        startLipSyncTicker()
      })
      .catch((err) => {
        console.error('[Chat] Live2D 加载失败:', err)
      })
  })
}

let lipSyncTickerId = null
const startLipSyncTicker = () => {
  const tick = () => {
    lipSync?.update()
    lipSyncTickerId = setTimeout(tick, 16)
  }
  tick()
}

// =============== 聊天逻辑 ===============

const addMessage = (role, content, type = 'text') => {
  messages.value.push({ role, content, type })
  scrollToBottom()
}

const scrollToBottom = () => {
  nextTick(() => {
    scrollTop.value = scrollTop.value === 99999 ? 99998 : 99999
  })
}

const handleSend = () => {
  const text = inputText.value.trim()
  if (!text || isSending.value) return

  inputText.value = ''
  addMessage('user', text)
  sendToBackend(text)
}

const sendToBackend = (text) => {
  if (!sessionId.value) return

  isSending.value = true
  isThinking.value = true
  statusText.value = '正在思考...'
  currentFullText = ''

  // 添加空的 AI 消息占位
  addMessage('assistant', '')
  currentAiMessageIndex = messages.value.length - 1

  // 重置 TTS 状态
  ttsQueue = []
  ttsPlaying = false
  ttsTotalSegments = 0
  currentPlayIndex = 0

  const requestTask = createSSERequest(
    '/ai/chat/send',
    { sessionId: Number(sessionId.value), message: text },
    {
      onMessage: (_type, raw) => {
        try {
          const payload = JSON.parse(raw)
          if (payload.type === 'token') {
            currentFullText += payload.content || ''
            if (currentAiMessageIndex >= 0) {
              messages.value[currentAiMessageIndex].content = currentFullText
            }
            scrollToBottom()
          } else if (payload.type === 'tts') {
            if (payload.ttsUrl) {
              const fileName = payload.ttsUrl.split('/').pop()
              ttsQueue.push({
                segmentIndex: payload.segmentIndex || 0,
                ttsUrl: payload.ttsUrl,
                fileName
              })
              ttsTotalSegments = payload.totalSegments || 1
              ttsQueue.sort((a, b) => a.segmentIndex - b.segmentIndex)
              playNextTtsSegment()
            }
          } else if (payload.type === 'tts_error') {
            console.warn('[Chat] TTS 合成失败:', payload.content)
          } else if (payload.type === 'complete') {
            // 确保最终文本完整
            if (currentAiMessageIndex >= 0 && currentFullText) {
              messages.value[currentAiMessageIndex].content = currentFullText
            }
          } else if (payload.type === 'error') {
            currentFullText += payload.content || '服务暂时不可用'
            if (currentAiMessageIndex >= 0) {
              messages.value[currentAiMessageIndex].content = currentFullText
            }
          }
        } catch (e) {
          // 非 JSON，当作纯文本 token
          currentFullText += raw
          if (currentAiMessageIndex >= 0) {
            messages.value[currentAiMessageIndex].content = currentFullText
          }
          scrollToBottom()
        }
      },
      onComplete: () => {
        isSending.value = false
        isThinking.value = false
        statusText.value = '正在为您服务...'
        currentAiMessageIndex = -1
      },
      onError: (err) => {
        console.error('[Chat] SSE 请求失败:', err)
        isSending.value = false
        isThinking.value = false
        statusText.value = '正在为您服务...'
        if (currentAiMessageIndex >= 0 && !currentFullText) {
          messages.value[currentAiMessageIndex].content = '抱歉，服务暂时不可用，请稍后重试'
        }
        currentAiMessageIndex = -1
      }
    }
  )
}

// =============== TTS 播放 ===============

const playNextTtsSegment = () => {
  if (ttsPlaying) return
  const next = ttsQueue.find((item) => item.segmentIndex === currentPlayIndex)
  if (!next) return

  ttsPlaying = true
  const audioUrl = next.ttsUrl.startsWith('http') ? next.ttsUrl : `${apiBase}${next.ttsUrl}`
  currentTtsUrl.value = audioUrl

  // 启动口型同步
  lipSync?.start()

  // 监听播放完成
  const checkDone = () => {
    ttsPlaying = false
    currentPlayIndex++
    if (currentPlayIndex >= ttsTotalSegments && ttsTotalSegments > 0) {
      lipSync?.stop()
      cleanupTtsAudio()
    } else {
      playNextTtsSegment()
    }
  }

  // TtsPlayer 组件会自动播放，通过事件监听完成
  uni.$once('TTS_PLAY_ENDED', checkDone)
}

const cleanupTtsAudio = () => {
  const token = uni.getStorageSync('token')
  const fileNames = ttsQueue.map((item) => item.fileName)
  if (fileNames.length === 0) return

  uni.request({
    url: `${apiBase}/ai/chat/tts/cleanup`,
    method: 'DELETE',
    header: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    data: fileNames
  })
}

// =============== 历史消息 ===============

const loadHistory = async () => {
  if (!sessionId.value || isLoadingHistory.value) return
  isLoadingHistory.value = true

  try {
    const result = await getMessageList(sessionId.value)
    const historyMessages = Array.isArray(result) ? result : result?.data || []
    if (historyMessages.length === 0) return

    // 拆分：最近 BATCH_SIZE 条立即显示，其余存起来
    const total = historyMessages.length
    const initial = historyMessages.slice(Math.max(0, total - BATCH_SIZE))
    allHistoryMessages.value = historyMessages.slice(0, Math.max(0, total - BATCH_SIZE))
    hasMoreHistory.value = allHistoryMessages.value.length > 0

    messages.value = initial.map((msg) => ({
      role: msg.role === 'assistant' ? 'assistant' : 'user',
      content: msg.content,
      type: 'text'
    }))

    scrollToBottom()
  } catch (e) {
    console.warn('[Chat] 加载历史消息失败:', e)
  } finally {
    isLoadingHistory.value = false
  }
}

const loadMoreMessages = () => {
  if (allHistoryMessages.value.length === 0 || isLoadingHistory.value) return

  const batch = allHistoryMessages.value.splice(-BATCH_SIZE)
  const batchMessages = batch.reverse().map((msg) => ({
    role: msg.role === 'assistant' ? 'assistant' : 'user',
    content: msg.content,
    type: 'text'
  }))

  messages.value = [...batchMessages, ...messages.value]
  hasMoreHistory.value = allHistoryMessages.value.length > 0
}

// =============== 新会话 ===============

const handleNewChat = () => {
  uni.showModal({
    title: '提示',
    content: '确定要开始新的问诊会话吗？',
    success: async (res) => {
      if (!res.confirm) return

      try {
        statusText.value = '正在创建新会话...'
        const token = uni.getStorageSync('token')

        // 结束当前会话
        if (sessionId.value) {
          await new Promise((resolve) => {
            uni.request({
              url: `${apiBase}/ai/chat/session/${sessionId.value}/end`,
              method: 'POST',
              header: { Authorization: `Bearer ${token}` },
              complete: resolve
            })
          })
        }

        // 创建新会话
        const result = await createSession('TRIAGE')
        sessionId.value = result?.sessionId || result?.id || result?.data?.id || ''

        // 清空消息
        messages.value = []
        allHistoryMessages.value = []
        hasMoreHistory.value = false
        addMessage('assistant', '您好！我是AI医疗助手，请描述您的症状，我来帮您分析和推荐合适的医生。')
      } catch (e) {
        console.error('[Chat] 创建新会话失败:', e)
        uni.showToast({ title: '创建新会话失败，请重试', icon: 'none' })
      } finally {
        statusText.value = '正在为您服务...'
      }
    }
  })
}

// =============== 生命周期 ===============

const initChat = async () => {
  try {
    const result = await createSession('TRIAGE')
    sessionId.value = result?.sessionId || result?.id || result?.data?.id || ''

    if (sessionId.value) {
      await loadHistory()
    }

    if (messages.value.length === 0) {
      addMessage('assistant', '您好！我是AI医疗助手，请描述您的症状，我来帮您分析和推荐合适的医生。')
    }
  } catch (e) {
    console.error('[Chat] 会话初始化失败:', e)
    uni.showToast({ title: '会话初始化失败，请返回重试', icon: 'none' })
  }
}

onLoad(() => {
  initChat()
})

onMounted(() => {
  // 延迟初始化 Live2D，确保 canvas 已渲染
  setTimeout(() => initLive2D(), 300)

  // 监听口型同步事件
  uni.$on('LIVE2D_POST_MESSAGE', (payload) => {
    if (payload.type === 'START_LIPSYNC') {
      lipSync?.start(payload.data?.duration || 0)
    } else if (payload.type === 'STOP_LIPSYNC') {
      lipSync?.stop()
    }
  })
})

onHide(() => {
  uni.setStorageSync('chatLeaveTime', Date.now())
})

onShow(() => {
  const leaveTime = uni.getStorageSync('chatLeaveTime')
  if (leaveTime && Date.now() - leaveTime > 5 * 60 * 1000) {
    console.log('[Chat] 会话超时，准备重新初始化')
    if (sessionId.value) {
      const token = uni.getStorageSync('token')
      uni.request({
        url: `${apiBase}/ai/chat/session/${sessionId.value}/end`,
        method: 'POST',
        header: { Authorization: `Bearer ${token}` }
      })
    }
    initChat()
  }
})

onUnmounted(() => {
  uni.$off('LIVE2D_POST_MESSAGE')
  if (lipSyncTickerId) {
    clearTimeout(lipSyncTickerId)
    lipSyncTickerId = null
  }
  renderer?.destroy()
  renderer = null
  lipSync = null
})
</script>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  width: 100vw;
  height: 100vh;
  background: linear-gradient(180deg, #e8f4f8 0%, #f5f7fa 40%, #f5f7fa 100%);
}

.live2d-area {
  position: relative;
  width: 100%;
  background: linear-gradient(180deg, #c5e8f7 0%, #e8f4f8 100%);
  overflow: hidden;
}

.live2d-canvas {
  width: 100%;
  height: 100%;
}

.live2d-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 60rpx 30rpx 20rpx;
}

.status-bar {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.status-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background-color: #4CAF50;
}

.status-dot.active {
  background-color: #FF9800;
  animation: pulse 1s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.status-text {
  font-size: 24rpx;
  color: #666;
}

.new-chat-btn {
  padding: 10rpx 24rpx;
  border-radius: 30rpx;
  background: rgba(255, 255, 255, 0.8);
  border: 1rpx solid #ddd;
}

.new-chat-btn text {
  font-size: 24rpx;
  color: #333;
}

/* 聊天消息区域 */
.chat-area {
  flex: 1;
  padding: 20rpx 0;
  overflow: hidden;
}

.load-more-tip {
  text-align: center;
  padding: 20rpx;
}

.load-more-tip text {
  font-size: 24rpx;
  color: #999;
}

/* 底部输入区域 */
.input-area {
  display: flex;
  align-items: center;
  padding: 16rpx 20rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1rpx solid #eee;
  gap: 16rpx;
}

.chat-input {
  flex: 1;
  height: 72rpx;
  padding: 0 24rpx;
  border-radius: 36rpx;
  background: #f5f5f5;
  font-size: 28rpx;
}

.send-btn {
  padding: 16rpx 32rpx;
  border-radius: 36rpx;
  background: #4A90D9;
}

.send-btn.disabled {
  background: #ccc;
}

.send-btn text {
  font-size: 28rpx;
  color: #fff;
}
</style>
