<template>
  <view class="schedule-page">
    <view class="slot-header">
      <view class="header-top">
        <uni-icons type="left" size="24" color="#FFFFFF" @click="goBack"></uni-icons>
        <text class="header-title">选择号源</text>
        <view class="placeholder"></view>
      </view>
      <view class="doctor-info" v-if="doctor">
        <image class="avatar" :src="doctor.avatar || defaultAvatar" mode="aspectFill" />
        <view class="meta">
          <view class="name-row">
            <text class="name">{{ doctor.name }}</text>
            <text class="title">{{ doctor.title }}</text>
          </view>
          <text class="dept">{{ doctor.departmentName }}</text>
        </view>
      </view>
    </view>

    <scroll-view class="slot-content" scroll-y>
      <view class="date-selector">
        <view 
          v-for="(item, index) in dateList" 
          :key="index" 
          class="date-item"
          :class="{ active: activeDate === item.date }"
          @click="selectDate(item.date)"
        >
          <text class="week">{{ item.week }}</text>
          <text class="day">{{ item.day }}</text>
        </view>
      </view>

      <view class="section">
        <text class="section-title">上午号源</text>
        <view class="time-grid">
          <view 
            v-for="slot in morningSlots" 
            :key="slot.id" 
            class="time-item"
            :class="{ active: selectedSlotId === slot.id, disabled: slot.remaining <= 0 }"
            @click="selectSlot(slot)"
          >
            <text class="time">{{ slot.time }}</text>
            <text class="status">{{ slot.remaining > 0 ? '有号' : '约满' }}</text>
          </view>
        </view>
      </view>

      <view class="section">
        <text class="section-title">下午号源</text>
        <view class="time-grid">
          <view 
            v-for="slot in afternoonSlots" 
            :key="slot.id" 
            class="time-item"
            :class="{ active: selectedSlotId === slot.id, disabled: slot.remaining <= 0 }"
            @click="selectSlot(slot)"
          >
            <text class="time">{{ slot.time }}</text>
            <text class="status">{{ slot.remaining > 0 ? '有号' : '约满' }}</text>
          </view>
        </view>
      </view>

      <view class="confirm-bar">
        <view class="selection-info">
          <text v-if="selectedSlot">已选: {{ selectedSlot.date }} {{ selectedSlot.time }}</text>
          <text v-else class="placeholder">请选择就诊时间</text>
        </view>
        <button class="confirm-btn" :disabled="!selectedSlotId" @click="handleConfirm">立即预约</button>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getDoctorById, getAvailableSlots } from '@/api/doctor'
import { createAppointment } from '@/api/appointment'

const doctorId = ref('')
const doctor = ref<any>(null)
const slots = ref<any[]>([])
const activeDate = ref('')
const selectedSlotId = ref('')
const defaultAvatar = '/static/logo.png'

const goBack = () => uni.navigateBack()

const dateList = computed(() => {
  const list = []
  const weeks = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  for (let i = 0; i < 7; i++) {
    const d = new Date()
    d.setDate(d.getDate() + i)
    const dateStr = d.toISOString().split('T')[0]
    list.push({
      date: dateStr,
      day: String(d.getDate()).padStart(2, '0'),
      week: i === 0 ? '今天' : weeks[d.getDay()]
    })
  }
  return list
})

const morningSlots = computed(() => {
  return slots.value.filter(s => s.date === activeDate.value && parseInt(s.time) < 12)
})

const afternoonSlots = computed(() => {
  return slots.value.filter(s => s.date === activeDate.value && parseInt(s.time) >= 12)
})

const selectedSlot = computed(() => {
  return slots.value.find(s => s.id === selectedSlotId.value)
})

const fetchDoctor = async () => {
  const res = await getDoctorById(doctorId.value)
  doctor.value = res.data || res
}

const fetchSlots = async () => {
  const res = await getAvailableSlots({ doctorId: doctorId.value })
  slots.value = res.data || res || []
  if (dateList.value.length > 0) {
    activeDate.value = dateList.value[0].date
  }
}

const selectDate = (date: string) => {
  activeDate.value = date
}

const selectSlot = (slot: any) => {
  if (slot.remaining <= 0) return
  selectedSlotId.value = slot.id
}

const handleConfirm = async () => {
  if (!selectedSlotId.value) return
  
  uni.showLoading({ title: '预约中...' })
  try {
    const res = await createAppointment({
      doctorId: doctorId.value,
      scheduleId: selectedSlotId.value
    })
    const data = res.data || res
    uni.hideLoading()
    uni.navigateTo({
      url: `/pages/appointment/detail?id=${data.id}&success=1`
    })
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: '预约失败', icon: 'none' })
  }
}

onLoad((options) => {
  doctorId.value = String(options?.id || '')
  fetchDoctor()
  fetchSlots()
})
</script>

<style scoped lang="scss">
.schedule-page {
  min-height: 100vh;
  background: $uni-bg-color-grey;
}

.slot-header {
  height: 360rpx;
  background: $bg-gradient;
  padding: 88rpx 32rpx 32rpx;
  display: flex;
  flex-direction: column;
  gap: 32rpx;
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

.doctor-info {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.avatar {
  width: 112rpx;
  height: 112rpx;
  border-radius: 56rpx;
  border: 4rpx solid rgba(255, 255, 255, 0.3);
}

.meta {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.name-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.name {
  font-size: 34rpx;
  font-weight: 700;
  color: #FFFFFF;
}

.title {
  font-size: 24rpx;
  color: #FFFFFF;
  background: rgba(255, 255, 255, 0.2);
  padding: 2rpx 12rpx;
  border-radius: 8rpx;
}

.dept {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.9);
}

.slot-content {
  margin-top: -32rpx;
  height: calc(100vh - 328rpx);
  background: $uni-bg-color-grey;
  border-top-left-radius: 40rpx;
  border-top-right-radius: 40rpx;
  padding: 32rpx;
  box-sizing: border-box;
}

.date-selector {
  display: flex;
  gap: 16rpx;
  margin-bottom: 40rpx;
  overflow-x: auto;
  padding-bottom: 8rpx;
}

.date-item {
  min-width: 100rpx;
  height: 120rpx;
  background: #FFFFFF;
  border-radius: 20rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4rpx;
  border: 1rpx solid #E5E7EB;
  flex-shrink: 0;

  &.active {
    background: $uni-color-primary;
    border-color: $uni-color-primary;
    .week, .day { color: #FFFFFF; }
  }

  .week { font-size: 22rpx; color: #6B7280; }
  .day { font-size: 30rpx; font-weight: 700; color: #1F2937; }
}

.section {
  margin-bottom: 40rpx;
}

.section-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #1F2937;
  margin-bottom: 24rpx;
  display: block;
}

.time-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20rpx;
}

.time-item {
  height: 100rpx;
  background: #FFFFFF;
  border-radius: 20rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 1rpx solid #E5E7EB;

  &.active {
    background: #EFF6FF;
    border-color: $uni-color-primary;
    .time { color: $uni-color-primary; }
    .status { color: $uni-color-primary; }
  }

  &.disabled {
    background: #F9FAFB;
    opacity: 0.5;
    .time, .status { color: #9CA3AF; }
  }

  .time { font-size: 28rpx; font-weight: 600; color: #1F2937; }
  .status { font-size: 20rpx; color: #10B981; }
}

.confirm-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: #FFFFFF;
  padding: 24rpx 32rpx calc(24rpx + env(safe-area-inset-bottom));
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1rpx solid #E5E7EB;
  z-index: 10;
}

.selection-info {
  flex: 1;
  font-size: 28rpx;
  color: #1F2937;
  .placeholder { color: #9CA3AF; }
}

.confirm-btn {
  width: 240rpx;
  height: 88rpx;
  background: $uni-color-primary;
  color: #FFFFFF;
  border-radius: 44rpx;
  font-size: 30rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
}

.confirm-btn[disabled] {
  background: #D1D5DB;
}
</style>
