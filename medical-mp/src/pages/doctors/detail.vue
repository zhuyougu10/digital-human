<template>
  <view class="page">
    <view class="header-card" v-if="doctor">
      <image class="avatar" :src="doctor.avatar || defaultAvatar" mode="aspectFill" />
      <view class="meta">
        <text class="name">{{ doctor.name }}</text>
        <text class="title">{{ doctor.title }}</text>
        <text class="intro">{{ doctor.introduction || '暂无简介' }}</text>
      </view>
    </view>

    <view class="section" v-if="doctor">
      <text class="section-title">擅长领域</text>
      <view class="tags">
        <text v-for="item in doctor.specialties" :key="item" class="tag">{{ item }}</text>
      </view>
    </view>

    <button class="consult-btn" @click="goChat">在线问诊</button>
  </view>
</template>

<script setup lang="ts">
// [Codex 降级接管] 医生详情页在 Gemini 不可用时由 Codex 接管实现。
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getDoctorById } from '@/api/doctor'

interface DoctorDetail {
  id: string | number
  name: string
  title: string
  avatar?: string
  introduction?: string
  specialties: string[]
}

const doctorId = ref<string>('')
const doctor = ref<DoctorDetail | null>(null)
const defaultAvatar = '/static/logo.png'

const parseSpecialties = (input: unknown) => {
  if (Array.isArray(input)) return input as string[]
  if (typeof input === 'string') return input.split(/[，,]/).filter(Boolean)
  return []
}

const fetchDoctorDetail = async () => {
  if (!doctorId.value) return
  try {
    const res = await getDoctorById(doctorId.value)
    const data = (res as any)?.data || res
    doctor.value = {
      id: data.id,
      name: data.name || data.doctorName || '未知医生',
      title: data.title || data.jobTitle || '',
      avatar: data.avatar,
      introduction: data.introduction || data.profile,
      specialties: parseSpecialties(data.specialties || data.expertise)
    }
  } catch {
    uni.showToast({ title: '医生详情加载失败', icon: 'none' })
  }
}

const goChat = () => {
  if (!doctorId.value) return
  uni.navigateTo({ url: `/pages/chat/chat?doctorId=${doctorId.value}` })
}

onLoad((options) => {
  doctorId.value = String(options?.id || '')
  fetchDoctorDetail()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f5f7fa;
  padding: 24rpx;
  box-sizing: border-box;
}

.header-card,
.section {
  background: #ffffff;
  border-radius: 12rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
}

.header-card {
  display: flex;
  gap: 18rpx;
}

.avatar {
  width: 128rpx;
  height: 128rpx;
  border-radius: 50%;
  background: #eef3f9;
}

.meta {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.name {
  font-size: 34rpx;
  color: #303133;
  font-weight: 700;
}

.title {
  font-size: 26rpx;
  color: #4a90d9;
}

.intro {
  font-size: 24rpx;
  color: #606266;
  line-height: 1.6;
}

.section-title {
  color: #303133;
  font-size: 28rpx;
  font-weight: 600;
}

.tags {
  margin-top: 14rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
}

.tag {
  padding: 8rpx 16rpx;
  border-radius: 999rpx;
  background: #ecf5ff;
  color: #4a90d9;
  font-size: 24rpx;
}

.consult-btn {
  margin-top: 24rpx;
  background: #4a90d9;
  border-radius: 10rpx;
  border: none;
}
</style>
