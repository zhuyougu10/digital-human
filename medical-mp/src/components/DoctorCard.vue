<template>
  <view class="doctor-card">
    <view class="header">
      <image class="avatar" :src="doctor.avatar || defaultAvatar" mode="aspectFill" />
      <view class="meta">
        <text class="name">{{ doctor.name }}</text>
        <text class="title">{{ doctor.title }}</text>
        <text class="department">{{ doctor.department }}</text>
      </view>
    </view>

    <view class="specialties">
      <text
        v-for="item in doctor.specialties"
        :key="item"
        class="tag"
      >
        {{ item }}
      </text>
    </view>

    <button class="select-btn" @click="onSelect">选择此医生</button>
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
  border-radius: 12rpx;
  box-shadow: 0 6rpx 18rpx rgba(74, 144, 217, 0.15);
  padding: 24rpx;
}

.header {
  display: flex;
  gap: 20rpx;
}

.avatar {
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  background: #f5f7fa;
}

.meta {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.name {
  font-size: 34rpx;
  color: #303133;
  font-weight: 700;
}

.title,
.department {
  font-size: 26rpx;
  color: #606266;
}

.specialties {
  margin-top: 20rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.tag {
  padding: 8rpx 16rpx;
  border-radius: 999rpx;
  background: #ecf5ff;
  color: #4a90d9;
  font-size: 24rpx;
}

.select-btn {
  margin-top: 24rpx;
  background: #4a90d9;
  border: 0;
  border-radius: 10rpx;
  font-size: 28rpx;
}
</style>
