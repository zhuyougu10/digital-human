<template>
  <view class="home-page">
    <view class="home-header">
      <view class="header-content">
        <view class="user-info">
          <image class="avatar" :src="userStore.userInfo.avatar || '/static/logo.png'" mode="aspectFill" />
          <view class="welcome-text">
            <text class="greeting">{{ greeting }}，{{ userStore.userInfo.nickname || '用户' }}</text>
            <text class="subtitle">您的健康，我们时刻守护</text>
          </view>
        </view>
        <view class="notice-bar">
          <uni-icons type="notification" size="18" color="#FFFFFF"></uni-icons>
          <text class="notice-text">今日挂号已开放，请及早预约</text>
        </view>
      </view>
    </view>

    <view class="home-content">
      <view class="main-card" @tap="startChat">
        <view class="card-info">
          <text class="card-title">AI 智能问诊</text>
          <text class="card-desc">24小时在线 · 虚拟数字人 · 快速导诊</text>
          <view class="card-tag">点击进入</view>
        </view>
        <view class="ai-avatar-preview">
          <view class="avatar-glow"></view>
          <text class="avatar-text">AI</text>
        </view>
      </view>

      <view class="quick-grid">
        <view v-for="item in quickActions" :key="item.id" class="grid-item" @tap="navigateTo(item.path)">
          <view class="grid-icon">
            <uni-icons :type="item.icon" size="28" color="#1E5AA8"></uni-icons>
          </view>
          <text class="grid-name">{{ item.name }}</text>
        </view>
      </view>

      <view class="health-encyclopedia">
        <view class="section-header">
          <text class="section-title">健康科普</text>
          <text class="section-more" @tap="showMore">更多 <uni-icons type="right" size="12" color="#9CA3AF"></uni-icons></text>
        </view>
        <view class="article-list">
          <view class="article-item" v-for="i in 2" :key="i">
            <view class="article-img-placeholder">
              <uni-icons type="image" size="24" color="#D1D5DB"></uni-icons>
            </view>
            <view class="article-info">
              <text class="article-title">春季过敏性鼻炎预防指南</text>
              <text class="article-desc">春暖花开，过敏性鼻炎患者该如何做好防护措施？</text>
              <view class="article-footer">
                <text class="tag">健康防护</text>
                <text class="time">2026-03-07</text>
              </view>
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { wxLogin, getUserInfo } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const greeting = ref('你好')

const quickActions = [
  { id: 1, name: '找医生', icon: 'person-filled', path: '/pages/doctors/list' },
  { id: 2, name: '我的预约', icon: 'calendar-filled', path: '/pages/appointment/list' },
  { id: 3, name: '就诊助手', icon: 'info-filled', path: '/pages/chat/chat' },
  { id: 4, name: '检查结果', icon: 'paperclip', path: '/pages/chat/chat' }
]

const startChat = () => {
  uni.navigateTo({
    url: '/pages/chat/chat'
  })
}

const navigateTo = (path) => {
  uni.navigateTo({ url: path })
}

const showMore = () => {
  uni.showToast({ title: '更多内容即将上线', icon: 'none' })
}

onMounted(() => {
  const hour = new Date().getHours()
  if (hour < 12) greeting.value = '早上好'
  else if (hour < 18) greeting.value = '下午好'
  else greeting.value = '晚上好'
})
</script>

<style scoped lang="scss">
.home-page {
  min-height: 100vh;
  background: $uni-bg-color-grey;
  display: flex;
  flex-direction: column;
}

.home-header {
  height: 480rpx;
  background: $bg-gradient;
  padding: 88rpx 32rpx 32rpx;
  border-bottom-left-radius: 40rpx;
  border-bottom-right-radius: 40rpx;
}

.header-content {
  display: flex;
  flex-direction: column;
  gap: 32rpx;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.avatar {
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  border: 4rpx solid rgba(255, 255, 255, 0.4);
}

.welcome-text {
  display: flex;
  flex-direction: column;
}

.greeting {
  font-size: 36rpx;
  font-weight: 700;
  color: #FFFFFF;
}

.subtitle {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.8);
  margin-top: 4rpx;
}

.notice-bar {
  height: 72rpx;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 36rpx;
  display: flex;
  align-items: center;
  padding: 0 24rpx;
  gap: 16rpx;
}

.notice-text {
  font-size: 26rpx;
  color: #FFFFFF;
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.home-content {
  margin-top: -80rpx;
  padding: 0 32rpx 40rpx;
  display: flex;
  flex-direction: column;
  gap: 32rpx;
}

.main-card {
  height: 240rpx;
  background: #FFFFFF;
  border-radius: 32rpx;
  padding: 40rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 16rpx 32rpx rgba(0, 0, 0, 0.05);
}

.card-info {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.card-title {
  font-size: 40rpx;
  font-weight: 700;
  color: #1E5AA8;
}

.card-desc {
  font-size: 24rpx;
  color: #6B7280;
}

.card-tag {
  margin-top: 12rpx;
  width: fit-content;
  padding: 8rpx 24rpx;
  background: #EFF6FF;
  color: #1E5AA8;
  font-size: 24rpx;
  font-weight: 600;
  border-radius: 20rpx;
}

.ai-avatar-preview {
  width: 120rpx;
  height: 120rpx;
  background: $bg-gradient;
  border-radius: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.avatar-glow {
  position: absolute;
  width: 140rpx;
  height: 140rpx;
  border: 2rpx solid #3B82F6;
  border-radius: 70rpx;
  opacity: 0.3;
}

.avatar-text {
  font-size: 48rpx;
  font-weight: bold;
  color: #FFFFFF;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20rpx;
}

.grid-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}

.grid-icon {
  width: 100rpx;
  height: 100rpx;
  background: #FFFFFF;
  border-radius: 32rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 16rpx rgba(0, 0, 0, 0.03);
}

.grid-name {
  font-size: 24rpx;
  color: #4B5563;
}

.health-encyclopedia {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #1F2937;
}

.section-more {
  font-size: 24rpx;
  color: #9CA3AF;
}

.article-list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.article-item {
  background: #FFFFFF;
  border-radius: 24rpx;
  padding: 24rpx;
  display: flex;
  gap: 24rpx;
}

.article-img-placeholder {
  width: 180rpx;
  height: 140rpx;
  background: #F3F4F6;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.article-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.article-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #1F2937;
}

.article-desc {
  font-size: 24rpx;
  color: #6B7280;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.article-footer {
  margin-top: 8rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tag {
  font-size: 20rpx;
  color: #1E5AA8;
  background: #EFF6FF;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}

.time {
  font-size: 20rpx;
  color: #9CA3AF;
}
</style>
