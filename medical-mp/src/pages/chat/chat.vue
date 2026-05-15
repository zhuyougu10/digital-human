<template>
  <view class="chat-page">
    <view v-if="isPageBootLoading" class="page-loading-overlay">
      <view class="page-loading-card">
        <view class="page-loading-spinner"></view>
        <text class="page-loading-title">安禾正在准备中</text>
        <text class="page-loading-desc">正在加载数字人，请稍候...</text>
      </view>
    </view>

    <!-- 顶部 Live2D 区域 -->
    <view class="live2d-area" :style="{ height: live2dHeight + 'px' }">
      <canvas
        type="webgl"
        id="live2d-canvas"
        class="live2d-canvas"
        :style="{ width: '100%', height: live2dHeight + 'px' }"
      ></canvas>
      <view class="live2d-overlay">
        <view class="assistant-card">
          <text class="assistant-name">数字人医疗助手</text>
          <text class="assistant-subtitle">我是你的医疗助手安禾</text>
          <view class="status-bar">
            <view class="status-dot" :class="{ active: isThinking }"></view>
            <text class="status-text">{{ statusText }}</text>
          </view>
        </view>
      </view>
      <view class="new-chat-btn" @click="handleNewChat">
        <text>新对话</text>
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
import { ref, onMounted, onUnmounted, nextTick, computed, getCurrentInstance } from 'vue'
import { onLoad, onHide, onShow } from '@dcloudio/uni-app'
import { createSession, getMessageList, getSessionList } from '@/api/chat'
import { createSSERequest } from '@/utils/sse'
import ChatMessage from '@/components/ChatMessage.vue'
import TtsPlayer from '@/components/TtsPlayer.vue'
import { createCubismRenderer } from '@/lib/cubism-renderer'
import { Live2dLipSync } from '@/lib/live2d-lip-sync'
import { getRuntimeConfig, resolveTtsUrl as buildTtsUrl } from '../../../shared/runtime-config'

// ---- 布局 ----
const systemInfo = uni.getSystemInfoSync()
const screenHeight = systemInfo.windowHeight
const live2dHeight = ref(Math.floor(screenHeight * 0.4))
const chatAreaHeight = computed(() => screenHeight - live2dHeight.value - 50)

// ---- Live2D ----
let renderer = null
let lipSync = null
const isPageBootLoading = ref(true)
const live2dReady = ref(false)
let live2dLoadingTimeoutId = null

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

// ---- Live2D 动作映射 ----
const AVATAR_CUES = {
  greeting: { expression: 'Star', motionGroup: '', motionIndex: 0 },
  symptom_collection: { expression: null, motionGroup: 'Idle', motionIndex: 0 },
  doctor_recommendation: { expression: 'Star', motionGroup: '', motionIndex: 1 },
  slot_selection: { expression: null, motionGroup: 'Idle', motionIndex: 0 },
  appointment_success: { expression: 'Star', motionGroup: '', motionIndex: 0 },
  appointment_failure: { expression: 'Cry', motionGroup: 'Idle', motionIndex: 0 },
  knowledge_explanation: { expression: null, motionGroup: '', motionIndex: 1 },
  urgent_warning: { expression: 'Aozame', motionGroup: 'Idle', motionIndex: 0 },
  fallback_error: { expression: 'Aozame', motionGroup: 'Idle', motionIndex: 0 }
}

const handleAvatarCue = (cue) => {
  if (!renderer || !cue) return
  const config = AVATAR_CUES[cue]
  if (!config) return
  
  console.log('[Chat] Triggering avatar cue:', cue, config)
  
  if (config.expression) {
    renderer.setExpression(config.expression)
  }
  
  if (config.motionGroup !== undefined && config.motionIndex !== undefined) {
    renderer.playMotion(config.motionGroup, config.motionIndex)
  }
}

// ---- TTS 队列 ----
let ttsQueue = []
let ttsPlaying = false
let ttsTotalSegments = 0
let currentPlayIndex = 0

// ---- SSE 流式状态 ----
let currentAiMessageIndex = -1
let currentFullText = ''

const { apiBase } = getRuntimeConfig()

const resolveTtsUrl = (ttsUrl) => {
  return buildTtsUrl(ttsUrl)
}

// =============== Live2D 初始化 ===============

const instance = getCurrentInstance()

const clearLive2dLoadingTimeout = () => {
  if (live2dLoadingTimeoutId) {
    clearTimeout(live2dLoadingTimeoutId)
    live2dLoadingTimeoutId = null
  }
}

const finishPageBootLoading = () => {
  isPageBootLoading.value = false
}

const initLive2D = () => {
  const query = uni.createSelectorQuery().in(instance)
  query.select('#live2d-canvas').node().exec((res) => {
    const canvasNode = res?.[0]?.node
    if (!canvasNode) {
      console.error('[Chat] 无法获取 canvas 节点')
      clearLive2dLoadingTimeout()
      finishPageBootLoading()
      uni.showToast({ title: '数字人加载失败', icon: 'none' })
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
        live2dReady.value = true
        clearLive2dLoadingTimeout()
        finishPageBootLoading()
        lipSync = new Live2dLipSync((value) => renderer?.setMouthOpenY(value))
        // 启动口型同步 ticker
        startLipSyncTicker()
      })
      .catch((err) => {
        console.error('[Chat] Live2D 加载失败:', err)
        clearLive2dLoadingTimeout()
        finishPageBootLoading()
        uni.showToast({ title: '数字人加载失败', icon: 'none' })
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

const updateMessageContent = (index, content) => {
  if (index < 0 || index >= messages.value.length) return
  const target = messages.value[index]
  messages.value.splice(index, 1, { ...target, content })
}

const parseSessionId = (payload) => {
  return payload?.sessionId || payload?.id || payload?.data?.id || payload?.data?.sessionId || ''
}

const isActiveTriageSession = (session) => {
  return session?.sessionType === 'TRIAGE' && Number(session?.status) === 0
}

const getSessionTimestamp = (session) => {
  const value = session?.updateTime || session?.createTime
  if (!value) return 0
  const timestamp = new Date(value).getTime()
  return Number.isNaN(timestamp) ? 0 : timestamp
}

const pickReusableTriageSession = (sessionList) => {
  if (!Array.isArray(sessionList) || sessionList.length === 0) return null

  const triageSessions = sessionList.filter((session) => session?.sessionType === 'TRIAGE')
  if (triageSessions.length === 0) return null

  return triageSessions
    .slice()
    .sort((a, b) => {
      const activeDiff = Number(isActiveTriageSession(b)) - Number(isActiveTriageSession(a))
      if (activeDiff !== 0) return activeDiff
      return getSessionTimestamp(b) - getSessionTimestamp(a)
    })[0]
}

const resetAvatarToNeutral = () => {
  if (!renderer) return
  console.log('[Chat] Resetting avatar to neutral state')
  renderer.resetExpression()
  renderer.playMotion('Idle', 0)
}

const resetTtsState = () => {
  uni.$off('TTS_PLAY_ENDED')
  ttsQueue = []
  ttsPlaying = false
  ttsTotalSegments = 0
  currentPlayIndex = 0
  currentTtsUrl.value = ''
  lipSync?.stop()
  resetAvatarToNeutral()
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
  let currentTurnCue = ''

  // 添加空的 AI 消息占位
  addMessage('assistant', '')
  currentAiMessageIndex = messages.value.length - 1

  // 重置 TTS 状态
  resetTtsState()

  createSSERequest(
    '/ai/chat/send',
    { sessionId: Number(sessionId.value), message: text },
    {
      onMessage: (_type, raw) => {
        console.log('[Chat] SSE消息:', _type, raw.substring(0, 120))
        try {
          const payload = JSON.parse(raw)
          
          const rawCue = payload.metadata?.avatarCue || payload.metadata?.cue || payload.avatarCue || payload.cue
          let cueValue = ''
          if (rawCue) {
            if (typeof rawCue === 'object') {
              cueValue = rawCue.bucket || rawCue.action || rawCue.expression || ''
            } else if (typeof rawCue === 'string') {
              cueValue = rawCue
            }
          }
          if (cueValue && cueValue !== currentTurnCue) {
            handleAvatarCue(cueValue)
            currentTurnCue = cueValue
          }

          if (payload.type === 'token') {
            currentFullText += payload.content || ''
            if (currentAiMessageIndex >= 0) {
              updateMessageContent(currentAiMessageIndex, currentFullText)
            }
            scrollToBottom()
          } else if (payload.type === 'tts') {
            console.log('[Chat] 收到TTS事件:', JSON.stringify(payload))
            if (payload.ttsUrl) {
              const audioUrl = resolveTtsUrl(payload.ttsUrl)
              console.log('[Chat] TTS音频URL:', audioUrl)
              const fileName = payload.ttsUrl.split('/').pop()
              ttsQueue.push({
                segmentIndex: payload.segmentIndex || 0,
                ttsUrl: audioUrl,
                fileName
              })
              if (typeof payload.totalSegments === 'number' && payload.totalSegments > 0) {
                ttsTotalSegments = payload.totalSegments
              }
              ttsQueue.sort((a, b) => a.segmentIndex - b.segmentIndex)
              playNextTtsSegment()
            }
          } else if (payload.type === 'tts_error') {
            console.warn('[Chat] TTS 合成失败:', payload.content)
          } else if (payload.type === 'complete') {
            // 确保最终文本完整
            if (currentAiMessageIndex >= 0 && currentFullText) {
              updateMessageContent(currentAiMessageIndex, currentFullText)
            }
            if (typeof payload.totalSegments === 'number' && payload.totalSegments > 0) {
              ttsTotalSegments = payload.totalSegments
            }
          } else if (payload.type === 'error') {
            currentFullText += payload.content || '服务暂时不可用'
            if (currentAiMessageIndex >= 0) {
              updateMessageContent(currentAiMessageIndex, currentFullText)
            }
          }
        } catch (e) {
          // 非 JSON，当作纯文本 token
          currentFullText += raw
          if (currentAiMessageIndex >= 0) {
            updateMessageContent(currentAiMessageIndex, currentFullText)
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
          updateMessageContent(currentAiMessageIndex, '抱歉，服务暂时不可用，请稍后重试')
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
  if (!next) {
    console.log('[Chat] TTS队列无待播放段落, currentPlayIndex=', currentPlayIndex, 'queue=', JSON.stringify(ttsQueue))
    return
  }

  console.log('[Chat] 播放TTS:', next.ttsUrl)
  ttsPlaying = true
  currentTtsUrl.value = next.ttsUrl

  // 启动口型同步
  lipSync?.start()

  // 监听播放完成
  const checkDone = () => {
    ttsPlaying = false
    currentPlayIndex++
    if (currentPlayIndex >= ttsTotalSegments && ttsTotalSegments > 0) {
      lipSync?.stop()
      cleanupTtsAudio()
      resetAvatarToNeutral()
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
        sessionId.value = parseSessionId(result)

        // 清空消息
        resetTtsState()
        messages.value = []
        allHistoryMessages.value = []
        hasMoreHistory.value = false
        addMessage('assistant', '您好，我是你的医疗助手安禾。请描述您的症状，我来帮您分析并推荐合适的医生。')
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
    const sessionList = await getSessionList()
    const activeSession = pickReusableTriageSession(sessionList)

    if (activeSession) {
      sessionId.value = parseSessionId(activeSession)
    } else {
      const result = await createSession('TRIAGE')
      sessionId.value = parseSessionId(result)
    }

    if (sessionId.value) {
      await loadHistory()
    }

    if (messages.value.length === 0) {
      addMessage('assistant', '您好，我是安禾。请描述您的症状，我来帮您分析并推荐合适的医生。')
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
  live2dLoadingTimeoutId = setTimeout(() => {
    if (live2dReady.value) return
    console.warn('[Chat] Live2D 加载超时，关闭首屏 loading')
    finishPageBootLoading()
    clearLive2dLoadingTimeout()
  }, 10000)

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
  uni.$off('TTS_PLAY_ENDED')
  clearLive2dLoadingTimeout()
  if (lipSyncTickerId) {
    clearTimeout(lipSyncTickerId)
    lipSyncTickerId = null
  }
  resetTtsState()
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
  background: var(--bg-page);
}

.page-loading-overlay {
  position: fixed;
  inset: 0;
  z-index: 999;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40rpx;
  background: var(--bg-page);
}

.page-loading-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20rpx;
  width: 100%;
  max-width: 420rpx;
  padding: 48rpx 36rpx;
  border-radius: 32rpx;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 20rpx 60rpx rgba(31, 79, 111, 0.12);
}

.page-loading-spinner {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  border: 6rpx solid rgba(31, 79, 111, 0.12);
  border-top-color: var(--brand-primary);
  animation: page-loading-spin 0.9s linear infinite;
}

.page-loading-title {
  font-size: 34rpx;
  font-weight: 600;
  color: var(--text-main);
}

.page-loading-desc {
  font-size: 26rpx;
  color: var(--text-subtle);
}

@keyframes page-loading-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.live2d-area {
  position: relative;
  width: 100%;
  background: linear-gradient(180deg, var(--brand-primary-muted) 0%, var(--bg-page) 100%);
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
  justify-content: flex-start;
  align-items: center;
  padding: 60rpx 30rpx 20rpx;
}

.assistant-card {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
  padding: 18rpx 22rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.76);
  backdrop-filter: blur(8rpx);
}

.assistant-name {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--text-main);
}

.assistant-subtitle {
  font-size: 24rpx;
  color: var(--text-subtle);
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
  background-color: var(--brand-success);
}

.status-dot.active {
  background-color: var(--brand-warning);
  animation: pulse 1s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.status-text {
  font-size: 24rpx;
  color: var(--text-subtle);
}

.new-chat-btn {
  position: absolute;
  right: 24rpx;
  bottom: 24rpx;
  z-index: 3;
  padding: 10rpx 24rpx;
  border-radius: 30rpx;
  background: rgba(255, 255, 255, 0.8);
  border: 1rpx solid var(--border-color);
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
  color: var(--text-subtle);
}

/* 底部输入区域 */
.input-area {
  display: flex;
  align-items: center;
  padding: 16rpx 20rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1rpx solid var(--border-color);
  gap: 16rpx;
}

.chat-input {
  flex: 1;
  height: 72rpx;
  padding: 0 24rpx;
  border-radius: 36rpx;
  background: var(--bg-muted);
  font-size: 28rpx;
}

.send-btn {
  padding: 16rpx 32rpx;
  border-radius: 36rpx;
  background: var(--brand-primary);
}

.send-btn.disabled {
  background: #ccc;
}

.send-btn text {
  font-size: 28rpx;
  color: #fff;
}
</style>
