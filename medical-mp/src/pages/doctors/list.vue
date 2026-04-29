<template>
  <view class="recommend-page">
    <view class="dr-header">
      <view class="header-top">
        <uni-icons type="left" size="24" color="#FFFFFF" @click="goBack"></uni-icons>
        <text class="header-title">找医生</text>
        <view class="placeholder"></view>
      </view>
      <view class="dr-info">
        <view class="dr-avatar">
          <uni-icons type="person-filled" size="36" color="#2e7ea7"></uni-icons>
        </view>
        <text class="dr-name">数字人医疗助手</text>
        <text class="dr-status">基于您的症状分析，为您精准推荐专家</text>
      </view>
    </view>

    <view class="dr-content-wrapper">
      <scroll-view class="dr-content" scroll-y>
        <!-- 搜索/筛选区域 -->
        <view class="search-section">
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
        </view>

        <!-- 医生列表 -->
        <view class="doctor-list">
          <view 
            v-for="doctor in filteredDoctors" 
            :key="doctor.id" 
            class="doctor-card"
            @click="goDetail(doctor.id)"
          >
            <view class="card-left">
              <image class="avatar" :src="doctor.avatar || defaultAvatar" mode="aspectFill" />
            </view>
            <view class="card-middle">
              <view class="name-row">
                <text class="name">{{ doctor.name }}</text>
                <text class="title-tag">{{ doctor.title }}</text>
              </view>
              <text class="dept-name">{{ doctor.departmentName }}</text>
              <view class="specialty-tags">
                <text v-for="tag in (doctor.specialties || []).slice(0, 2)" :key="tag" class="tag">{{ tag }}</text>
              </view>
            </view>
            <view class="card-right">
              <view class="fee-box">
                <text class="fee-label">挂号费</text>
                <text class="fee-amount">¥{{ doctor.fee || 50 }}</text>
              </view>
              <view class="book-btn">预约</view>
            </view>
          </view>
        </view>

        <!-- 空状态 -->
        <view v-if="!filteredDoctors.length" class="empty-state">
          <view class="empty-icon-box">
            <uni-icons type="info" size="64" color="#cbd5e1"></uni-icons>
          </view>
          <text class="empty-text">暂无相关医生</text>
          <text class="empty-subtext">可以尝试切换其他科室看看</text>
        </view>
        
        <!-- 底部占位 -->
        <view class="bottom-placeholder"></view>
      </scroll-view>
    </view>
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
  return doctors.value.filter((d: any) => d.departmentId === activeDeptId.value)
})

const fetchDepartments = async () => {
  const res = await getDepartmentList()
  departments.value = (Array.isArray(res) ? res : (res.records || [])).map((d: any) => ({
    id: d.id,
    name: d.name || d.departmentName
  }))
}

const fetchDoctors = async () => {
  const res = await getDoctorList({})
  doctors.value = (Array.isArray(res) ? res : (res.records || [])).map((d: any) => ({
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
  background: #f8faff;
  font-family: "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
}

.dr-header {
  height: 480rpx;
  background: linear-gradient(180deg, #2e7ea7 0%, #468eb3 60%, #5b9dbf 100%);
  padding: 88rpx 32rpx 32rpx;
  display: flex;
  flex-direction: column;
}

.header-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 40rpx;
}

.header-title {
  font-size: 34rpx;
  font-weight: 600;
  color: #FFFFFF;
}

.placeholder { width: 48rpx; }

.dr-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
}

.dr-avatar {
  width: 140rpx;
  height: 140rpx;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 70rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 20rpx rgba(0, 0, 0, 0.1);
  border: 4rpx solid rgba(255, 255, 255, 0.3);
}

.dr-name {
  font-size: 36rpx;
  font-weight: 700;
  color: #FFFFFF;
  letter-spacing: 2rpx;
}

.dr-status {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.9);
}

.dr-content-wrapper {
  margin-top: -48rpx;
  flex: 1;
  position: relative;
  height: calc(100vh - 432rpx);
}

.dr-content {
  height: 100%;
  background: #f8faff;
  border-top-left-radius: 48rpx;
  border-top-right-radius: 48rpx;
  padding: 40rpx 32rpx;
  box-sizing: border-box;
}

.search-section {
  margin-bottom: 32rpx;
}

.dept-selector {
  display: flex;
  white-space: nowrap;
  gap: 20rpx;
  padding-bottom: 8rpx;
  overflow-x: auto;
  
  // 隐藏滚动条
  &::-webkit-scrollbar {
    display: none;
  }
}

.dept-tag {
  flex-shrink: 0;
  padding: 14rpx 32rpx;
  background: #f1f5f9;
  color: #64748b;
  font-size: 26rpx;
  border-radius: 40rpx;
  transition: all 0.3s;
  
  &.active {
    background: #2e7ea7;
    color: #FFFFFF;
    box-shadow: 0 4rpx 12rpx rgba(46, 126, 167, 0.25);
  }
}

.doctor-list {
  display: flex;
  flex-direction: column;
  gap: 28rpx;
}

.doctor-card {
  background: #FFFFFF;
  border-radius: 28rpx;
  padding: 32rpx 24rpx;
  display: flex;
  align-items: center;
  gap: 24rpx;
  box-shadow: 0 8rpx 24rpx rgba(148, 163, 184, 0.08);
  border: 1rpx solid rgba(226, 232, 240, 0.6);
  
  &:active {
    transform: scale(0.98);
    background: #fcfdfe;
  }
}

.card-left {
  flex-shrink: 0;
  
  .avatar {
    width: 120rpx;
    height: 120rpx;
    border-radius: 60rpx;
    background: #f8faff;
    border: 4rpx solid #f0f4ff;
  }
}

.card-middle {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  min-width: 0;
  
  .name-row {
    display: flex;
    align-items: center;
    gap: 12rpx;
  }
  
  .name {
    font-size: 32rpx;
    font-weight: 700;
    color: #1e293b;
  }
  
  .title-tag {
    font-size: 22rpx;
    color: #2e7ea7;
    background: rgba(46, 126, 167, 0.08);
    padding: 2rpx 12rpx;
    border-radius: 8rpx;
  }
  
  .dept-name {
    font-size: 26rpx;
    color: #64748b;
  }
  
  .specialty-tags {
    display: flex;
    gap: 12rpx;
    margin-top: 6rpx;
    
    .tag {
      font-size: 22rpx;
      color: #94a3b8;
      background: #f8fafc;
      padding: 4rpx 16rpx;
      border-radius: 20rpx;
      border: 1rpx solid #f1f5f9;
    }
  }
}

.card-right {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 16rpx;
  
  .fee-box {
    text-align: right;
    display: flex;
    flex-direction: column;
  }
  
  .fee-label {
    font-size: 20rpx;
    color: #94a3b8;
  }
  
  .fee-amount {
    font-size: 28rpx;
    font-weight: 700;
    color: #0d9488; // 使用辅色
  }
  
  .book-btn {
    background: #2e7ea7;
    color: #ffffff;
    font-size: 24rpx;
    font-weight: 600;
    padding: 10rpx 28rpx;
    border-radius: 24rpx;
    box-shadow: 0 4rpx 10rpx rgba(46, 126, 167, 0.2);
  }
}

.empty-state {
  padding: 160rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24rpx;
}

.empty-icon-box {
  width: 160rpx;
  height: 160rpx;
  background: #f1f5f9;
  border-radius: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8rpx;
}

.empty-text {
  font-size: 32rpx;
  font-weight: 600;
  color: #64748b;
}

.empty-subtext {
  font-size: 26rpx;
  color: #94a3b8;
}

.bottom-placeholder {
  height: 80rpx;
}
</style>
