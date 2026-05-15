<script setup>
import { computed } from 'vue'
import { parseMarkdown } from '../utils/markdown'
import { getRenderableSuggestedReplies } from '../utils/suggestion-helper.mjs'

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
  },
  isActiveSuggestionOwner: {
    type: Boolean,
    default: false
  },
  messageIndex: {
    type: Number,
    default: -1
  }
})

const emit = defineEmits(['suggestion-tap'])

const handleSuggestionTap = (reply) => {
  emit('suggestion-tap', reply, props.messageIndex)
}

const parsedContent = computed(() => {
  if (props.message.role === 'assistant' && props.message.type === 'text') {
    return parseMarkdown(props.message.content)
  }
  return props.message.content
})

const renderableSuggestedReplies = computed(() => {
  return getRenderableSuggestedReplies(props.message, props.isActiveSuggestionOwner)
})
</script>

<template>
  <view :class="['message-item', message.role === 'user' ? 'message-user' : 'message-ai']">
    <!-- AI 头像保持固定，用户头像从 message.avatar（或默认头像）获取 -->
    <image v-if="message.role === 'assistant'" class="avatar" :src="'/static/logo.png'" mode="aspectFill" />
    <view class="content-wrapper">
      <view v-if="message.type === 'text'" class="bubble">
        <text v-if="message.role === 'user'" selectable>{{ message.content }}</text>
        <rich-text v-else :nodes="parsedContent"></rich-text>
      </view>
      <!-- Suggestions list rendered under assistant text bubbles, not in special branch -->
      <view v-if="renderableSuggestedReplies.length > 0" class="suggestions-list">
        <view 
          v-for="(reply, idx) in renderableSuggestedReplies" 
          :key="idx" 
          class="suggestion-chip"
          @click="handleSuggestionTap(reply)"
        >
          <text>{{ reply }}</text>
        </view>
      </view>
      <!-- Special message types slot -->
      <view v-else-if="message.type !== 'text'" class="special-card">
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
  background-color: var(--bg-card, #ffffff);
  color: var(--text-main, #333333);
  border-top-left-radius: 8rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
  border: 1rpx solid rgba(226, 232, 240, 0.8);
  padding: 24rpx 32rpx;
  overflow: hidden;
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

.suggestions-list {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: 16rpx;
}

.suggestion-chip {
  padding: 12rpx 24rpx;
  background: var(--bg-muted, #f1f5f9);
  border-radius: 30rpx;
  border: 1rpx solid var(--border-color, #e2e8f0);
}

.suggestion-chip text {
  font-size: 24rpx;
  color: var(--brand-primary, #2563eb);
}
</style>
