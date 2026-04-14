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
  background: #ffffff;
  border-radius: 24rpx;
  padding: 28rpx 24rpx;
  display: flex;
  align-items: center;
  gap: 24rpx;
  box-shadow: 0 8rpx 24rpx rgba(46, 126, 167, 0.06);
  margin-bottom: 24rpx;
  border: 1rpx solid rgba(226, 232, 240, 0.6);
  font-family: "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
}

.avatar-section {
  flex-shrink: 0;
}

.avatar {
  width: 120rpx;
  height: 120rpx;
  border-radius: 60rpx;
  background: #f8faff;
  border: 4rpx solid #f0f4ff;
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
  color: #1e293b;
  font-weight: 700;
}

.title {
  font-size: 24rpx;
  color: #2e7ea7;
  background: rgba(46, 126, 167, 0.08);
  padding: 2rpx 12rpx;
  border-radius: 6rpx;
}

.dept {
  font-size: 26rpx;
  color: #64748b;
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
  background: #f1f5f9;
  color: #64748b;
  font-size: 22rpx;
  white-space: nowrap;
}

.action-section {
  flex-shrink: 0;
}

.book-btn {
  background: #2e7ea7;
  color: #ffffff;
  font-size: 26rpx;
  font-weight: 600;
  padding: 12rpx 32rpx;
  border-radius: 30rpx;
  box-shadow: 0 4rpx 12rpx rgba(46, 126, 167, 0.2);
}

.book-btn:active {
  opacity: 0.8;
  transform: scale(0.95);
}
</style>
