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

type EntityId = string | number

interface DepartmentItem {
  id: EntityId
  name: string
}

interface DoctorItem {
  id: EntityId
  avatar?: string
  name: string
  title?: string
  departmentId?: EntityId
  departmentName?: string
  fee?: number | string
  specialties: string[]
}

interface RawDepartment {
  id?: EntityId
  name?: string
  departmentName?: string
}

interface RawDoctor {
  id?: EntityId
  avatar?: string
  name?: string
  title?: string
  departmentId?: EntityId
  departmentName?: string
  fee?: number | string
  specialties?: string | string[]
}

const departments = ref<DepartmentItem[]>([])
const doctors = ref<DoctorItem[]>([])
const activeDeptId = ref<EntityId | ''>('')
const defaultAvatar = '/static/logo.png'

const goBack = () => uni.navigateBack()

const filteredDoctors = computed(() => {
  if (!activeDeptId.value) return doctors.value
  return doctors.value.filter((d) => d.departmentId === activeDeptId.value)
})

const fetchDepartments = async () => {
  const res = await getDepartmentList()
  const list: RawDepartment[] = Array.isArray(res) ? res : (res.records || [])
  departments.value = list.map((d) => ({
    id: d.id ?? '',
    name: d.name || d.departmentName || '未命名科室'
  }))
}

const fetchDoctors = async () => {
  const res = await getDoctorList({})
  const list: RawDoctor[] = Array.isArray(res) ? res : (res.records || [])
  doctors.value = list.map((d) => ({
    id: d.id ?? '',
    avatar: d.avatar,
    name: d.name || '未命名医生',
    title: d.title || '',
    departmentId: d.departmentId,
    departmentName: d.departmentName || '',
    fee: d.fee,
    specialties: Array.isArray(d.specialties)
      ? d.specialties.filter((item): item is string => typeof item === 'string' && item.length > 0)
      : String(d.specialties || '').split(/[，,]/).filter(Boolean)
  }))
}

const goDetail = (id: EntityId) => {
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
  background:
    radial-gradient(circle at top right, rgba(37, 99, 235, 0.08), transparent 22%),
    linear-gradient(180deg, #f8fbff 0%, #f7fafc 100%);
  font-family: "PingFang SC", "Hiragino Sans GB", "Noto Sans SC", "Microsoft YaHei", sans-serif;
}

.dr-header {
  height: 480rpx;
  background: linear-gradient(135deg, var(--brand-primary) 0%, var(--brand-secondary) 100%);
  padding: 88rpx 32rpx 32rpx;
  display: flex;
  flex-direction: column;
  box-shadow: 0 18rpx 40rpx rgba(37, 99, 235, 0.12);
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
  background: rgba(255, 255, 255, 0.92);
  border-radius: 70rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 14rpx 30rpx rgba(15, 23, 42, 0.10);
  border: 4rpx solid rgba(255, 255, 255, 0.35);
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
  background: transparent;
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
  background: rgba(255, 255, 255, 0.88);
  color: var(--text-subtle);
  font-size: 26rpx;
  border-radius: 9999rpx;
  border: 1rpx solid rgba(226, 232, 240, 0.9);
  transition: all 0.3s;
  
  &.active {
    background: linear-gradient(135deg, var(--brand-primary) 0%, var(--brand-secondary) 100%);
    color: #FFFFFF;
    box-shadow: 0 8rpx 20rpx rgba(37, 99, 235, 0.18);
  }
}

.doctor-list {
  display: flex;
  flex-direction: column;
  gap: 28rpx;
}

.doctor-card {
  background: rgba(255, 255, 255, 0.94);
  border-radius: 28rpx;
  padding: 32rpx 24rpx;
  display: flex;
  align-items: center;
  gap: 24rpx;
  box-shadow: var(--shadow-sm);
  border: 1rpx solid rgba(226, 232, 240, 0.88);
  
  &:active {
    transform: scale(0.98);
    background: #ffffff;
  }
}

.card-left {
  flex-shrink: 0;
  
  .avatar {
    width: 120rpx;
    height: 120rpx;
    border-radius: 60rpx;
    background: var(--brand-primary-soft);
    border: 4rpx solid rgba(219, 234, 254, 0.8);
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
    color: var(--text-main);
  }
  
  .title-tag {
    font-size: 22rpx;
    color: var(--brand-primary);
    background: var(--brand-primary-soft);
    padding: 2rpx 12rpx;
    border-radius: 9999rpx;
  }
  
  .dept-name {
    font-size: 26rpx;
    color: var(--text-subtle);
  }
  
  .specialty-tags {
    display: flex;
    gap: 12rpx;
    margin-top: 6rpx;
    
    .tag {
      font-size: 22rpx;
      color: var(--text-subtle);
      background: var(--bg-muted);
      padding: 4rpx 16rpx;
      border-radius: 9999rpx;
      border: 1rpx solid rgba(226, 232, 240, 0.8);
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
    color: var(--text-subtle);
  }
  
  .fee-amount {
    font-size: 28rpx;
    font-weight: 700;
    color: var(--brand-primary);
  }
  
  .book-btn {
    background: linear-gradient(135deg, var(--brand-primary) 0%, var(--brand-secondary) 100%);
    color: #ffffff;
    font-size: 24rpx;
    font-weight: 600;
    padding: 10rpx 28rpx;
    border-radius: 9999rpx;
    box-shadow: 0 8rpx 18rpx rgba(37, 99, 235, 0.18);
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
  background: var(--bg-muted);
  border-radius: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8rpx;
}

.empty-text {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--text-main);
}

.empty-subtext {
  font-size: 26rpx;
  color: var(--text-subtle);
}

.bottom-placeholder {
  height: 80rpx;
}
</style>
