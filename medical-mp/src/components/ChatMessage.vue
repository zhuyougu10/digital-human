<script setup>
const props = defineProps({
  message: {
    type: Object,
    required: true,
    default: () => ({
      role: 'assistant',
      content: '',
      type: 'text',
      metadata: null
    })
  }
})
</script>

<template>
  <view :class="['message-item', message.role === 'user' ? 'message-user' : 'message-ai']">
    <!-- AI 头像保持固定，用户头像从 message.avatar（或默认头像）获取 -->
    <image v-if="message.role === 'assistant'" class="avatar" :src="'/static/ai-avatar.png'" mode="aspectFill" />
    <view class="content-wrapper">
      <view v-if="message.type === 'text'" class="bubble">
        <text selectable>{{ message.content }}</text>
      </view>
      <!-- Special message types slot -->
      <view v-else class="special-card">
        <slot name="special" :message="message"></slot>
      </view>
    </view>
    <image v-if="message.role === 'user'" class="avatar" :src="message.avatar || '/static/logo.png'" mode="aspectFill" />
  </view>
</template>

<style scoped>
.message-item {
  display: flex;
  margin-bottom: 28rpx;
  padding: 0 24rpx;
  align-items: flex-start;
}

.message-user {
  justify-content: flex-end;
}

.message-ai {
  justify-content: flex-start;
}

.avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 36rpx;
  background-color: var(--bg-muted);
  flex-shrink: 0;
}

.content-wrapper {
  max-width: 72%;
  margin: 0 20rpx;
}

.bubble {
  padding: 24rpx 32rpx;
  border-radius: 26rpx;
  font-size: 28rpx;
  line-height: 1.6;
  word-break: break-all;
}

.message-ai .bubble {
  background-color: var(--bg-card);
  color: var(--text-main);
  border-top-left-radius: 8rpx;
  box-shadow: var(--shadow-sm);
  border: 1rpx solid rgba(226, 232, 240, 0.9);
}

.message-user .bubble {
  background: linear-gradient(135deg, var(--brand-primary) 0%, var(--brand-secondary) 100%);
  color: #ffffff;
  border-top-right-radius: 8rpx;
  box-shadow: 0 10rpx 24rpx rgba(37, 99, 235, 0.18);
}

.special-card {
  width: 100%;
}
</style>
