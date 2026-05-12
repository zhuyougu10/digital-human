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
            <uni-icons :type="item.icon" size="28" color="var(--brand-primary)"></uni-icons>
          </view>
          <text class="grid-name">{{ item.name }}</text>
        </view>
      </view>

      <view class="health-encyclopedia">
        <view class="section-header">
          <text class="section-title">健康科普</text>
          <text class="section-more" @tap="showMore">更多 <uni-icons type="right" size="12" color="var(--text-light)"></uni-icons></text>
        </view>
        <view class="article-list">
          <view class="article-item" v-for="i in 2" :key="i">
            <view class="article-img-placeholder">
              <uni-icons type="image" size="24" color="var(--border-color)"></uni-icons>
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
  background:
    radial-gradient(circle at top right, rgba(37, 99, 235, 0.08), transparent 24%),
    linear-gradient(180deg, #f8fbff 0%, #f7fafc 100%);
  display: flex;
  flex-direction: column;
}

.home-header {
  height: 480rpx;
  background: linear-gradient(135deg, var(--brand-primary) 0%, var(--brand-secondary) 100%);
  padding: 88rpx 32rpx 32rpx;
  border-bottom-left-radius: 48rpx;
  border-bottom-right-radius: 48rpx;
  box-shadow: 0 18rpx 40rpx rgba(37, 99, 235, 0.12);
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
  background: rgba(255, 255, 255, 0.12);
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
  background: rgba(255, 255, 255, 0.9);
  border-radius: 36rpx;
  padding: 40rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: var(--shadow-md);
  border: 1rpx solid rgba(226, 232, 240, 0.9);
  backdrop-filter: blur(12rpx);
}

.card-info {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.card-title {
  font-size: 40rpx;
  font-weight: 700;
  color: var(--text-main);
}

.card-desc {
  font-size: 24rpx;
  color: var(--text-subtle);
}

.card-tag {
  margin-top: 12rpx;
  width: fit-content;
  padding: 8rpx 24rpx;
  background: var(--brand-primary-soft);
  color: var(--brand-primary);
  font-size: 24rpx;
  font-weight: 600;
  border-radius: 9999rpx;
}

.ai-avatar-preview {
  width: 120rpx;
  height: 120rpx;
  background: linear-gradient(135deg, var(--brand-primary) 0%, var(--brand-secondary) 100%);
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
  border: 2rpx solid rgba(255, 255, 255, 0.5);
  border-radius: 70rpx;
  opacity: 0.45;
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
  background: rgba(255, 255, 255, 0.92);
  border-radius: 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-sm);
  border: 1rpx solid rgba(226, 232, 240, 0.8);
}

.grid-name {
  font-size: 24rpx;
  color: var(--text-regular);
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
  color: var(--text-main);
}

.section-more {
  font-size: 24rpx;
  color: var(--text-subtle);
}

.article-list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.article-item {
  background: rgba(255, 255, 255, 0.92);
  border-radius: 28rpx;
  padding: 24rpx;
  display: flex;
  gap: 24rpx;
  border: 1rpx solid rgba(226, 232, 240, 0.82);
  box-shadow: var(--shadow-sm);
}

.article-img-placeholder {
  width: 180rpx;
  height: 140rpx;
  background: linear-gradient(135deg, #eff6ff 0%, #e0f2fe 100%);
  border-radius: 18rpx;
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
  color: var(--text-main);
}

.article-desc {
  font-size: 24rpx;
  color: var(--text-subtle);
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
  color: var(--brand-primary);
  background: var(--brand-primary-soft);
  padding: 4rpx 12rpx;
  border-radius: 9999rpx;
}

.time {
  font-size: 20rpx;
  color: var(--text-subtle);
}
</style>
