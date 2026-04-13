<template>
  <view class="tts-player" @click="togglePlay">
    <text class="icon">🔊</text>
    <text class="state">{{ statusText }}</text>
  </view>
</template>

<script setup lang="ts">
// [Codex 降级接管] TTS 播放组件在 Gemini 不可用时由 Codex 接管实现。
import { computed, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps<{
  ttsUrl: string
}>()

const emit = defineEmits<{
  play: []
  pause: []
  stop: []
  ended: []
  error: []
}>()

const isPlaying = ref(false)
let audioContext: UniApp.InnerAudioContext | null = null

const statusText = computed(() => (isPlaying.value ? '播放中，点击暂停' : '点击播放语音'))

const postLive2dMessage = (type: 'START_LIPSYNC' | 'STOP_LIPSYNC') => {
  const payload = { type }
  uni.$emit('LIVE2D_POST_MESSAGE', payload)
  if (typeof window !== 'undefined' && typeof window.postMessage === 'function') {
    window.postMessage(payload)
  }
}

const emitPlaybackEnded = () => {
  uni.$emit('TTS_PLAY_ENDED')
}

const bindAudioEvents = () => {
  if (!audioContext) return
  audioContext.onPlay(() => {
    isPlaying.value = true
    postLive2dMessage('START_LIPSYNC')
    emit('play')
  })
  audioContext.onPause(() => {
    isPlaying.value = false
    postLive2dMessage('STOP_LIPSYNC')
    emit('pause')
  })
  audioContext.onStop(() => {
    isPlaying.value = false
    postLive2dMessage('STOP_LIPSYNC')
    emitPlaybackEnded()
    emit('stop')
  })
  audioContext.onEnded(() => {
    isPlaying.value = false
    postLive2dMessage('STOP_LIPSYNC')
    emitPlaybackEnded()
    emit('ended')
  })
  audioContext.onError(() => {
    isPlaying.value = false
    postLive2dMessage('STOP_LIPSYNC')
    emitPlaybackEnded()
    emit('error')
    uni.showToast({ title: '语音播放失败', icon: 'none' })
  })
}

const ensureAudioContext = () => {
  if (audioContext) return audioContext
  audioContext = uni.createInnerAudioContext()
  bindAudioEvents()
  return audioContext
}

const playAudio = () => {
  if (!props.ttsUrl) return
  const ctx = ensureAudioContext()
  ctx.src = props.ttsUrl
  ctx.play()
}

const stopAudio = () => {
  if (!audioContext) return
  audioContext.pause()
}

const togglePlay = () => {
  if (!props.ttsUrl) return
  if (!audioContext || audioContext.src !== props.ttsUrl) {
    playAudio()
    return
  }
  if (isPlaying.value) {
    stopAudio()
  } else {
    audioContext.play()
  }
}

watch(
  () => props.ttsUrl,
  (newUrl) => {
    if (!newUrl) {
      stopAudio()
      return
    }
    playAudio()
  }
)

onBeforeUnmount(() => {
  if (!audioContext) return
  audioContext.stop()
  audioContext.destroy()
  audioContext = null
})
</script>

<style scoped>
.tts-player {
  display: inline-flex;
  align-items: center;
  gap: 10rpx;
  padding: 10rpx 16rpx;
  border-radius: 999rpx;
  background: #ecf5ff;
  color: #4a90d9;
}

.icon {
  font-size: 30rpx;
}

.state {
  font-size: 24rpx;
}
</style>
