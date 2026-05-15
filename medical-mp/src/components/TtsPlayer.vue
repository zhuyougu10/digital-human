<template>
  <view class="tts-player" @click="togglePlay">
    <text class="icon">🔊</text>
    <text class="state">{{ statusText }}</text>
  </view>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { buildAuthHeader } from '@/api/request'

declare const wx:
  | {
      env?: {
        USER_DATA_PATH?: string
      }
      downloadFile: (options: {
        url: string
        header?: Record<string, string>
        filePath?: string
        success?: (res: { statusCode: number; tempFilePath?: string; filePath?: string }) => void
        fail?: (err: unknown) => void
      }) => void
    }
  | undefined

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
let activePlaybackToken = 0

const statusText = computed(() => (isPlaying.value ? '播放中，点击暂停' : '点击播放语音'))

const isAbsoluteUrl = (url: string) => /^https?:\/\//.test(url)

const sanitizeAudioFileName = (url: string) => {
  const lastSegment = url.split('/').pop()?.split('?')[0] || ''
  const normalizedBaseName = lastSegment.replace(/\.[^.]+$/, '').replace(/[^a-zA-Z0-9_-]/g, '_')
  const safeBaseName = normalizedBaseName || `tts_${Date.now()}`
  return `${safeBaseName}.mp3`
}

const buildWechatAudioFilePath = (url: string) => {
  if (typeof wx === 'undefined' || !wx.env?.USER_DATA_PATH) {
    return ''
  }

  return `${wx.env.USER_DATA_PATH}/${sanitizeAudioFileName(url)}`
}

const isSamePlayback = (token: number) => token === activePlaybackToken

const buildDownloadHeader = () => buildAuthHeader()

const downloadAudioSource = (url: string, token: number) => {
  return new Promise<string>((resolve, reject) => {
    const onSuccess = (res: { statusCode: number; tempFilePath?: string; filePath?: string }) => {
      if (!isSamePlayback(token)) {
        resolve('')
        return
      }

      const localPath = res.filePath || res.tempFilePath || ''
      if (res.statusCode < 200 || res.statusCode >= 300 || !localPath) {
        reject(new Error(`[TtsPlayer] 下载失败, statusCode=${res.statusCode}`))
        return
      }

      resolve(localPath)
    }

    const onFail = (err: unknown) => {
      if (!isSamePlayback(token)) {
        resolve('')
        return
      }
      reject(err)
    }

    // #ifdef MP-WEIXIN
    const wechatApi = wx
    const filePath = buildWechatAudioFilePath(url)
    if (wechatApi?.downloadFile && filePath) {
      wechatApi.downloadFile({
        url,
        header: buildDownloadHeader(),
        filePath,
        success: onSuccess,
        fail: onFail
      })
      return
    }
    // #endif

    uni.downloadFile({
      url,
      header: buildDownloadHeader(),
      success: onSuccess,
      fail: onFail
    })
  })
}

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

const getAudioErrorDetail = (error: unknown, key: 'errCode' | 'errMsg') => {
  if (typeof error !== 'object' || error === null || !(key in error)) {
    return undefined
  }

  const value = (error as Record<'errCode' | 'errMsg', unknown>)[key]
  return typeof value === 'string' || typeof value === 'number' ? value : undefined
}

const destroyAudio = () => {
  activePlaybackToken++
  if (!audioContext) return
  try {
    audioContext.stop()
    audioContext.destroy()
  } catch (error) {
    console.warn('[TtsPlayer] 销毁音频上下文失败:', error)
  }
  audioContext = null
}

const createFreshAudio = () => {
  destroyAudio()

  const ctx = uni.createInnerAudioContext()
  audioContext = ctx

  ctx.obeyMuteSwitch = false

  ctx.onPlay(() => {
    console.log('[TtsPlayer] onPlay 触发')
    isPlaying.value = true
    postLive2dMessage('START_LIPSYNC')
    emit('play')
  })
  ctx.onPause(() => {
    isPlaying.value = false
    postLive2dMessage('STOP_LIPSYNC')
    emit('pause')
  })
  ctx.onStop(() => {
    isPlaying.value = false
    postLive2dMessage('STOP_LIPSYNC')
    emitPlaybackEnded()
    emit('stop')
  })
  ctx.onEnded(() => {
    console.log('[TtsPlayer] onEnded 触发')
    isPlaying.value = false
    postLive2dMessage('STOP_LIPSYNC')
    emitPlaybackEnded()
    emit('ended')
  })
  ctx.onError((error: Error | unknown) => {
    isPlaying.value = false
    postLive2dMessage('STOP_LIPSYNC')
    emitPlaybackEnded()
    console.error('[TtsPlayer] 音频播放失败:', {
      errCode: getAudioErrorDetail(error, 'errCode'),
      errMsg: getAudioErrorDetail(error, 'errMsg'),
      src: ctx.src,
      ttsUrl: props.ttsUrl
    })
    emit('error')
  })

  return ctx
}

const playAudio = async () => {
  if (!props.ttsUrl) return
  if (!isAbsoluteUrl(props.ttsUrl)) {
    console.error('[TtsPlayer] TTS URL 不是绝对地址:', props.ttsUrl)
    emitPlaybackEnded()
    emit('error')
    return
  }

  console.log('[TtsPlayer] 开始下载音频:', props.ttsUrl)

  const playbackToken = ++activePlaybackToken

  try {
    const localAudioPath = await downloadAudioSource(props.ttsUrl, playbackToken)
    if (!localAudioPath || !isSamePlayback(playbackToken)) {
      return
    }

    console.log('[TtsPlayer] 下载成功, 播放本地文件:', localAudioPath)
    const ctx = createFreshAudio()

    let playbackTriggered = false
    const triggerPlayback = (reason: 'canplay' | 'fallback') => {
      if (playbackTriggered || !isSamePlayback(playbackToken)) {
        return
      }
      playbackTriggered = true
      console.log(`[TtsPlayer] ${reason === 'canplay' ? 'onCanplay' : 'fallback'}, 开始播放`)
      ctx.play()
    }

    ctx.onCanplay(() => {
      triggerPlayback('canplay')
    })
    ctx.autoplay = true
    ctx.src = localAudioPath
    setTimeout(() => {
      triggerPlayback('fallback')
    }, 120)
  } catch (err) {
    if (!isSamePlayback(playbackToken)) {
      return
    }
    console.error('[TtsPlayer] 下载音频失败:', err)
    emitPlaybackEnded()
    emit('error')
  }
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
  },
  { immediate: true }
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
  border-radius: 9999rpx;
  background: var(--brand-primary-soft);
  color: var(--brand-primary);
  border: 1rpx solid rgba(37, 99, 235, 0.12);
}

.icon {
  font-size: 30rpx;
}

.state {
  font-size: 24rpx;
}
</style>
