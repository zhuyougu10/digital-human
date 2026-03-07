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

// Message Bridge: UniApp -> H5
const postToH5 = (type, data) => {
  // In UniApp web-view, we use evalJS or just trust H5's message listener if platform is H5
  // For cross-platform, we use the standard webview context
  const webview = plus.webview.currentWebview().children()[0];
  if (webview) {
    webview.evalJS(`window.dispatchEvent(new MessageEvent('message', {data: ${JSON.stringify({type, data})}}))`);
  } else {
    // If running in browser/H5 platform directly
    window.postMessage({ type, data }, '*')
  }
}

// Handle messages from H5 -> UniApp
const onWebviewMessage = (e) => {
  const payload = e.detail.data[0] || e.detail.data
  if (payload.type === 'USER_SEND') {
    sendMessage(payload.content || payload.data)
  }
}

// SSE Chat Logic
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
          if (payload.type === 'text') {
            postToH5('AI_TOKEN', payload.content || '')
          } else if (payload.type === 'tts') {
            currentTtsUrl.value = payload.metadata || payload.url || ''
          }
        } catch (e) {
          // Fallback if not JSON
          postToH5('AI_TOKEN', raw)
        }
      },
      onComplete: () => {
        postToH5('AI_COMPLETE')
      }
    }
  )
}

// Sync lip-sync events from TtsPlayer to H5
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
