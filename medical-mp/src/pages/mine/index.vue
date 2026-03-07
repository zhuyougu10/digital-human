<template>
  <view class="mine-page">
    <view class="profile-header">
      <view class="header-top">
        <text class="header-title">个人中心</text>
        <view class="header-right" @click="goSettings">
          <uni-icons type="settings" size="24" color="#FFFFFF"></uni-icons>
        </view>
      </view>
      <view class="profile-info">
        <image class="avatar" :src="user.avatar || defaultAvatar" mode="aspectFill" />
        <view class="info-content">
          <text class="nickname">{{ user.nickname || '未登录用户' }}</text>
          <text class="phone">{{ user.phone || '绑定手机号，享受完整服务' }}</text>
        </view>
      </view>
    </view>

    <view class="profile-content">
      <view class="menu-card">
        <view class="menu-item" @click="go('/pages/appointment/list')">
          <view class="item-left">
            <uni-icons type="calendar" size="20" color="#1E5AA8"></uni-icons>
            <text class="item-text">我的预约</text>
          </view>
          <uni-icons type="right" size="16" color="#9CA3AF"></uni-icons>
        </view>
        <view class="divider"></view>
        <view class="menu-item" @click="go('/pages/chat/chat')">
          <view class="item-left">
            <uni-icons type="chatbubble" size="20" color="#1E5AA8"></uni-icons>
            <text class="item-text">对话记录</text>
          </view>
          <uni-icons type="right" size="16" color="#9CA3AF"></uni-icons>
        </view>
        <view class="divider"></view>
        <view class="menu-item" @click="showComingSoon">
          <view class="item-left">
            <uni-icons type=" wallet" size="20" color="#1E5AA8"></uni-icons>
            <text class="item-text">就诊卡管理</text>
          </view>
          <uni-icons type="right" size="16" color="#9CA3AF"></uni-icons>
        </view>
      </view>

      <view class="menu-card">
        <view class="menu-item" @click="showComingSoon">
          <view class="item-left">
            <uni-icons type="help" size="20" color="#1E5AA8"></uni-icons>
            <text class="item-text">帮助中心</text>
          </view>
          <uni-icons type="right" size="16" color="#9CA3AF"></uni-icons>
        </view>
        <view class="divider"></view>
        <view class="menu-item" @click="showAbout">
          <view class="item-left">
            <uni-icons type="info" size="20" color="#1E5AA8"></uni-icons>
            <text class="item-text">关于我们</text>
          </view>
          <uni-icons type="right" size="16" color="#9CA3AF"></uni-icons>
        </view>
      </view>

      <button class="logout-btn" @click="handleLogout">退出登录</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { logout } from '@/api/auth'

const defaultAvatar = '/static/logo.png'
const user = reactive({
  avatar: '',
  nickname: '',
  phone: ''
})

const loadUser = () => {
  const userInfo = uni.getStorageSync('userInfo') || {}
  user.avatar = userInfo.avatar || userInfo.avatarUrl || ''
  user.nickname = userInfo.nickname || userInfo.nickName || ''
  user.phone = userInfo.phone || ''
}

const go = (url: string) => {
  uni.navigateTo({ url })
}

const goSettings = () => {
  uni.showToast({ title: '设置功能开发中', icon: 'none' })
}

const showComingSoon = () => {
  uni.showToast({ title: '功能即将上线', icon: 'none' })
}

const showAbout = () => {
  uni.showModal({
    title: '关于',
    content: 'AI 数字人医疗小助手 v1.0.0',
    showCancel: false
  })
}

const handleLogout = async () => {
  const res = await uni.showModal({
    title: '提示',
    content: '确定要退出登录吗？'
  })
  if (res.confirm) {
    try {
      await logout()
      uni.reLaunch({ url: '/pages/login/login' })
    } catch (e) {
      uni.showToast({ title: '登出失败', icon: 'none' })
    }
  }
}

onShow(loadUser)
</script>

<style scoped lang="scss">
.mine-page {
  min-height: 100vh;
  background: $uni-bg-color-grey;
  display: flex;
  flex-direction: column;
}

.profile-header {
  height: 460rpx;
  background: $bg-gradient;
  padding: 120rpx 32rpx 48rpx;
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
  font-size: 40rpx;
  font-weight: 700;
  color: #FFFFFF;
}

.profile-info {
  display: flex;
  align-items: center;
  gap: 32rpx;
}

.avatar {
  width: 128rpx;
  height: 128rpx;
  border-radius: 64rpx;
  border: 4rpx solid rgba(255, 255, 255, 0.4);
}

.info-content {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.nickname {
  font-size: 40rpx;
  font-weight: 600;
  color: #FFFFFF;
}

.phone {
  font-size: 28rpx;
  color: rgba(255, 255, 255, 0.8);
}

.profile-content {
  margin-top: -40rpx;
  padding: 0 32rpx 60rpx;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.menu-card {
  background: #FFFFFF;
  border-radius: 24rpx;
  padding: 0 8rpx;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.05);
}

.menu-item {
  height: 112rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24rpx;
}

.item-left {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.item-text {
  font-size: 30rpx;
  color: #1F2937;
}

.divider {
  height: 1rpx;
  background: #F3F4F6;
  margin: 0 24rpx;
}

.logout-btn {
  margin-top: 40rpx;
  height: 96rpx;
  background: #FFFFFF;
  color: #EF4444;
  border-radius: 24rpx;
  font-size: 32rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.05);
}
</style>
