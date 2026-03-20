<template>
  <view class="appointment-list-page">
    <view class="appt-header">
      <text class="appt-header-title">我的预约</text>
    </view>

    <view class="appt-tabs">
      <view 
        v-for="tab in tabs" 
        :key="tab.value" 
        class="tab-item"
        :class="{ active: activeTab === tab.value }"
        @click="activeTab = tab.value"
      >
        <text class="tab-text">{{ tab.label }}</text>
        <view class="tab-line"></view>
      </view>
    </view>

    <scroll-view class="appt-content" scroll-y>
      <view class="list">
        <view 
          v-for="item in filteredAppointments" 
          :key="item.id" 
          class="appt-card"
          @click="goDetail(item.id)"
        >
          <view class="card-header">
            <view class="doctor-info">
              <uni-icons type="person-filled" size="20" color="#1E5AA8"></uni-icons>
              <text class="doctor-name">{{ item.doctorName }}</text>
            </view>
            <text class="status" :class="item.status.toLowerCase()">{{ statusText(item.status) }}</text>
          </view>
          <view class="card-body">
            <view class="info-row">
              <text class="label">就诊科室：</text>
              <text class="value">{{ item.department }}</text>
            </view>
            <view class="info-row">
              <text class="label">就诊时间：</text>
              <text class="value">{{ item.date }} {{ item.time }}</text>
            </view>
            <view class="info-row">
              <text class="label">就诊地点：</text>
              <text class="value">门诊楼 3 楼 {{ item.department }}诊室</text>
            </view>
          </view>
          <view class="card-footer" v-if="item.status === 0 || item.status === 'PENDING' || item.status === '待就诊'">
            <view class="cancel-btn" @click.stop="handleCancel(item.id)">取消预约</view>
          </view>
        </view>
      </view>

      <view v-if="!filteredAppointments.length" class="empty">
        <uni-icons type="calendar" size="48" color="#D1D5DB"></uni-icons>
        <text>暂无预约记录</text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getMyAppointments, cancelAppointment } from '@/api/appointment'

const tabs = [
  { label: '全部', value: 'ALL' },
  { label: '待就诊', value: 'PENDING' },
  { label: '已完成', value: 'COMPLETED' }
]

const activeTab = ref('ALL')
const appointments = ref<any[]>([])

const filteredAppointments = computed(() => {
  if (activeTab.value === 'ALL') return appointments.value
  return appointments.value.filter(a => {
    if (activeTab.value === 'PENDING') return a.status === 0 || a.status === 'PENDING' || a.status === '待就诊'
    if (activeTab.value === 'COMPLETED') return a.status === 1 || a.status === 'COMPLETED' || a.status === '已完成'
    return true
  })
})

const statusText = (status: any) => {
  const map: any = {
    0: '待就诊', 1: '已完成', 2: '已取消',
    PENDING: '待就诊', COMPLETED: '已完成', CANCELED: '已取消',
    '待就诊': '待就诊', '已完成': '已完成', '已取消': '已取消'
  }
  return map[status] ?? String(status)
}

const goDetail = (id: string | number) => {
  uni.navigateTo({ url: `/pages/appointment/detail?id=${id}` })
}

const fetchAppointments = async () => {
  const res = await getMyAppointments({})
  const list = Array.isArray(res) ? res : (res.records || [])
  appointments.value = list.map((item: any) => ({
    id: item.id,
    doctorName: item.doctorName || '未知医生',
    department: item.departmentName || '',
    date: item.appointmentDate || '',
    time: item.startTime ? `${item.startTime}${item.endTime ? '-' + item.endTime : ''}` : '',
    status: item.status
  }))
}

const handleCancel = async (id: string | number) => {
  const res = await uni.showModal({
    title: '提示',
    content: '确定要取消这个预约吗？'
  })
  if (res.confirm) {
    try {
      await cancelAppointment(id)
      uni.showToast({ title: '已取消', icon: 'success' })
      fetchAppointments()
    } catch (e) {
      uni.showToast({ title: '取消失败', icon: 'none' })
    }
  }
}

onMounted(fetchAppointments)
</script>

<style scoped lang="scss">
.appointment-list-page {
  min-height: 100vh;
  background: $uni-bg-color-grey;
  display: flex;
  flex-direction: column;
}

.appt-header {
  height: 220rpx;
  background: $bg-gradient;
  padding: 88rpx 32rpx 32rpx;
  display: flex;
  align-items: center;
}

.appt-header-title {
  font-size: 40rpx;
  font-weight: 700;
  color: #FFFFFF;
}

.appt-tabs {
  height: 96rpx;
  background: #FFFFFF;
  display: flex;
  padding: 0 32rpx;
  border-bottom: 1rpx solid #F3F4F6;
}

.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  
  .tab-text {
    font-size: 28rpx;
    color: #6B7280;
    transition: all 0.3s;
  }
  
  &.active {
    .tab-text {
      color: $uni-color-primary;
      font-weight: 600;
    }
    .tab-line {
      width: 40rpx;
      height: 6rpx;
      background: $uni-color-primary;
      border-radius: 3rpx;
      position: absolute;
      bottom: 0;
    }
  }
}

.appt-content {
  flex: 1;
  min-height: 0;
  padding: 32rpx;
  box-sizing: border-box;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.appt-card {
  background: #FFFFFF;
  border-radius: 24rpx;
  padding: 32rpx;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.05);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24rpx;
  padding-bottom: 24rpx;
  border-bottom: 1rpx solid #F3F4F6;
}

.doctor-info {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.doctor-name {
  font-size: 30rpx;
  font-weight: 600;
  color: #1F2937;
}

.status {
  font-size: 24rpx;
  font-weight: 500;
  
  &.pending { color: $uni-color-warning; }
  &.completed { color: $uni-color-success; }
  &.canceled { color: #9CA3AF; }
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.info-row {
  display: flex;
  gap: 16rpx;
  
  .label {
    font-size: 26rpx;
    color: #6B7280;
    width: 140rpx;
  }
  
  .value {
    font-size: 26rpx;
    color: #374151;
    flex: 1;
  }
}

.card-footer {
  margin-top: 24rpx;
  padding-top: 24rpx;
  border-top: 1rpx solid #F3F4F6;
  display: flex;
  justify-content: flex-end;
}

.cancel-btn {
  padding: 8rpx 24rpx;
  border: 1rpx solid #FCA5A5;
  color: #EF4444;
  font-size: 24rpx;
  border-radius: 24rpx;
}

.empty {
  padding: 120rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20rpx;
  color: #9CA3AF;
  font-size: 28rpx;
}
</style>
