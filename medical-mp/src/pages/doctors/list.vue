<template>
  <view class="page">
    <scroll-view class="dept-tabs" scroll-x>
      <view
        class="dept-tab"
        :class="{ active: !activeDepartmentId }"
        @click="activeDepartmentId = ''"
      >
        全部
      </view>
      <view
        v-for="item in departments"
        :key="item.id"
        class="dept-tab"
        :class="{ active: item.id === activeDepartmentId }"
        @click="activeDepartmentId = item.id"
      >
        {{ item.name }}
      </view>
    </scroll-view>

    <view class="list">
      <view
        v-for="doctor in filteredDoctors"
        :key="doctor.id"
        class="doctor-item"
        @click="goDetail(doctor.id)"
      >
        <image class="avatar" :src="doctor.avatar || defaultAvatar" mode="aspectFill" />
        <view class="info">
          <view class="name-row">
            <text class="name">{{ doctor.name }}</text>
            <text class="title">{{ doctor.title }}</text>
          </view>
          <text class="specialties">{{ doctor.specialties.join(' / ') }}</text>
        </view>
      </view>
      <view v-if="!filteredDoctors.length" class="empty">暂无医生</view>
    </view>
  </view>
</template>

<script setup lang="ts">
// [Codex 降级接管] 医生列表页在 Gemini 不可用时由 Codex 接管实现。
import { computed, onMounted, ref } from 'vue'
import { getDepartmentList, getDoctorList } from '@/api/doctor'

interface Department {
  id: string | number
  name: string
}

interface Doctor {
  id: string | number
  name: string
  title: string
  avatar?: string
  specialties: string[]
  departmentId?: string | number
}

const departments = ref<Department[]>([])
const doctors = ref<Doctor[]>([])
const activeDepartmentId = ref<string | number>('')
const defaultAvatar = '/static/logo.png'

const filteredDoctors = computed(() => {
  if (!activeDepartmentId.value) {
    return doctors.value
  }
  return doctors.value.filter((item) => item.departmentId === activeDepartmentId.value)
})

const normalizeSpecialties = (specialties: unknown): string[] => {
  if (Array.isArray(specialties)) return specialties as string[]
  if (typeof specialties === 'string') return specialties.split(/[，,]/).filter(Boolean)
  return []
}

const unwrapList = (payload: any): any[] => {
  if (Array.isArray(payload)) return payload
  if (Array.isArray(payload?.data)) return payload.data
  if (Array.isArray(payload?.data?.records)) return payload.data.records
  if (Array.isArray(payload?.records)) return payload.records
  if (Array.isArray(payload?.list)) return payload.list
  return []
}

const fetchDepartments = async () => {
  const res = await getDepartmentList()
  const list = unwrapList(res)
  departments.value = list.map((item) => ({
    id: item.id,
    name: item.name || item.departmentName || '未命名科室'
  }))
}

const fetchDoctors = async () => {
  const res = await getDoctorList({})
  const list = unwrapList(res)
  doctors.value = list.map((item) => ({
    id: item.id,
    name: item.name || item.doctorName || '未知医生',
    title: item.title || item.jobTitle || '',
    avatar: item.avatar,
    specialties: normalizeSpecialties(item.specialties || item.expertise),
    departmentId: item.departmentId
  }))
}

const goDetail = (id: string | number) => {
  uni.navigateTo({ url: `/pages/doctors/detail?id=${id}` })
}

onMounted(async () => {
  try {
    await Promise.all([fetchDepartments(), fetchDoctors()])
  } catch {
    uni.showToast({ title: '加载医生数据失败', icon: 'none' })
  }
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f5f7fa;
  padding: 20rpx;
  box-sizing: border-box;
}

.dept-tabs {
  white-space: nowrap;
  margin-bottom: 20rpx;
}

.dept-tab {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-right: 12rpx;
  padding: 12rpx 22rpx;
  border-radius: 999rpx;
  background: #ffffff;
  color: #606266;
  font-size: 24rpx;
}

.dept-tab.active {
  color: #ffffff;
  background: #4a90d9;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.doctor-item {
  display: flex;
  gap: 16rpx;
  padding: 20rpx;
  background: #ffffff;
  border-radius: 12rpx;
}

.avatar {
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  background: #eef3f9;
}

.info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.name-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.name {
  font-size: 32rpx;
  color: #303133;
  font-weight: 700;
}

.title {
  font-size: 24rpx;
  color: #4a90d9;
  background: #ecf5ff;
  border-radius: 999rpx;
  padding: 4rpx 12rpx;
}

.specialties {
  color: #606266;
  font-size: 24rpx;
}

.empty {
  text-align: center;
  color: #909399;
  margin-top: 80rpx;
}
</style>
