<template>
  <view class="page">
    <view
      v-for="item in appointments"
      :key="item.id"
      class="appointment-card"
      @click="goDetail(item.id)"
    >
      <view class="top-row">
        <text class="doctor">{{ item.doctorName }}</text>
        <text class="status" :class="statusClass(item.status)">{{ statusText(item.status) }}</text>
      </view>
      <text class="meta">科室：{{ item.department }}</text>
      <text class="meta">时间：{{ item.date }} {{ item.time }}</text>
    </view>
    <view v-if="!appointments.length" class="empty">暂无预约记录</view>
  </view>
</template>

<script setup lang="ts">
// [Codex 降级接管] 预约列表页在 Gemini 不可用时由 Codex 接管实现。
import { onMounted, ref } from 'vue'
import request from '@/api/request'

interface Appointment {
  id: string | number
  doctorName: string
  department: string
  date: string
  time: string
  status: string
}

const appointments = ref<Appointment[]>([])

const statusText = (status: string) => {
  const map: Record<string, string> = {
    PENDING: '待就诊',
    COMPLETED: '已完成',
    CANCELED: '已取消',
    pending: '待就诊',
    completed: '已完成',
    canceled: '已取消'
  }
  return map[status] || status || '待就诊'
}

const statusClass = (status: string) => {
  if (['COMPLETED', 'completed'].includes(status)) return 'done'
  if (['CANCELED', 'canceled'].includes(status)) return 'cancel'
  return 'pending'
}

const goDetail = (id: string | number) => {
  uni.navigateTo({ url: `/pages/appointment/detail?id=${id}` })
}

const unwrapList = (payload: any): any[] => {
  if (Array.isArray(payload)) return payload
  if (Array.isArray(payload?.data)) return payload.data
  if (Array.isArray(payload?.data?.records)) return payload.data.records
  if (Array.isArray(payload?.records)) return payload.records
  if (Array.isArray(payload?.list)) return payload.list
  return []
}

const fetchAppointments = async () => {
  try {
    const res = await request({
      url: '/appointment/appointment/my',
      method: 'GET',
      data: {}
    })
    const list = unwrapList(res)
    appointments.value = list.map((item) => ({
      id: item.id,
      doctorName: item.doctorName || item.doctor?.name || '未知医生',
      department: item.department || item.departmentName || '',
      date: item.date || item.appointmentDate || '',
      time: item.time || item.appointmentTime || '',
      status: item.status || 'PENDING'
    }))
  } catch {
    uni.showToast({ title: '预约记录加载失败', icon: 'none' })
  }
}

onMounted(fetchAppointments)
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f5f7fa;
  padding: 20rpx;
  box-sizing: border-box;
}

.appointment-card {
  background: #ffffff;
  border-radius: 12rpx;
  padding: 20rpx;
  margin-bottom: 16rpx;
}

.top-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.doctor {
  font-size: 32rpx;
  color: #303133;
  font-weight: 700;
}

.status {
  font-size: 24rpx;
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
}

.status.pending {
  color: #e6a23c;
  background: #fdf6ec;
}

.status.done {
  color: #67c23a;
  background: #f0f9eb;
}

.status.cancel {
  color: #f56c6c;
  background: #fef0f0;
}

.meta {
  display: block;
  margin-top: 10rpx;
  color: #606266;
  font-size: 24rpx;
}

.empty {
  text-align: center;
  color: #909399;
  margin-top: 100rpx;
}
</style>
