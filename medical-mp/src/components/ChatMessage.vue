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
  margin-bottom: 32rpx;
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
  background-color: #f5f8fb;
  flex-shrink: 0;
}

.content-wrapper {
  max-width: 72%;
  margin: 0 20rpx;
}

.bubble {
  padding: 24rpx 32rpx;
  border-radius: 24rpx;
  font-size: 28rpx;
  line-height: 1.6;
  word-break: break-all;
}

.message-ai .bubble {
  background-color: #ffffff;
  color: #1f2d3d;
  border-top-left-radius: 8rpx;
  box-shadow: 0 4rpx 16rpx rgba(31, 79, 111, 0.04);
  border: 1rpx solid rgba(217, 231, 239, 0.6);
}

.message-user .bubble {
  background-color: #2e7ea7;
  color: #ffffff;
  border-top-right-radius: 8rpx;
  box-shadow: 0 4rpx 16rpx rgba(46, 126, 167, 0.16);
}

.special-card {
  width: 100%;
}
</style>
