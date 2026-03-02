<template>
  <view class="chat-page">
    <view class="live2d-zone">
      <view class="live2d-mask">
        <text class="live2d-title">AI 数字人医生</text>
        <text class="live2d-subtitle">正在为您服务...</text>
      </view>
    </view>

    <scroll-view
      class="message-scroll"
      scroll-y
      :scroll-into-view="scrollIntoView"
      :scroll-with-animation="true"
    >
      <view class="message-list">
        <ChatMessage
          v-for="message in messages"
          :key="message.id"
          :id="`msg-${message.id}`"
          :message="message"
        >
          <template #special="{ message: specialMessage }">
            <view v-if="specialMessage.type === 'doctor_recommend'" class="special-wrapper">
              <DoctorCard
                v-for="doctor in normalizeDoctorList(specialMessage.metadata)"
                :key="doctor.id"
                :doctor="doctor"
                @select="handleDoctorSelect"
              />
            </view>

            <view v-else-if="specialMessage.type === 'slot_picker'" class="special-wrapper">
              <SlotPicker
                :slots="normalizeSlotList(specialMessage.metadata)"
                @select="handleSlotSelect"
              />
            </view>

            <view v-else-if="specialMessage.type === 'appointment_result'" class="special-wrapper">
              <AppointmentCard :appointment="normalizeAppointment(specialMessage.metadata)" />
            </view>

            <view v-else-if="specialMessage.type === 'tts'" class="special-wrapper">
              <TtsPlayer :tts-url="extractTtsUrl(specialMessage)" />
            </view>

            <view v-else class="unknown-card">
              <text>收到未识别消息类型</text>
            </view>
          </template>
        </ChatMessage>

        <view :id="bottomAnchorId" class="bottom-anchor"></view>
      </view>
    </scroll-view>

    <view class="input-bar">
      <input
        v-model="inputText"
        class="chat-input"
        placeholder="请输入您的症状或需求"
        confirm-type="send"
        @confirm="handleSend"
      />
      <button class="send-btn" :disabled="sending" @click="handleSend">
        {{ sending ? '发送中' : '发送' }}
      </button>
    </view>
  </view>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { createSession } from '@/api/chat'
import { createSSERequest } from '@/utils/sse'
import ChatMessage from '@/components/ChatMessage.vue'
import DoctorCard from '@/components/DoctorCard.vue'
import SlotPicker from '@/components/SlotPicker.vue'
import AppointmentCard from '@/components/AppointmentCard.vue'
import TtsPlayer from '@/components/TtsPlayer.vue'

const sessionId = ref('')
const inputText = ref('')
const sending = ref(false)
const scrollIntoView = ref('')
const bottomAnchorId = 'chat-bottom-anchor'

const messages = ref([])
let currentAiMessageId = ''
let currentRequestTask = null

const buildMessage = (data = {}) => ({
  id: String(Date.now() + Math.floor(Math.random() * 1000)),
  role: 'assistant',
  content: '',
  type: 'text',
  metadata: null,
  loading: false,
  ...data
})

const scrollToBottom = async () => {
  await nextTick()
  scrollIntoView.value = ''
  await nextTick()
  scrollIntoView.value = bottomAnchorId
}

const appendMessage = async (message) => {
  messages.value.push(message)
  await scrollToBottom()
}

const appendAssistantToken = async (token) => {
  if (!token) return

  let target = messages.value.find((item) => item.id === currentAiMessageId)

  if (!target || target.type !== 'text' || target.role !== 'assistant') {
    const aiMessage = buildMessage({
      role: 'assistant',
      content: '',
      type: 'text',
      loading: true
    })
    currentAiMessageId = aiMessage.id
    messages.value.push(aiMessage)
    target = aiMessage
  }

  target.content += token
  await scrollToBottom()
}

const setCurrentAssistantFinished = () => {
  const target = messages.value.find((item) => item.id === currentAiMessageId)
  if (target) {
    target.loading = false
  }
  currentAiMessageId = ''
}

const normalizeDoctorList = (meta) => {
  if (Array.isArray(meta)) return meta
  if (Array.isArray(meta?.doctors)) return meta.doctors
  if (Array.isArray(meta?.list)) return meta.list
  return []
}

const normalizeSlotList = (meta) => {
  if (Array.isArray(meta)) return meta
  if (Array.isArray(meta?.slots)) return meta.slots
  if (Array.isArray(meta?.list)) return meta.list
  return []
}

const normalizeAppointment = (meta) => {
  return {
    id: meta?.id || meta?.appointmentId || '',
    doctorName: meta?.doctorName || meta?.doctor || '未分配医生',
    department: meta?.department || '未知科室',
    date: meta?.date || '',
    time: meta?.time || '',
    queueNumber: meta?.queueNumber || '-',
    status: meta?.status || '待就诊'
  }
}

const extractTtsUrl = (message) => {
  if (typeof message.metadata === 'string') return message.metadata
  return message.metadata?.ttsUrl || message.metadata?.url || message.content || ''
}

const parsePayload = (raw) => {
  if (typeof raw !== 'string') return raw
  try {
    return JSON.parse(raw)
  } catch {
    return raw
  }
}

const handleStructuredMessage = async (payload) => {
  const type = payload?.type

  if (type === 'doctor_recommend') {
    setCurrentAssistantFinished()
    await appendMessage(
      buildMessage({
        role: 'assistant',
        type: 'doctor_recommend',
        metadata: payload.metadata || payload.data || payload.doctors || []
      })
    )
    return true
  }

  if (type === 'slot_picker') {
    setCurrentAssistantFinished()
    await appendMessage(
      buildMessage({
        role: 'assistant',
        type: 'slot_picker',
        metadata: payload.metadata || payload.data || payload.slots || []
      })
    )
    return true
  }

  if (type === 'appointment_result') {
    setCurrentAssistantFinished()
    await appendMessage(
      buildMessage({
        role: 'assistant',
        type: 'appointment_result',
        metadata: payload.metadata || payload.data || payload
      })
    )
    return true
  }

  if (type === 'tts') {
    await appendMessage(
      buildMessage({
        role: 'assistant',
        type: 'tts',
        content: payload.content || '',
        metadata: payload.metadata || payload.url || payload.ttsUrl || ''
      })
    )
    return true
  }

  if (type === 'text') {
    const token = payload.content || payload.text || payload.token || ''
    await appendAssistantToken(token)
    return true
  }

  return false
}

const sendMessage = async (content) => {
  const text = String(content || '').trim()
  if (!text || !sessionId.value || sending.value) return

  await appendMessage(
    buildMessage({
      role: 'user',
      content: text,
      type: 'text'
    })
  )

  inputText.value = ''
  sending.value = true

  currentRequestTask = createSSERequest(
    '/ai/chat/send',
    {
      sessionId: sessionId.value,
      message: text
    },
    {
      onMessage: async (_eventType, raw) => {
        const payload = parsePayload(raw)

        if (typeof payload === 'string') {
          await appendAssistantToken(payload)
          return
        }

        const isHandled = await handleStructuredMessage(payload)
        if (!isHandled) {
          const fallback = payload?.content || payload?.text || ''
          if (fallback) {
            await appendAssistantToken(fallback)
          }
        }
      },
      onComplete: async () => {
        setCurrentAssistantFinished()
        sending.value = false
        await scrollToBottom()
      },
      onError: async () => {
        setCurrentAssistantFinished()
        sending.value = false
        await appendMessage(
          buildMessage({
            role: 'assistant',
            content: '抱歉，当前网络异常，请稍后再试。',
            type: 'text'
          })
        )
      }
    }
  )
}

const handleSend = async () => {
  await sendMessage(inputText.value)
}

const handleDoctorSelect = async (doctor) => {
  await sendMessage(`我选择${doctor.name}医生`)
}

const handleSlotSelect = async (slot) => {
  await sendMessage(`我选择${slot.date} ${slot.time}的号`)
}

const initChat = async () => {
  try {
    const res = await createSession('TRIAGE')
    const data = res?.data || res
    sessionId.value = String(data?.sessionId || data?.id || '')

    if (!sessionId.value) {
      throw new Error('missing session id')
    }

    await sendMessage('你好，我需要看病')
  } catch {
    await appendMessage(
      buildMessage({
        role: 'assistant',
        content: '初始化会话失败，请返回首页后重试。',
        type: 'text'
      })
    )
  }
}

onLoad(() => {
  initChat()
})
</script>

<style scoped>
.chat-page {
  min-height: 100vh;
  height: 100vh;
  background: #f5f7fa;
  display: flex;
  flex-direction: column;
}

.live2d-zone {
  height: 40vh;
  min-height: 360rpx;
  background: linear-gradient(135deg, #4a90d9 0%, #357abd 100%);
  border-bottom-left-radius: 28rpx;
  border-bottom-right-radius: 28rpx;
  box-shadow: 0 10rpx 24rpx rgba(53, 122, 189, 0.28);
  display: flex;
  align-items: center;
  justify-content: center;
  padding-top: env(safe-area-inset-top);
}

.live2d-mask {
  text-align: center;
  color: #ffffff;
}

.live2d-title {
  display: block;
  font-size: 46rpx;
  font-weight: 700;
  letter-spacing: 2rpx;
}

.live2d-subtitle {
  display: block;
  margin-top: 12rpx;
  font-size: 26rpx;
  opacity: 0.95;
}

.message-scroll {
  flex: 1;
  min-height: 0;
  padding: 24rpx 0 170rpx;
  box-sizing: border-box;
}

.message-list {
  padding: 0 6rpx;
}

.special-wrapper {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.unknown-card {
  background: #ffffff;
  border-radius: 12rpx;
  padding: 20rpx;
  color: #909399;
  font-size: 24rpx;
}

.bottom-anchor {
  width: 2rpx;
  height: 2rpx;
}

.input-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 14rpx;
  padding: 16rpx 20rpx calc(16rpx + env(safe-area-inset-bottom));
  background: #ffffff;
  border-top: 1rpx solid #e4e7ed;
  box-shadow: 0 -4rpx 12rpx rgba(0, 0, 0, 0.04);
}

.chat-input {
  flex: 1;
  height: 72rpx;
  background: #f5f7fa;
  border-radius: 36rpx;
  padding: 0 24rpx;
  font-size: 28rpx;
  color: #303133;
}

.send-btn {
  width: 150rpx;
  height: 72rpx;
  line-height: 72rpx;
  border: none;
  border-radius: 36rpx;
  background: #4a90d9;
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 600;
  padding: 0;
}

.send-btn[disabled] {
  opacity: 0.6;
}
</style>
