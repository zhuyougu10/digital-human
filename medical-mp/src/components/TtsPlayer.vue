<template>
  <view class="tts-player" @click="togglePlay">
    <text class="icon">🔊</text>
    <text class="state">{{ statusText }}</text>
  </view>
</template>

<script setup lang="ts">
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

const isAbsoluteUrl = (url: string) => /^https?:\/\//.test(url)

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

const destroyAudio = () => {
  if (!audioContext) return
  try {
    audioContext.stop()
    audioContext.destroy()
  } catch (_) { /* ignore */ }
  audioContext = null
}

const createFreshAudio = () => {
  destroyAudio()

  // 尝试使用 useWebAudioImplement（基础库 3.4.7+）
  try {
    audioContext = uni.createInnerAudioContext({ useWebAudioImplement: true } as any)
  } catch (_) {
    audioContext = uni.createInnerAudioContext()
  }

  audioContext.obeyMuteSwitch = false

  audioContext.onPlay(() => {
    console.log('[TtsPlayer] onPlay 触发')
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
    console.log('[TtsPlayer] onEnded 触发')
    isPlaying.value = false
    postLive2dMessage('STOP_LIPSYNC')
    emitPlaybackEnded()
    emit('ended')
  })
  audioContext.onError((error: any) => {
    isPlaying.value = false
    postLive2dMessage('STOP_LIPSYNC')
    emitPlaybackEnded()
    console.error('[TtsPlayer] 音频播放失败:', {
      errCode: error?.errCode,
      errMsg: error?.errMsg,
      src: audioContext?.src,
      ttsUrl: props.ttsUrl
    })
    emit('error')
  })

  return audioContext
}

const playAudio = () => {
  if (!props.ttsUrl) return
  if (!isAbsoluteUrl(props.ttsUrl)) {
    console.error('[TtsPlayer] TTS URL 不是绝对地址:', props.ttsUrl)
    emitPlaybackEnded()
    emit('error')
    return
  }

  console.log('[TtsPlayer] 开始下载音频:', props.ttsUrl)

  // 先下载到本地临时文件，再用本地路径播放（解决远程URL播放无声的已知问题）
  uni.downloadFile({
    url: props.ttsUrl,
    success: (res) => {
      if (res.statusCode !== 200 || !res.tempFilePath) {
        console.error('[TtsPlayer] 下载失败, statusCode:', res.statusCode)
        emitPlaybackEnded()
        emit('error')
        return
      }

      console.log('[TtsPlayer] 下载成功, 播放本地文件:', res.tempFilePath)
      const ctx = createFreshAudio()
      ctx.src = res.tempFilePath
      ctx.onCanplay(() => {
        console.log('[TtsPlayer] onCanplay, 开始播放')
        ctx.play()
      })
    },
    fail: (err) => {
      console.error('[TtsPlayer] 下载音频失败:', err)
      emitPlaybackEnded()
      emit('error')
    }
  })
}

const stopAudio = () => {
  if (!audioContext) return
  audioContext.pause()
}

const togglePlay = () => {
  if (!props.ttsUrl) return
  if (!audioContext) {
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
  destroyAudio()
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
