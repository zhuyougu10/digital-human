<template>
  <view class="appointment-card">
    <view class="card-header">
      <view class="doctor-brief">
        <view class="avatar-placeholder">
          <text class="avatar-text">{{ appointment.doctorName.substring(0, 1) }}</text>
        </view>
        <view class="doctor-meta">
          <text class="doctor-name">{{ appointment.doctorName }}</text>
          <text class="department-name">{{ appointment.department }}</text>
        </view>
      </view>
      <view class="status-tag" :class="statusClass">
        {{ statusText }}
      </view>
    </view>

    <view class="card-content">
      <view class="info-item">
        <text class="info-label">就诊时间</text>
        <text class="info-value">{{ appointment.date }} {{ appointment.time }}</text>
      </view>
      <view class="info-item">
        <text class="info-label">排队序号</text>
        <text class="info-value highlight">{{ appointment.queueNumber }}号</text>
      </view>
    </view>

    <view class="card-footer">
      <button class="detail-btn" @click="goDetail">查看详情</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Appointment {
  id: number | string
  doctorName: string
  department: string
  date: string
  time: string
  queueNumber: string | number
  status: string | number
}

const props = defineProps<{
  appointment: Appointment
}>()

const statusText = computed(() => {
  const s = props.appointment.status
  if (s === 0 || s === 'PENDING' || s === '待就诊') return '待就诊'
  if (s === 1 || s === 'COMPLETED' || s === '已完成') return '已完成'
  if (s === 2 || s === 'CANCELED' || s === '已取消') return '已取消'
  return String(s)
})

const statusClass = computed(() => {
  const s = props.appointment.status
  if (s === 0 || s === 'PENDING' || s === '待就诊') return 'status-pending'
  if (s === 1 || s === 'COMPLETED' || s === '已完成') return 'status-completed'
  if (s === 2 || s === 'CANCELED' || s === '已取消') return 'status-canceled'
  return ''
})

const goDetail = () => {
  uni.navigateTo({
    url: `/pages/appointment/detail?id=${props.appointment.id}`
  })
}
</script>

<style scoped>
.appointment-card {
  background: #ffffff;
  border-radius: 24rpx;
  padding: 32rpx;
  box-shadow: 0 8rpx 24rpx rgba(37, 99, 235, 0.06);
  border: 1rpx solid rgba(37, 99, 235, 0.04);
  margin-bottom: 24rpx;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 28rpx;
}

.doctor-brief {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.avatar-placeholder {
  width: 80rpx;
  height: 80rpx;
  background: #eff6ff;
  border-radius: 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-text {
  color: #2563eb;
  font-size: 32rpx;
  font-weight: 600;
}

.doctor-meta {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.doctor-name {
  font-size: 32rpx;
  font-weight: 600;
  color: #1e293b;
}

.department-name {
  font-size: 24rpx;
  color: #64748b;
}

.status-tag {
  padding: 6rpx 16rpx;
  border-radius: 8rpx;
  font-size: 22rpx;
  font-weight: 500;
}

.status-pending {
  background: #fff7ed;
  color: #f59e0b;
}

.status-completed {
  background: #ecfdf5;
  color: #10b981;
}

.status-canceled {
  background: #f1f5f9;
  color: #64748b;
}

.card-content {
  background: #f8faff;
  border-radius: 16rpx;
  padding: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.info-label {
  font-size: 26rpx;
  color: #64748b;
}

.info-value {
  font-size: 26rpx;
  color: #1e293b;
  font-weight: 500;
}

.info-value.highlight {
  color: #2563eb;
  font-weight: 600;
}

.card-footer {
  margin-top: 28rpx;
}

.detail-btn {
  width: 100%;
  height: 80rpx;
  line-height: 80rpx;
  background: #ffffff;
  color: #2563eb;
  border: 2rpx solid #2563eb;
  border-radius: 40rpx;
  font-size: 28rpx;
  font-weight: 600;
}

.detail-btn:active {
  background: #f0f7ff;
}
</style>
