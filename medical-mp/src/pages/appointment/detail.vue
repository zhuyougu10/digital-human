<template>
  <view class="result-page">
    <view class="result-header" :class="headerClass">
      <view class="header-top">
        <view class="back-icon" @click="goBack">
          <uni-icons type="left" size="24" color="#FFFFFF"></uni-icons>
        </view>
        <text class="header-title">{{ isSuccess ? '预约成功' : '预约详情' }}</text>
        <view class="placeholder"></view>
      </view>
      
      <view class="status-info" v-if="isSuccess">
        <view class="success-icon-wrap">
          <view class="success-icon-inner">
            <uni-icons type="checkmarkempty" size="48" color="#10b981"></uni-icons>
          </view>
        </view>
        <text class="status-title">预约提交成功</text>
        <text class="status-desc">请携带有效证件按时前往医院就诊</text>
      </view>
      <view class="status-info" v-else>
        <view class="status-badge">{{ statusText(appointment?.status) }}</view>
        <text class="status-title">{{ appointment?.doctorName }} 的预约</text>
        <text class="status-desc">请保持手机畅通，关注就诊提醒</text>
      </view>
    </view>

    <view class="result-content">
      <view class="info-group-title">预约详情信息</view>
      <view class="appointment-card" v-if="appointment">
        <view class="card-item">
          <text class="label">就诊医生</text>
          <view class="value-wrap">
            <text class="value-name">{{ appointment.doctorName }}</text>
            <text class="value-title">{{ appointment.title || '主任医师' }}</text>
          </view>
        </view>
        <view class="card-item">
          <text class="label">就诊科室</text>
          <text class="value">{{ appointment.departmentName }}</text>
        </view>
        <view class="divider"></view>
        <view class="card-item">
          <text class="label">就诊时间</text>
          <text class="value highlight">{{ appointment.appointmentDate }} {{ appointment.startTime }}{{ appointment.endTime ? '-' + appointment.endTime : '' }}</text>
        </view>
        <view class="card-item">
          <text class="label">就诊序号</text>
          <text class="value highlight-secondary">{{ appointment.queueNumber || '-' }}号</text>
        </view>
        <view class="card-item">
          <text class="label">就诊地点</text>
          <text class="value">门诊楼 3 楼 {{ appointment.departmentName }}诊区</text>
        </view>
      </view>

      <view class="tips-card">
        <uni-icons type="info" size="16" color="#64748b"></uni-icons>
        <text class="tips-text">温馨提示：如需取消预约，请至少提前 2 小时操作。</text>
      </view>

      <view class="action-btns">
        <button class="btn btn-primary" @click="goHome">返回首页</button>
        <button class="btn btn-secondary" @click="goList">查看我的预约</button>
        <view 
          v-if="canCancel" 
          class="cancel-link" 
          @click="handleCancel"
        >
          取消预约
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getAppointmentById, cancelAppointment } from '@/api/appointment'

const appointmentId = ref('')
const isSuccess = ref(false)
const appointment = ref<any>(null)

const headerClass = computed(() => ({
  'is-success': isSuccess.value,
  'is-detail': !isSuccess.value
}))

const canCancel = computed(() => {
  const s = appointment.value?.status
  return s === 0 || s === 'PENDING' || s === '待就诊'
})

const statusText = (status: any) => {
  const map: any = { 0: '待就诊', 1: '已完成', 2: '已取消' }
  return map[status] ?? status ?? '已预约'
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
    content: '确定要取消这个预约吗？',
    confirmColor: '#ef4444'
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
$primary: #2e7ea7;
$success: #41a56a;
$text-main: #1f2d3d;
$text-sub: #5f6b76;
$bg-gradient: linear-gradient(135deg, #2e7ea7 0%, #1f5f82 100%);
$success-gradient: linear-gradient(135deg, #41a56a 0%, #2f8352 100%);

.result-page {
  min-height: 100vh;
  background: #f5f8fb;
}

.result-header {
  height: 560rpx;
  padding: 88rpx 32rpx 48rpx;
  display: flex;
  flex-direction: column;
  transition: all 0.3s;
  
  &.is-success {
    background: $success-gradient;
  }
  
  &.is-detail {
    background: $bg-gradient;
    height: 480rpx;
  }
}

.header-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.back-icon {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 32rpx;
}

.header-title {
  font-size: 34rpx;
  font-weight: 600;
  color: #FFFFFF;
}

.placeholder { width: 64rpx; }

.status-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
}

.success-icon-wrap {
  width: 160rpx;
  height: 160rpx;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16rpx;
}

.success-icon-inner {
  width: 120rpx;
  height: 120rpx;
  background: #FFFFFF;
  border-radius: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 20rpx rgba(0, 0, 0, 0.05);
}

.status-badge {
  background: rgba(255, 255, 255, 0.2);
  color: #FFFFFF;
  font-size: 22rpx;
  padding: 4rpx 16rpx;
  border-radius: 20rpx;
  margin-bottom: 12rpx;
}

.status-title {
  font-size: 44rpx;
  font-weight: 700;
  color: #FFFFFF;
}

.status-desc {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.85);
}

.result-content {
  margin-top: -60rpx;
  padding: 0 32rpx 80rpx;
  display: flex;
  flex-direction: column;
  gap: 32rpx;
}

.info-group-title {
  font-size: 26rpx;
  color: #FFFFFF;
  margin-left: 12rpx;
  margin-bottom: -16rpx;
  opacity: 0.9;
}

.appointment-card {
  background: #FFFFFF;
  border-radius: 32rpx;
  padding: 40rpx;
  box-shadow: 0 12rpx 32rpx rgba(0, 0, 0, 0.04);
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
    color: $text-sub;
  }
  
  .value {
    font-size: 28rpx;
    color: $text-main;
    font-weight: 500;
    text-align: right;
  }
  
  .value-wrap {
    text-align: right;
    display: flex;
    flex-direction: column;
    
    .value-name {
      font-size: 30rpx;
      font-weight: 600;
      color: $text-main;
    }
    
    .value-title {
      font-size: 22rpx;
      color: $text-sub;
      margin-top: 4rpx;
    }
  }
  
  .highlight {
    color: $primary;
    font-size: 30rpx;
    font-weight: 700;
  }
  
  .highlight-secondary {
    color: #f59e0b;
    font-size: 32rpx;
    font-weight: 700;
  }
}

.divider {
  height: 1rpx;
  background: #f1f5f9;
}

.tips-card {
  background: #f1f5f9;
  border-radius: 16rpx;
  padding: 24rpx;
  display: flex;
  gap: 12rpx;
  
  .tips-text {
    font-size: 24rpx;
    color: $text-sub;
    flex: 1;
  }
}

.action-btns {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  margin-top: 24rpx;
}

.btn {
  height: 100rpx;
  border-radius: 50rpx;
  font-size: 32rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  
  &-primary {
    background: $primary;
    color: #FFFFFF;
    box-shadow: 0 8rpx 20rpx rgba(37, 99, 235, 0.2);
  }
  
  &-secondary {
    background: #FFFFFF;
    color: $text-main;
    border: 2rpx solid #e2e8f0;
  }
}

.cancel-link {
  text-align: center;
  font-size: 28rpx;
  color: #ef4444;
  margin-top: 20rpx;
  padding: 20rpx;
}
</style>
