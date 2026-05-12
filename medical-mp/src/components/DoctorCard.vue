<template>
  <view class="doctor-card" @click="onSelect">
    <!-- 左侧：头像区域 -->
    <view class="avatar-section">
      <image class="avatar" :src="doctor.avatar || defaultAvatar" mode="aspectFill" />
    </view>

    <!-- 中间：信息区域 -->
    <view class="info-section">
      <view class="name-row">
        <text class="name">{{ doctor.name }}</text>
        <text class="title">{{ doctor.title }}</text>
      </view>
      <text class="dept">{{ doctor.department }}</text>
      <view class="specialties">
        <text
          v-for="(item, index) in (doctor.specialties || []).slice(0, 2)"
          :key="index"
          class="tag"
        >
          {{ item }}
        </text>
      </view>
    </view>

    <!-- 右侧：操作区域 -->
    <view class="action-section">
      <view class="book-btn">预约</view>
    </view>
  </view>
</template>

<script setup lang="ts">
// [Codex 降级接管] 特殊消息卡片组件在 Gemini 不可用时由 Codex 接管实现。
interface Doctor {
  id: number | string
  name: string
  title: string
  avatar?: string
  specialties: string[]
  department: string
}

const props = defineProps<{
  doctor: Doctor
}>()

const emit = defineEmits<{
  (e: 'select', doctor: Doctor): void
}>()

const defaultAvatar = '/static/logo.png'

const onSelect = () => {
  emit('select', props.doctor)
}
</script>

<style scoped>
.doctor-card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  padding: 28rpx 24rpx;
  display: flex;
  align-items: center;
  gap: 24rpx;
  box-shadow: var(--shadow-sm);
  margin-bottom: 24rpx;
  border: 1rpx solid rgba(226, 232, 240, 0.84);
  font-family: "PingFang SC", "Hiragino Sans GB", "Noto Sans SC", "Microsoft YaHei", sans-serif;
}

.avatar-section {
  flex-shrink: 0;
}

.avatar {
  width: 120rpx;
  height: 120rpx;
  border-radius: 60rpx;
  background: var(--brand-primary-soft);
  border: 4rpx solid rgba(219, 234, 254, 0.8);
}

.info-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  min-width: 0;
}

.name-row {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
}

.name {
  font-size: 32rpx;
  color: var(--text-main);
  font-weight: 700;
}

.title {
  font-size: 24rpx;
  color: var(--brand-primary);
  background: var(--brand-primary-soft);
  padding: 2rpx 12rpx;
  border-radius: 9999rpx;
}

.dept {
  font-size: 26rpx;
  color: var(--text-subtle);
}

.specialties {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 4rpx;
}

.tag {
  padding: 4rpx 16rpx;
  border-radius: 24rpx;
  background: var(--bg-muted);
  color: var(--text-subtle);
  font-size: 22rpx;
  white-space: nowrap;
}

.action-section {
  flex-shrink: 0;
}

.book-btn {
  background: linear-gradient(135deg, var(--brand-primary) 0%, var(--brand-secondary) 100%);
  color: #ffffff;
  font-size: 26rpx;
  font-weight: 600;
  padding: 12rpx 32rpx;
  border-radius: 9999rpx;
  box-shadow: 0 8rpx 20rpx rgba(37, 99, 235, 0.18);
}

.book-btn:active {
  opacity: 0.8;
  transform: scale(0.95);
}
</style>
