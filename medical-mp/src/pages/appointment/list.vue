<template>
  <view class="appointment-list-page">
    <view class="appt-header">
      <view class="header-content">
        <text class="appt-header-title">我的预约</text>
        <text class="appt-header-subtitle">我是你的医疗助手安禾，为您管理就诊安排</text>
      </view>
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
              <view class="avatar-sm">
                <text>{{ item.doctorName.substring(0, 1) }}</text>
              </view>
              <view class="doctor-meta">
                <text class="doctor-name">{{ item.doctorName }}</text>
                <text class="dept-name">{{ item.department }}</text>
              </view>
            </view>
            <view class="status-tag" :class="statusClass(item.status)">
              {{ statusText(item.status) }}
            </view>
          </view>
          
          <view class="card-body">
            <view class="info-row">
              <uni-icons type="calendar" size="14" color="#64748b"></uni-icons>
              <text class="label">预约时间：</text>
              <text class="value">{{ item.date }} {{ item.time }}</text>
            </view>
            <view class="info-row">
              <uni-icons type="location" size="14" color="#64748b"></uni-icons>
              <text class="label">就诊地点：</text>
              <text class="value">门诊楼 3 楼 {{ item.department }}诊室</text>
            </view>
          </view>

          <view class="card-footer" v-if="isPending(item.status)">
            <view class="cancel-btn" @click.stop="handleCancel(item.id)">取消预约</view>
            <view class="detail-link">
              <text>详情</text>
              <uni-icons type="right" size="12" color="#2563eb"></uni-icons>
            </view>
          </view>
          <view class="card-footer" v-else>
            <view class="detail-link">
              <text>查看详情</text>
              <uni-icons type="right" size="12" color="#64748b"></uni-icons>
            </view>
          </view>
        </view>
      </view>

      <view v-if="!filteredAppointments.length" class="empty-state">
        <image class="empty-img" src="/static/images/empty-apt.png" mode="aspectFit" v-if="false"></image>
        <view class="empty-icon-wrap">
          <uni-icons type="calendar-filled" size="64" color="#e2e8f0"></uni-icons>
        </view>
        <text class="empty-text">暂无相关预约记录</text>
        <button class="empty-btn" @click="goChat" v-if="activeTab === 'ALL'">去预约</button>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getMyAppointments, cancelAppointment } from '@/api/appointment'

type AppointmentStatus = string | number

interface AppointmentItem {
  id: string | number
  doctorName: string
  department: string
  date: string
  time: string
  status: AppointmentStatus
}

interface RawAppointment {
  id?: string | number
  doctorName?: string
  departmentName?: string
  appointmentDate?: string
  startTime?: string
  endTime?: string
  status?: AppointmentStatus
}

const tabs = [
  { label: '全部', value: 'ALL' },
  { label: '待就诊', value: 'PENDING' },
  { label: '已完成', value: 'COMPLETED' }
]

const activeTab = ref('ALL')
const appointments = ref<AppointmentItem[]>([])

const filteredAppointments = computed(() => {
  if (activeTab.value === 'ALL') return appointments.value
  return appointments.value.filter(a => {
    if (activeTab.value === 'PENDING') return a.status === 0 || a.status === 'PENDING' || a.status === '待就诊'
    if (activeTab.value === 'COMPLETED') return a.status === 1 || a.status === 'COMPLETED' || a.status === '已完成'
    return true
  })
})

const statusText = (status: AppointmentStatus) => {
  const map: Record<string, string> = {
    0: '待就诊', 1: '已完成', 2: '已取消',
    PENDING: '待就诊', COMPLETED: '已完成', CANCELED: '已取消',
    '待就诊': '待就诊', '已完成': '已完成', '已取消': '已取消'
  }
  return map[status] ?? String(status)
}

const statusClass = (status: AppointmentStatus) => {
  if (status === 0 || status === 'PENDING' || status === '待就诊') return 'pending'
  if (status === 1 || status === 'COMPLETED' || status === '已完成') return 'completed'
  return 'canceled'
}

const isPending = (status: AppointmentStatus) => {
  return status === 0 || status === 'PENDING' || status === '待就诊'
}

const goDetail = (id: string | number) => {
  uni.navigateTo({ url: `/pages/appointment/detail?id=${id}` })
}

const goChat = () => {
  uni.reLaunch({ url: '/pages/chat/chat' })
}

const fetchAppointments = async () => {
  const res = await getMyAppointments({})
  const list: RawAppointment[] = Array.isArray(res) ? res : (res.records || [])
  appointments.value = list.map((item) => ({
    id: item.id ?? '',
    doctorName: item.doctorName || '未知医生',
    department: item.departmentName || '',
    date: item.appointmentDate || '',
    time: item.startTime ? `${item.startTime}${item.endTime ? '-' + item.endTime : ''}` : '',
    status: item.status ?? '已取消'
  }))
}

const handleCancel = async (id: string | number) => {
  const res = await uni.showModal({
    title: '提示',
    content: '确定要取消这个预约吗？',
    confirmColor: '#2563eb'
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
$primary: var(--brand-primary);
$secondary: var(--brand-secondary);
$success: var(--brand-success);
$warning: var(--brand-warning);
$text-main: var(--text-main);
$text-sub: var(--text-subtle);
$bg-light: var(--bg-muted);

.appointment-list-page {
  min-height: 100vh;
  background:
    radial-gradient(circle at top right, rgba(37, 99, 235, 0.08), transparent 22%),
    linear-gradient(180deg, #f8fbff 0%, #f7fafc 100%);
  display: flex;
  flex-direction: column;
}

.appt-header {
  height: 260rpx;
  background: linear-gradient(135deg, $primary 0%, $secondary 100%);
  padding: 80rpx 40rpx 40rpx;
  display: flex;
  align-items: center;
  position: relative;
  overflow: hidden;

  &::after {
    content: '';
    position: absolute;
    right: -30rpx;
    bottom: -30rpx;
    width: 220rpx;
    height: 220rpx;
    background: rgba(255, 255, 255, 0.08);
    border-radius: 110rpx;
  }
}

.appt-header-title {
  font-size: 40rpx;
  font-weight: 600;
  color: #FFFFFF;
  display: block;
}

.appt-header-subtitle {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.85);
  margin-top: 10rpx;
  display: block;
}

.appt-tabs {
  height: 100rpx;
  background: rgba(255, 255, 255, 0.92);
  display: flex;
  padding: 0 40rpx;
  position: sticky;
  top: 0;
  z-index: 10;
  box-shadow: var(--shadow-sm);
  border-bottom: 1rpx solid rgba(226, 232, 240, 0.8);
}

.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  
  .tab-text {
    font-size: 30rpx;
    color: $text-sub;
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  }
  
  &.active {
    .tab-text {
      color: $primary;
      font-weight: 600;
      transform: scale(1.05);
    }
    .tab-line {
      width: 48rpx;
      height: 6rpx;
      background: $primary;
      border-radius: 3rpx;
      position: absolute;
      bottom: 12rpx;
      animation: tabIn 0.3s ease-out;
    }
  }
}

@keyframes tabIn {
  from { width: 0; opacity: 0; }
  to { width: 48rpx; opacity: 1; }
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
  gap: 28rpx;
}

.appt-card {
  background: rgba(255, 255, 255, 0.94);
  border-radius: 28rpx;
  padding: 32rpx;
  box-shadow: var(--shadow-sm);
  border: 1rpx solid rgba(226, 232, 240, 0.88);
  transition: transform 0.2s;
  
  &:active {
    transform: scale(0.99);
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 28rpx;
}

.doctor-info {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.avatar-sm {
  width: 72rpx;
  height: 72rpx;
  background: var(--brand-primary-soft);
  border-radius: 36rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 600;
  color: $primary;
}

.doctor-meta {
  display: flex;
  flex-direction: column;
}

.doctor-name {
  font-size: 30rpx;
  font-weight: 600;
  color: $text-main;
}

.dept-name {
  font-size: 22rpx;
  color: $text-sub;
}

.status-tag {
  font-size: 22rpx;
  padding: 6rpx 16rpx;
  border-radius: 9999rpx;
  font-weight: 500;
  
  &.pending { background: rgba(245, 158, 11, 0.12); color: $warning; }
  &.completed { background: rgba(16, 185, 129, 0.12); color: $success; }
  &.canceled { background: var(--bg-muted); color: $text-sub; }
}

.card-body {
  background: $bg-light;
  border-radius: 22rpx;
  padding: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.info-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  
.label {
    font-size: 26rpx;
    color: $text-sub;
  }
  
  .value {
    font-size: 26rpx;
    color: $text-main;
    font-weight: 500;
  }
}

.card-footer {
  margin-top: 28rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.cancel-btn {
  padding: 10rpx 28rpx;
  border: 2rpx solid rgba(226, 232, 240, 0.95);
  color: $text-sub;
  font-size: 24rpx;
  border-radius: 9999rpx;
}

.detail-link {
  display: flex;
  align-items: center;
  gap: 4rpx;
  font-size: 26rpx;
  color: $primary;
  font-weight: 500;
}

.empty-state {
  padding: 160rpx 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.empty-icon-wrap {
  width: 160rpx;
  height: 160rpx;
  background: var(--bg-muted);
  border-radius: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 32rpx;
}

.empty-text {
  color: var(--text-subtle);
  font-size: 28rpx;
}

.empty-btn {
  margin-top: 48rpx;
  width: 240rpx;
  height: 80rpx;
  line-height: 80rpx;
  background: linear-gradient(135deg, var(--brand-primary) 0%, var(--brand-secondary) 100%);
  color: #FFFFFF;
  border-radius: 9999rpx;
  font-size: 28rpx;
  font-weight: 600;
}
</style>
