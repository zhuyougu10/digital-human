<template>
  <view class="appointment-card">
    <view class="status-row">
      <text class="success-icon">✔</text>
      <text class="status-text">预约成功</text>
    </view>

    <view class="info-row"><text class="label">医生</text><text class="value">{{ appointment.doctorName }}</text></view>
    <view class="info-row"><text class="label">科室</text><text class="value">{{ appointment.department }}</text></view>
    <view class="info-row"><text class="label">就诊时间</text><text class="value">{{ appointment.date }} {{ appointment.time }}</text></view>
    <view class="info-row"><text class="label">排队号</text><text class="value">{{ appointment.queueNumber }}</text></view>
    <view class="info-row"><text class="label">状态</text><text class="value">{{ appointment.status }}</text></view>

    <button class="detail-btn" @click="goDetail">查看预约详情</button>
  </view>
</template>

<script setup lang="ts">
// [Codex 降级接管] 特殊消息卡片组件在 Gemini 不可用时由 Codex 接管实现。
interface Appointment {
  id: number | string
  doctorName: string
  department: string
  date: string
  time: string
  queueNumber: string | number
  status: string
}

const props = defineProps<{
  appointment: Appointment
}>()

const goDetail = () => {
  uni.navigateTo({
    url: `/pages/appointment/detail?id=${props.appointment.id}`
  })
}
</script>

<style scoped>
.appointment-card {
  background: #ffffff;
  border-radius: 12rpx;
  padding: 22rpx;
  box-shadow: 0 6rpx 18rpx rgba(103, 194, 58, 0.14);
}

.status-row {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-bottom: 14rpx;
}

.success-icon {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  color: #ffffff;
  background: #67c23a;
  display: inline-flex;
  justify-content: center;
  align-items: center;
  font-size: 24rpx;
}

.status-text {
  color: #67c23a;
  font-size: 28rpx;
  font-weight: 700;
}

.info-row {
  display: flex;
  justify-content: space-between;
  font-size: 25rpx;
  padding: 8rpx 0;
  border-bottom: 1rpx solid #f2f6fc;
}

.label {
  color: #909399;
}

.value {
  color: #303133;
  max-width: 68%;
  text-align: right;
}

.detail-btn {
  margin-top: 20rpx;
  color: #4a90d9;
  background: #ecf5ff;
  border: 0;
  border-radius: 10rpx;
  font-size: 26rpx;
}
</style>
