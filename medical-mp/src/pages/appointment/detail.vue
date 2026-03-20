<template>
  <view class="result-page">
    <view class="result-header" :class="{ success: isSuccess }">
      <view class="header-top">
        <uni-icons type="left" size="24" color="#FFFFFF" @click="goBack"></uni-icons>
        <text class="header-title">{{ isSuccess ? '预约结果' : '预约详情' }}</text>
        <view class="placeholder"></view>
      </view>
      <view class="status-info" v-if="isSuccess">
        <view class="success-icon">
          <uni-icons type="checkmarkempty" size="48" color="#1E5AA8"></uni-icons>
        </view>
        <text class="status-title">预约成功</text>
        <text class="status-desc">请按时就诊，祝您早日康复</text>
      </view>
      <view class="status-info" v-else>
        <text class="status-title">{{ statusText(appointment?.status) }}</text>
        <text class="status-desc">感谢使用智能医疗助手</text>
      </view>
    </view>

    <view class="result-content">
      <view class="appointment-card" v-if="appointment">
        <view class="card-item">
          <text class="label">就诊医生</text>
          <text class="value">{{ appointment.doctorName }} ({{ appointment.title || '主任医师' }})</text>
        </view>
        <view class="card-item">
          <text class="label">就诊科室</text>
          <text class="value">{{ appointment.departmentName }}</text>
        </view>
        <view class="card-item">
          <text class="label">就诊时间</text>
          <text class="value highlight">{{ appointment.appointmentDate }} {{ appointment.startTime }}{{ appointment.endTime ? '-' + appointment.endTime : '' }}</text>
        </view>
        <view class="card-item">
          <text class="label">就诊序号</text>
          <text class="value highlight">{{ appointment.queueNumber || '-' }}号</text>
        </view>
        <view class="card-item">
          <text class="label">就诊地点</text>
          <text class="value">门诊楼 3 楼 {{ appointment.departmentName }}第 5 诊室</text>
        </view>
      </view>

      <view class="action-btns">
        <button class="btn primary" @click="goHome">返回首页</button>
        <button class="btn secondary" @click="goList">查看所有预约</button>
        <button 
          v-if="appointment?.status === 0 || appointment?.status === 'PENDING' || appointment?.status === '待就诊'" 
          class="btn outline" 
          @click="handleCancel"
        >
          取消预约
        </button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getAppointmentById, cancelAppointment } from '@/api/appointment'

const appointmentId = ref('')
const isSuccess = ref(false)
const appointment = ref<any>(null)

const statusText = (status: any) => {
  const map: any = { 0: '待就诊', 1: '已完成', 2: '已取消' }
  return map[status] ?? status ?? '预约详情'
}

const goBack = () => uni.navigateBack()
const goHome = () => uni.reLaunch({ url: '/pages/chat/chat' })
const goList = () => uni.redirectTo({ url: '/pages/appointment/list' })

const fetchDetail = async () => {
  if (!appointmentId.value) return
  const res = await getAppointmentById(appointmentId.value)
  appointment.value = res.data || res
}

const handleCancel = async () => {
  const res = await uni.showModal({
    title: '提示',
    content: '确定要取消这个预约吗？'
  })
  if (res.confirm) {
    try {
      await cancelAppointment(appointmentId.value)
      uni.showToast({ title: '已取消', icon: 'success' })
      fetchDetail()
    } catch (e) {
      uni.showToast({ title: '取消失败', icon: 'none' })
    }
  }
}

onLoad((options) => {
  appointmentId.value = String(options?.id || '')
  isSuccess.value = options?.success === '1'
  fetchDetail()
})
</script>

<style scoped lang="scss">
.result-page {
  min-height: 100vh;
  background: $uni-bg-color-grey;
}

.result-header {
  height: 520rpx;
  background: $bg-gradient;
  padding: 88rpx 32rpx 32rpx;
  display: flex;
  flex-direction: column;
  gap: 40rpx;
  
  &.success {
    height: 580rpx;
  }
}

.header-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-title {
  font-size: 36rpx;
  font-weight: 600;
  color: #FFFFFF;
}

.placeholder { width: 48rpx; }

.status-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
}

.success-icon {
  width: 144rpx;
  height: 144rpx;
  background: #FFFFFF;
  border-radius: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.1);
  margin-bottom: 8rpx;
}

.status-title {
  font-size: 44rpx;
  font-weight: 700;
  color: #FFFFFF;
}

.status-desc {
  font-size: 28rpx;
  color: rgba(255, 255, 255, 0.9);
}

.result-content {
  margin-top: -40rpx;
  padding: 0 32rpx 60rpx;
  display: flex;
  flex-direction: column;
  gap: 32rpx;
}

.appointment-card {
  background: #FFFFFF;
  border-radius: 32rpx;
  padding: 40rpx;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  gap: 32rpx;
}

.card-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  
  .label {
    font-size: 28rpx;
    color: #6B7280;
  }
  
  .value {
    font-size: 28rpx;
    color: #1F2937;
    font-weight: 500;
    text-align: right;
    
    &.highlight {
      color: $uni-color-primary;
      font-size: 32rpx;
      font-weight: 700;
    }
  }
}

.action-btns {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.btn {
  height: 96rpx;
  border-radius: 48rpx;
  font-size: 32rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  
  &.primary {
    background: $uni-color-primary;
    color: #FFFFFF;
  }
  
  &.secondary {
    background: #FFFFFF;
    color: #1F2937;
  }
  
  &.outline {
    background: transparent;
    color: #EF4444;
    border: 1rpx solid #FCA5A5;
    margin-top: 20rpx;
  }
}
</style>
