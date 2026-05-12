<template>
  <view class="mine-page">
    <view class="profile-header">
      <view class="header-bg">
        <view class="bg-circle bg-circle-1"></view>
        <view class="bg-circle bg-circle-2"></view>
      </view>
      <view class="header-top">
        <text class="header-title">个人中心</text>
        <view class="header-right" @click="goSettings">
          <uni-icons type="settings" size="24" color="#FFFFFF"></uni-icons>
        </view>
      </view>
      <view class="profile-info">
        <view class="avatar-wrapper">
          <image class="avatar" :src="user.avatar || defaultAvatar" mode="aspectFill" />
          <view class="avatar-border"></view>
        </view>
        <view class="info-content">
          <text class="nickname">{{ user.nickname || '未登录用户' }}</text>
          <text class="phone">{{ user.phone || '绑定手机号，享受完整服务' }}</text>
        </view>
      </view>
    </view>

    <view class="profile-content">
      <view class="menu-group">
        <view class="menu-card">
          <view class="menu-item" hover-class="menu-item-hover" @click="go('/pages/appointment/list')">
            <view class="item-left">
              <view class="icon-box blue">
                <uni-icons type="calendar" size="20" color="var(--brand-primary)"></uni-icons>
              </view>
              <text class="item-text">我的预约</text>
            </view>
            <uni-icons type="right" size="14" color="#cbd5e1"></uni-icons>
          </view>
          <view class="divider"></view>
          <view class="menu-item" hover-class="menu-item-hover" @click="go('/pages/chat/chat')">
            <view class="item-left">
              <view class="icon-box green">
                <uni-icons type="chatbubble" size="20" color="var(--brand-success)"></uni-icons>
              </view>
              <text class="item-text">继续问安禾</text>
            </view>
            <uni-icons type="right" size="14" color="#cbd5e1"></uni-icons>
          </view>
          <view class="divider"></view>
          <view class="menu-item" hover-class="menu-item-hover" @click="showComingSoon">
            <view class="item-left">
              <view class="icon-box orange">
                <uni-icons type="wallet" size="20" color="var(--brand-warning)"></uni-icons>
              </view>
              <text class="item-text">就诊卡管理</text>
            </view>
            <uni-icons type="right" size="14" color="#cbd5e1"></uni-icons>
          </view>
        </view>

        <view class="menu-card">
          <view class="menu-item" hover-class="menu-item-hover" @click="showComingSoon">
            <view class="item-left">
              <view class="icon-box indigo">
                <uni-icons type="help" size="20" color="var(--brand-secondary)"></uni-icons>
              </view>
              <text class="item-text">帮助中心</text>
            </view>
            <uni-icons type="right" size="14" color="#cbd5e1"></uni-icons>
          </view>
          <view class="divider"></view>
          <view class="menu-item" hover-class="menu-item-hover" @click="showAbout">
            <view class="item-left">
              <view class="icon-box slate">
                <uni-icons type="info" size="20" color="var(--text-subtle)"></uni-icons>
              </view>
              <text class="item-text">关于我们</text>
            </view>
            <uni-icons type="right" size="14" color="#cbd5e1"></uni-icons>
          </view>
        </view>
      </view>

      <view class="logout-wrapper">
        <button class="logout-btn" hover-class="logout-btn-hover" @click="handleLogout">退出当前账号</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
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
    content: '确定要退出登录吗？',
    confirmColor: '#2563eb'
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
  background-color: var(--bg-page);
  display: flex;
  flex-direction: column;
  font-family: "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
}

.profile-header {
  position: relative;
  height: 480rpx;
  padding: 110rpx 40rpx 60rpx;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
  background: linear-gradient(135deg, var(--brand-primary) 0%, var(--brand-primary-hover) 100%);
}

.header-bg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
  
  .bg-circle {
    position: absolute;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.1);
  }
  
  .bg-circle-1 {
    width: 300rpx;
    height: 300rpx;
    top: -100rpx;
    right: -100rpx;
  }
  
  .bg-circle-2 {
    width: 200rpx;
    height: 200rpx;
    bottom: -50rpx;
    left: 10%;
    background: rgba(255, 255, 255, 0.05);
  }
}

.header-top {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-title {
  font-size: 44rpx;
  font-weight: 700;
  color: #FFFFFF;
  letter-spacing: 2rpx;
}

.profile-info {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 32rpx;
}

.avatar-wrapper {
  position: relative;
  width: 140rpx;
  height: 140rpx;
}

.avatar {
  width: 100%;
  height: 100%;
  border-radius: 70rpx;
  border: 4rpx solid rgba(255, 255, 255, 0.8);
  box-shadow: 0 8rpx 20rpx rgba(0, 0, 0, 0.15);
  background-color: var(--bg-muted);
}

.avatar-border {
  position: absolute;
  top: -10rpx;
  left: -10rpx;
  right: -10rpx;
  bottom: -10rpx;
  border-radius: 50%;
  border: 2rpx solid rgba(255, 255, 255, 0.2);
  pointer-events: none;
}

.info-content {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.nickname {
  font-size: 44rpx;
  font-weight: 600;
  color: #FFFFFF;
  text-shadow: 0 2rpx 4rpx rgba(0, 0, 0, 0.1);
}

.phone {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.9);
  background: rgba(255, 255, 255, 0.15);
  padding: 4rpx 16rpx;
  border-radius: 20rpx;
  width: fit-content;
}

.profile-content {
  position: relative;
  z-index: 2;
  margin-top: -40rpx;
  padding: 0 32rpx 80rpx;
  display: flex;
  flex-direction: column;
  gap: 32rpx;
}

.menu-group {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.menu-card {
  background: #FFFFFF;
  border-radius: 32rpx;
  padding: 8rpx 0;
  box-shadow: 0 8rpx 24rpx rgba(30, 41, 59, 0.04);
}

.menu-item {
  height: 120rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 32rpx;
  transition: background-color 0.2s;
  
  &:first-child {
    border-top-left-radius: 32rpx;
    border-top-right-radius: 32rpx;
  }
  
  &:last-child {
    border-bottom-left-radius: 32rpx;
    border-bottom-right-radius: 32rpx;
  }
}

.menu-item-hover {
  background-color: #f8fafc;
}

.item-left {
  display: flex;
  align-items: center;
  gap: 28rpx;
}

.icon-box {
  width: 72rpx;
  height: 72rpx;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  
  &.blue { background-color: rgba(37, 99, 235, 0.08); }
  &.green { background-color: rgba(65, 165, 106, 0.08); }
  &.orange { background-color: rgba(245, 158, 11, 0.08); }
  &.indigo { background-color: rgba(99, 102, 241, 0.08); }
  &.slate { background-color: rgba(100, 116, 139, 0.08); }
}

.item-text {
  font-size: 30rpx;
  font-weight: 500;
  color: var(--text-main);
}

.divider {
  height: 1rpx;
  background-color: var(--bg-muted);
  margin: 0 32rpx;
}

.logout-wrapper {
  margin-top: 20rpx;
}

.logout-btn {
  width: 100%;
  height: 100rpx;
  background-color: #FFFFFF;
  color: #ef4444;
  border-radius: 32rpx;
  font-size: 30rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1rpx solid #fee2e2;
  box-shadow: 0 4rpx 12rpx rgba(239, 68, 68, 0.05);
  transition: all 0.2s;
}

.logout-btn-hover {
  background-color: #fef2f2;
  transform: translateY(1rpx);
}
</style>
