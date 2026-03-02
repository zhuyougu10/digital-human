<script setup>
import { ref, onMounted } from 'vue'
import { wxLogin, getUserInfo } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const greeting = ref('你好')

// 首页快捷入口（固定配置，无需后端动态化）
const quickActions = [
  { id: 1, name: '找医生', icon: 'hospital', path: '/pages/doctors/list' },
  { id: 2, name: '我的预约', icon: 'calendar', path: '/pages/appointment/list' },
  { id: 3, name: '健康科普', icon: 'book', path: '/pages/chat/chat' }
]

const initAuth = async () => {
  const token = uni.getStorageSync('token')
  if (!token) {
    try {
      await wxLogin()
      const info = await getUserInfo()
      userStore.setUserInfo(info)
    } catch (e) {
      console.error('Login failed', e)
    }
  } else if (!userStore.userInfo.id) {
    try {
      const info = await getUserInfo()
      userStore.setUserInfo(info)
    } catch (e) {
      console.error('Fetch user info failed', e)
    }
  }
}

const startChat = () => {
  uni.navigateTo({
    url: '/pages/chat/chat'
  })
}

const navigateTo = (path) => {
  uni.navigateTo({ url: path })
}

onMounted(() => {
  const hour = new Date().getHours()
  if (hour < 12) greeting.value = '早上好'
  else if (hour < 18) greeting.value = '下午好'
  else greeting.value = '晚上好'
  
  initAuth()
})
</script>

<template>
  <view class="container">
    <view class="header">
      <view class="user-info">
        <image class="avatar" :src="userStore.userInfo.avatar || '/static/logo.png'" mode="aspectFill" />
        <view class="welcome-text">
          <text class="greeting">{{ greeting }}，{{ userStore.userInfo.nickname || '新用户' }}</text>
          <text class="subtitle">今天有什么可以帮您的？</text>
        </view>
      </view>
    </view>

    <view class="main-action">
      <view class="chat-card" @tap="startChat">
        <view class="chat-info">
          <text class="title">AI 智能问诊</text>
          <text class="desc">专业导诊，为您推荐合适科室和医生</text>
        </view>
        <view class="btn-start">开始问诊</view>
      </view>
    </view>

    <view class="quick-grid">
      <view v-for="item in quickActions" :key="item.id" class="grid-item" @tap="navigateTo(item.path)">
        <view class="icon-placeholder">{{ item.name[0] }}</view>
        <text class="item-name">{{ item.name }}</text>
      </view>
    </view>
  </view>
</template>

<style scoped>
.container {
  min-height: 100vh;
  background-color: #f5f7fa;
  padding: 30rpx;
}

.header {
  margin-top: 40rpx;
  margin-bottom: 60rpx;
}

.user-info {
  display: flex;
  align-items: center;
}

.avatar {
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  background-color: #eee;
  margin-right: 20rpx;
}

.welcome-text {
  display: flex;
  flex-direction: column;
}

.greeting {
  font-size: 36rpx;
  font-weight: bold;
  color: #333;
}

.subtitle {
  font-size: 24rpx;
  color: #666;
  margin-top: 8rpx;
}

.main-action {
  margin-bottom: 40rpx;
}

.chat-card {
  background: linear-gradient(135deg, #4A90D9 0%, #357ABD 100%);
  border-radius: 20rpx;
  padding: 40rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: white;
  box-shadow: 0 10rpx 20rpx rgba(74, 144, 217, 0.3);
}

.chat-info {
  flex: 1;
}

.title {
  font-size: 36rpx;
  font-weight: bold;
  display: block;
}

.desc {
  font-size: 24rpx;
  opacity: 0.9;
  margin-top: 10rpx;
  display: block;
}

.btn-start {
  background-color: white;
  color: #4A90D9;
  padding: 16rpx 32rpx;
  border-radius: 40rpx;
  font-size: 28rpx;
  font-weight: bold;
}

.quick-grid {
  display: flex;
  justify-content: space-between;
}

.grid-item {
  flex: 1;
  background-color: white;
  border-radius: 20rpx;
  padding: 30rpx;
  margin: 0 10rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 4rpx 10rpx rgba(0, 0, 0, 0.05);
}

.icon-placeholder {
  width: 80rpx;
  height: 80rpx;
  background-color: #eef5fd;
  border-radius: 20rpx;
  color: #4A90D9;
  font-size: 36rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16rpx;
}

.item-name {
  font-size: 26rpx;
  color: #333;
}
</style>
