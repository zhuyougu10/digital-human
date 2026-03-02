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
  margin-bottom: 30rpx;
  padding: 0 20rpx;
}

.message-user {
  justify-content: flex-end;
}

.message-ai {
  justify-content: flex-start;
}

.avatar {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  background-color: #eee;
}

.content-wrapper {
  max-width: 70%;
  margin: 0 20rpx;
}

.bubble {
  padding: 20rpx 30rpx;
  border-radius: 20rpx;
  font-size: 30rpx;
  line-height: 1.5;
  word-break: break-all;
}

.message-ai .bubble {
  background-color: white;
  color: #333;
  border-top-left-radius: 4rpx;
  box-shadow: 0 4rpx 10rpx rgba(0,0,0,0.05);
}

.message-user .bubble {
  background-color: #4A90D9;
  color: white;
  border-top-right-radius: 4rpx;
}

.special-card {
  width: 100%;
}
</style>
