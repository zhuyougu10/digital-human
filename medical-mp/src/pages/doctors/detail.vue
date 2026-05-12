<template>
  <view class="schedule-page">
    <view class="slot-header">
      <view class="header-top">
        <view class="back-icon" @click="goBack">
          <uni-icons type="left" size="24" color="#1e293b"></uni-icons>
        </view>
        <text class="header-title">选择号源</text>
        <view class="placeholder"></view>
      </view>
      <view class="doctor-card" v-if="doctor">
        <image class="avatar" :src="doctor.avatar || defaultAvatar" mode="aspectFill" />
        <view class="meta">
          <view class="name-row">
            <text class="name">{{ doctor.name }}</text>
            <text class="title">{{ doctor.title }}</text>
          </view>
          <view class="dept-row">
            <text class="dept">{{ doctor.departmentName }}</text>
            <view class="divider"></view>
            <text class="hospital">互联网医院</text>
          </view>
        </view>
      </view>
    </view>

    <view class="slot-content">
      <scroll-view class="date-selector" scroll-x enable-flex>
        <view 
          v-for="(item, index) in dateList" 
          :key="index" 
          class="date-item"
          :class="{ active: activeDate === item.date }"
          @click="selectDate(item.date)"
        >
          <text class="week">{{ item.week }}</text>
          <text class="day">{{ item.day }}</text>
          <view class="active-dot" v-if="activeDate === item.date"></view>
        </view>
      </scroll-view>

      <scroll-view class="slots-scroll" scroll-y>
        <view class="section">
          <view class="section-header">
            <view class="indicator morning"></view>
            <text class="section-title">上午号源</text>
          </view>
          <view class="time-grid">
            <view 
              v-for="slot in morningSlots" 
              :key="slot.id" 
              class="time-item"
              :class="{ active: selectedSlotId === slot.id, disabled: slot.remaining <= 0 }"
              @click="selectSlot(slot)"
            >
              <text class="time">{{ slot.time }}</text>
              <view class="status-tag" :class="{ 'full': slot.remaining <= 0 }">
                <text class="status-text">{{ slot.remaining > 0 ? '有号' : '约满' }}</text>
              </view>
            </view>
            <view v-if="morningSlots.length === 0" class="empty-tip">暂无排班</view>
          </view>
        </view>

        <view class="section">
          <view class="section-header">
            <view class="indicator afternoon"></view>
            <text class="section-title">下午号源</text>
          </view>
          <view class="time-grid">
            <view 
              v-for="slot in afternoonSlots" 
              :key="slot.id" 
              class="time-item"
              :class="{ active: selectedSlotId === slot.id, disabled: slot.remaining <= 0 }"
              @click="selectSlot(slot)"
            >
              <text class="time">{{ slot.time }}</text>
              <view class="status-tag" :class="{ 'full': slot.remaining <= 0 }">
                <text class="status-text">{{ slot.remaining > 0 ? '有号' : '约满' }}</text>
              </view>
            </view>
            <view v-if="afternoonSlots.length === 0" class="empty-tip">暂无排班</view>
          </view>
        </view>
        <view class="bottom-padding"></view>
      </scroll-view>
    </view>

    <view class="confirm-bar">
      <view class="selection-info">
        <view class="info-label" v-if="selectedSlot">
          <text class="selected-text">已选：{{ selectedSlot.date }} {{ selectedSlot.time }}</text>
        </view>
        <text v-else class="info-placeholder">请在上方选择就诊时间</text>
      </view>
      <button class="confirm-btn" :disabled="!selectedSlotId" @click="handleConfirm">
        确认预约
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getDoctorById, getAvailableSlots } from '@/api/doctor'
import { createAppointment } from '@/api/appointment'

interface DoctorDetail {
  id?: string | number
  avatar?: string
  name?: string
  title?: string
  departmentName?: string
  departmentId?: string | number | null
}

interface SlotItem {
  id: string | number
  scheduleDate?: string
  startTime?: string
  endTime?: string
  time: string
  date: string
  availableSlots?: number
  totalSlots?: number
  bookedSlots?: number
  remaining: number
}

const doctorId = ref('')
const doctor = ref<DoctorDetail | null>(null)
const slots = ref<SlotItem[]>([])
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
  return slots.value.filter(s => {
    const hour = parseInt(String(s.startTime || s.time || '0'))
    return hour < 12
  })
})

const afternoonSlots = computed(() => {
  return slots.value.filter(s => {
    const hour = parseInt(String(s.startTime || s.time || '0'))
    return hour >= 12
  })
})

const selectedSlot = computed(() => {
  return slots.value.find(s => s.id === selectedSlotId.value)
})

const fetchDoctor = async () => {
  const res = await getDoctorById(doctorId.value)
  doctor.value = res.data || res
}

const fetchSlots = async (date?: string) => {
  const queryDate = date || activeDate.value || dateList.value[0]?.date
  if (!queryDate || !doctorId.value) return
  activeDate.value = queryDate
  try {
    const res = await getAvailableSlots({ doctorId: doctorId.value, date: queryDate })
    const list = Array.isArray(res) ? res : (res || [])
    slots.value = list.map((s: Record<string, unknown>) => {
      const startTime = typeof s.startTime === 'string' ? s.startTime : ''
      const endTime = typeof s.endTime === 'string' ? s.endTime : ''
      const availableSlots = typeof s.availableSlots === 'number' ? s.availableSlots : undefined
      const totalSlots = typeof s.totalSlots === 'number' ? s.totalSlots : undefined
      const bookedSlots = typeof s.bookedSlots === 'number' ? s.bookedSlots : undefined

      return {
      ...s,
      id: String(s.id ?? ''),
      date: typeof s.scheduleDate === 'string' ? s.scheduleDate : queryDate,
      startTime,
      endTime,
      time: startTime ? `${startTime}${endTime ? '-' + endTime : ''}` : '',
      availableSlots,
      totalSlots,
      bookedSlots,
      remaining: availableSlots ?? ((totalSlots ?? 0) - (bookedSlots ?? 0))
      }
    })
  } catch (e) {
    slots.value = []
  }
}

const selectDate = (date: string) => {
  fetchSlots(date)
}

const selectSlot = (slot: SlotItem) => {
  if (slot.remaining <= 0) return
  selectedSlotId.value = String(slot.id)
}

const handleConfirm = async () => {
  if (!selectedSlotId.value) return
  
  uni.showLoading({ title: '预约中...' })
  try {
    const res = await createAppointment({
      doctorId: doctorId.value,
      slotId: selectedSlotId.value,
      departmentId: doctor.value?.departmentId || null
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
  if (dateList.value.length > 0) {
    activeDate.value = dateList.value[0].date
  }
  fetchSlots()
})
</script>

<style scoped lang="scss">
.schedule-page {
  min-height: 100vh;
  background: #f8faff;
  display: flex;
  flex-direction: column;
}

.slot-header {
  padding: 88rpx 32rpx 48rpx;
  background: #ffffff;
}

.header-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 40rpx;
}

.back-icon {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: flex-start;
}

.header-title {
  font-size: 34rpx;
  font-weight: 600;
  color: #1e293b;
}

.doctor-card {
  display: flex;
  align-items: center;
  gap: 28rpx;
  padding: 24rpx;
  background: #f1f5f9;
  border-radius: 24rpx;
}

.avatar {
  width: 120rpx;
  height: 120rpx;
  border-radius: 60rpx;
  border: 4rpx solid #ffffff;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.05);
}

.meta {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.name-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.name {
  font-size: 36rpx;
  font-weight: 700;
  color: #1e293b;
}

.title {
  font-size: 24rpx;
  color: #2e7ea7;
  background: #e8f4f8;
  padding: 4rpx 16rpx;
  border-radius: 8rpx;
  font-weight: 500;
}

.dept-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.dept, .hospital {
  font-size: 26rpx;
  color: #64748b;
}

.divider {
  width: 2rpx;
  height: 20rpx;
  background: #cbd5e1;
}

.slot-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #f8faff;
  padding: 32rpx 32rpx 0;
  border-radius: 48rpx 48rpx 0 0;
  margin-top: -24rpx;
  box-shadow: 0 -8rpx 24rpx rgba(0, 0, 0, 0.02);
}

.date-selector {
  display: flex;
  white-space: nowrap;
  padding: 8rpx 0 32rpx;
  margin-bottom: 16rpx;
}

.date-item {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 104rpx;
  height: 128rpx;
  background: #ffffff;
  border-radius: 24rpx;
  margin-right: 20rpx;
  border: 2rpx solid #f1f5f9;
  transition: all 0.2s ease;
  position: relative;

  &.active {
    background: #2e7ea7;
    border-color: #2e7ea7;
    box-shadow: 0 8rpx 16rpx rgba(46, 126, 167, 0.2);
    
    .week { color: rgba(255, 255, 255, 0.8); }
    .day { color: #ffffff; }
  }

  .week {
    font-size: 22rpx;
    color: #64748b;
    margin-bottom: 4rpx;
  }

  .day {
    font-size: 32rpx;
    font-weight: 700;
    color: #1e293b;
  }
  
  .active-dot {
    position: absolute;
    bottom: 12rpx;
    width: 8rpx;
    height: 8rpx;
    background: #ffffff;
    border-radius: 4rpx;
  }
}

.slots-scroll {
  flex: 1;
}

.section {
  margin-bottom: 40rpx;
  background: #ffffff;
  padding: 32rpx;
  border-radius: 32rpx;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.01);
}

.section-header {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 24rpx;
}

.indicator {
  width: 8rpx;
  height: 28rpx;
  border-radius: 4rpx;
  
  &.morning { background: #f59e0b; }
  &.afternoon { background: #2e7ea7; }
}

.section-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #1e293b;
}

.time-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20rpx;
}

.time-item {
  height: 112rpx;
  background: #f8faff;
  border-radius: 20rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 2rpx solid #f1f5f9;
  transition: all 0.2s;

  &.active {
    background: #e8f4f8;
    border-color: #2e7ea7;
    .time { color: #2e7ea7; }
    .status-tag { background: #2e7ea7; .status-text { color: #ffffff; } }
  }

  &.disabled {
    background: #f1f5f9;
    opacity: 0.6;
    border-color: #f1f5f9;
    .time { color: #94a3b8; }
  }

  .time {
    font-size: 28rpx;
    font-weight: 600;
    color: #334155;
    margin-bottom: 4rpx;
  }
}

.status-tag {
  padding: 2rpx 12rpx;
  background: #dcfce7;
  border-radius: 6rpx;
  
  &.full { background: #f1f5f9; }
}

.status-text {
  font-size: 20rpx;
  color: #059669;
  font-weight: 500;
  
  .full & { color: #94a3b8; }
}

.empty-tip {
  grid-column: span 3;
  padding: 40rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: #94a3b8;
}

.bottom-padding {
  height: 200rpx;
}

.confirm-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: #ffffff;
  padding: 24rpx 40rpx calc(40rpx + env(safe-area-inset-bottom));
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 -8rpx 30rpx rgba(0, 0, 0, 0.05);
  border-radius: 40rpx 40rpx 0 0;
  z-index: 100;
}

.selection-info {
  flex: 1;
}

.selected-text {
  font-size: 28rpx;
  font-weight: 600;
  color: #1e293b;
}

.info-placeholder {
  font-size: 26rpx;
  color: #94a3b8;
}

.confirm-btn {
  width: 260rpx;
  height: 96rpx;
  background: #2e7ea7;
  color: #ffffff;
  border-radius: 48rpx;
  font-size: 32rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  box-shadow: 0 8rpx 20rpx rgba(46, 126, 167, 0.25);
  margin: 0;
  
  &::after { border: none; }
  
  &[disabled] {
    background: #cbd5e1;
    box-shadow: none;
    color: #ffffff;
  }
}
</style>
