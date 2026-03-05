<template>
  <view class="page" v-if="appointment">
    <view class="card">
      <view class="row"><text class="label">医生</text><text class="value">{{ appointment.doctorName }}</text></view>
      <view class="row"><text class="label">科室</text><text class="value">{{ appointment.department }}</text></view>
      <view class="row"><text class="label">预约时间</text><text class="value">{{ appointment.date }} {{ appointment.time }}</text></view>
      <view class="row"><text class="label">排队号</text><text class="value">{{ appointment.queueNumber }}</text></view>
      <view class="row"><text class="label">状态</text><text class="value">{{ statusText(appointment.status) }}</text></view>
    </view>

    <button
      v-if="isPending(appointment.status)"
      class="cancel-btn"
      @click="cancelAppointment"
    >
      取消预约
    </button>
  </view>
</template>

<script setup lang="ts">
// [Codex 降级接管] 预约详情页在 Gemini 不可用时由 Codex 接管实现。
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getAppointmentById, cancelAppointment as cancelApi } from '@/api/appointment'

interface AppointmentDetail {
  id: string | number
  doctorName: string
  department: string
  date: string
  time: string
  queueNumber: string | number
  status: string
}

const appointmentId = ref('')
const appointment = ref<AppointmentDetail | null>(null)

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

const isPending = (status: string) => ['PENDING', 'pending'].includes(status)

const fetchDetail = async () => {
  if (!appointmentId.value) return
  try {
    const res = await getAppointmentById(appointmentId.value)
    const data = (res as any)?.data || res
    appointment.value = {
      id: data.id,
      doctorName: data.doctorName || data.doctor?.name || '未知医生',
      department: data.department || data.departmentName || '',
      date: data.date || data.appointmentDate || '',
      time: data.time || data.appointmentTime || '',
      queueNumber: data.queueNumber || '-',
      status: data.status || 'PENDING'
    }
  } catch {
    uni.showToast({ title: '详情加载失败', icon: 'none' })
  }
}

const cancelAppointment = async () => {
  if (!appointmentId.value) return
  try {
    await cancelApi(appointmentId.value)
  } catch {
    uni.showToast({ title: '取消预约失败', icon: 'none' })
    return
  }
  uni.showToast({ title: '已取消预约', icon: 'success' })
  fetchDetail()
}

onLoad((options) => {
  appointmentId.value = String(options?.id || '')
  fetchDetail()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f5f7fa;
  padding: 24rpx;
  box-sizing: border-box;
}

.card {
  background: #ffffff;
  border-radius: 12rpx;
  padding: 22rpx;
}

.row {
  display: flex;
  justify-content: space-between;
  padding: 14rpx 0;
  border-bottom: 1rpx solid #f2f6fc;
}

.row:last-child {
  border-bottom: 0;
}

.label {
  color: #909399;
  font-size: 26rpx;
}

.value {
  color: #303133;
  font-size: 26rpx;
}

.cancel-btn {
  margin-top: 28rpx;
  border: 0;
  border-radius: 10rpx;
  color: #ffffff;
  background: #f56c6c;
}
</style>
