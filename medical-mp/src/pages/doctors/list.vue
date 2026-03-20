<template>
  <view class="recommend-page">
    <view class="dr-header">
      <view class="header-top">
        <uni-icons type="left" size="24" color="#FFFFFF" @click="goBack"></uni-icons>
        <text class="header-title">医生推荐</text>
        <view class="placeholder"></view>
      </view>
      <view class="dr-info">
        <view class="dr-avatar">
          <uni-icons type="person-filled" size="32" color="#1E5AA8"></uni-icons>
        </view>
        <text class="dr-name">数字人医生</text>
        <text class="dr-status">根据您的症状，为您推荐以下医生</text>
      </view>
    </view>

    <scroll-view class="dr-content" scroll-y>
      <view class="dept-selector" v-if="departments.length">
        <view 
          v-for="dept in departments" 
          :key="dept.id" 
          class="dept-tag"
          :class="{ active: activeDeptId === dept.id }"
          @click="activeDeptId = dept.id"
        >
          {{ dept.name }}
        </view>
      </view>

      <view class="doctor-list">
        <view 
          v-for="doctor in filteredDoctors" 
          :key="doctor.id" 
          class="doctor-card"
          @click="goDetail(doctor.id)"
        >
          <view class="card-header">
            <image class="avatar" :src="doctor.avatar || defaultAvatar" mode="aspectFill" />
            <view class="meta">
              <view class="name-row">
                <text class="name">{{ doctor.name }}</text>
                <text class="title">{{ doctor.title }}</text>
              </view>
              <text class="dept">{{ doctor.departmentName }}</text>
            </view>
            <view class="score">
              <uni-icons type="star-filled" size="14" color="#F59E0B"></uni-icons>
              <text class="score-text">4.9</text>
            </view>
          </view>
          <view class="specialties">
            <text v-for="tag in doctor.specialties" :key="tag" class="tag">{{ tag }}</text>
          </view>
          <view class="footer">
            <text class="fee">挂号费: ¥{{ doctor.fee || 50 }}</text>
            <view class="select-btn">立即预约</view>
          </view>
        </view>
      </view>

      <view v-if="!filteredDoctors.length" class="empty">
        <uni-icons type="info" size="48" color="#D1D5DB"></uni-icons>
        <text>暂无匹配医生</text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getDepartmentList, getDoctorList } from '@/api/doctor'

const departments = ref<any[]>([])
const doctors = ref<any[]>([])
const activeDeptId = ref<string | number>('')
const defaultAvatar = '/static/logo.png'

const goBack = () => uni.navigateBack()

const filteredDoctors = computed(() => {
  if (!activeDeptId.value) return doctors.value
  return doctors.value.filter(d => d.departmentId === activeDeptId.value)
})

const fetchDepartments = async () => {
  const res = await getDepartmentList()
  departments.value = (Array.isArray(res) ? res : (res.records || [])).map(d => ({
    id: d.id,
    name: d.name || d.departmentName
  }))
}

const fetchDoctors = async () => {
  const res = await getDoctorList({})
  doctors.value = (Array.isArray(res) ? res : (res.records || [])).map(d => ({
    ...d,
    specialties: (d.specialties || '').split(/[，,]/).filter(Boolean)
  }))
}

const goDetail = (id: string | number) => {
  uni.navigateTo({ url: `/pages/doctors/detail?id=${id}` })
}

onMounted(() => {
  fetchDepartments()
  fetchDoctors()
})
</script>

<style scoped lang="scss">
.recommend-page {
  min-height: 100vh;
  background: $uni-bg-color-grey;
}

.dr-header {
  height: 440rpx;
  background: $bg-gradient;
  padding: 88rpx 32rpx 32rpx;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
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

.dr-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
}

.dr-avatar {
  width: 120rpx;
  height: 120rpx;
  background: #FFFFFF;
  border-radius: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.dr-name {
  font-size: 32rpx;
  font-weight: 600;
  color: #FFFFFF;
}

.dr-status {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.9);
}

.dr-content {
  margin-top: -32rpx;
  height: calc(100vh - 408rpx);
  background: $uni-bg-color-grey;
  border-top-left-radius: 40rpx;
  border-top-right-radius: 40rpx;
  padding: 32rpx;
  box-sizing: border-box;
}

.dept-selector {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-bottom: 32rpx;
}

.dept-tag {
  padding: 12rpx 28rpx;
  background: #FFFFFF;
  color: #4B5563;
  font-size: 24rpx;
  border-radius: 32rpx;
  border: 1rpx solid #E5E7EB;
  
  &.active {
    background: $uni-color-primary;
    color: #FFFFFF;
    border-color: $uni-color-primary;
  }
}

.doctor-list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.doctor-card {
  background: #FFFFFF;
  border-radius: 24rpx;
  padding: 24rpx;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.05);
}

.card-header {
  display: flex;
  gap: 20rpx;
  align-items: flex-start;
}

.avatar {
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  background: #F3F4F6;
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
  gap: 12rpx;
}

.name {
  font-size: 32rpx;
  font-weight: 700;
  color: #1F2937;
}

.title {
  font-size: 24rpx;
  color: $uni-color-primary;
  background: #EFF6FF;
  padding: 2rpx 12rpx;
  border-radius: 8rpx;
}

.dept {
  font-size: 24rpx;
  color: #6B7280;
}

.score {
  display: flex;
  align-items: center;
  gap: 4rpx;
  background: #FFFBEB;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  
  .score-text {
    font-size: 24rpx;
    font-weight: 600;
    color: #D97706;
  }
}

.specialties {
  margin-top: 20rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  
  .tag {
    font-size: 22rpx;
    color: #6B7280;
    background: #F9FAFB;
    padding: 6rpx 16rpx;
    border-radius: 8rpx;
  }
}

.footer {
  margin-top: 24rpx;
  padding-top: 24rpx;
  border-top: 1rpx solid #F3F4F6;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.fee {
  font-size: 28rpx;
  font-weight: 600;
  color: #EF4444;
}

.select-btn {
  padding: 12rpx 32rpx;
  background: $uni-color-primary;
  color: #FFFFFF;
  font-size: 26rpx;
  font-weight: 600;
  border-radius: 32rpx;
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
