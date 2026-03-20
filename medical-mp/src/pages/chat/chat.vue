<template>
  <view class="chat-bridge">
    <!-- Scheme A: Full-screen Web-view shell -->
    <web-view 
      v-if="live2dUrl" 
      :src="live2dUrl" 
      class="full-webview"
      @message="onWebviewMessage"
    ></web-view>
    
    <!-- Hidden TtsPlayer for background audio -->
    <view style="display: none;">
      <TtsPlayer v-if="currentTtsUrl" :tts-url="currentTtsUrl" />
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { createSession } from '@/api/chat'
import { createSSERequest } from '@/utils/sse'
import TtsPlayer from '@/components/TtsPlayer.vue'

const sessionId = ref('')
const live2dBaseUrl = 'http://localhost:5173' // Use local IP for real device test
const live2dUrl = ref(`${live2dBaseUrl}?t=${Date.now()}`)
const currentTtsUrl = ref('')

// 消息桥接：UniApp -> H5
const postToH5 = (type, data) => {
  // 通过 URL hash 传递指令给 H5，解决小程序跨端兼容性问题
  const payload = encodeURIComponent(JSON.stringify({ type, data, ts: Date.now() }))
  const token = uni.getStorageSync('token')
  live2dUrl.value = `${live2dBaseUrl}?token=${encodeURIComponent(token)}&sessionId=${sessionId.value}#msg=${payload}`
}

// 处理来自 H5 的消息
const onWebviewMessage = (e) => {
  const payload = e.detail.data[0] || e.detail.data
  if (payload.type === 'USER_SEND') {
    sendMessage(payload.content || payload.data)
  }
}

// SSE 聊天逻辑 (保留在 UniApp 侧)
const sendMessage = async (text) => {
  if (!text || !sessionId.value) return

  postToH5('AI_START')

  createSSERequest(
    '/ai/chat/send',
    { sessionId: sessionId.value, message: text },
    {
      onMessage: async (_type, raw) => {
        try {
          const payload = JSON.parse(raw)
          if (payload.type === 'token') {
            postToH5('AI_TOKEN', payload.content || '')
          } else if (payload.type === 'complete') {
            // 完成事件，可用于获取 ttsUrl
            if (payload.ttsUrl) {
              currentTtsUrl.value = payload.ttsUrl
            }
          } else if (payload.type === 'error') {
            postToH5('AI_TOKEN', payload.content || '服务暂时不可用')
          }
        } catch (e) {
          // 非 JSON 数据，当作纯文本 token 处理
          postToH5('AI_TOKEN', raw)
        }
      },
      onComplete: () => {
        postToH5('AI_COMPLETE')
      }
    }
  )
}

// 同步 TtsPlayer 的口型事件到 H5
onMounted(() => {
  uni.$on('LIVE2D_POST_MESSAGE', (payload) => {
    postToH5(payload.type, payload.data)
  })
})

onUnmounted(() => {
  uni.$off('LIVE2D_POST_MESSAGE')
})

const initChat = async () => {
  try {
    const res = await createSession('TRIAGE')
    sessionId.value = res.data?.sessionId || res.id
    
    // 初始化时将 token 和 sessionId 传给 H5
    const token = uni.getStorageSync('token')
    live2dUrl.value = `${live2dBaseUrl}?token=${encodeURIComponent(token)}&sessionId=${sessionId.value}&t=${Date.now()}`
  } catch (e) {
    console.error('Session init failed', e)
  }
}

onLoad(() => {
  initChat()
})
</script>

<style scoped>
.chat-bridge {
  width: 100vw;
  height: 100vh;
}
.full-webview {
  width: 100%;
  height: 100%;
}
</style>
