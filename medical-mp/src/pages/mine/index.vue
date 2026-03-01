<template>
  <view class="page">
    <view class="user-card">
      <image class="avatar" :src="user.avatar || defaultAvatar" mode="aspectFill" />
      <text class="nickname">{{ user.nickname || '未登录用户' }}</text>
    </view>

    <view class="menu-card">
      <view class="menu-item" @click="go('/pages/appointment/list')">我的预约</view>
      <view class="menu-item" @click="go('/pages/chat/chat')">对话记录</view>
      <view class="menu-item" @click="showDeveloping">设置</view>
      <view class="menu-item" @click="showDeveloping">关于</view>
    </view>

    <button class="logout-btn" @click="logout">退出登录</button>
  </view>
</template>

<script setup lang="ts">
// [Codex 降级接管] 个人中心页在 Gemini 不可用时由 Codex 接管实现。
import { reactive } from 'vue'
import { onShow } from '@dcloudio/uni-app'

const defaultAvatar = '/static/logo.png'
const user = reactive({
  avatar: '',
  nickname: ''
})

const loadUser = () => {
  const userInfo = uni.getStorageSync('userInfo') || {}
  user.avatar = userInfo.avatar || userInfo.avatarUrl || ''
  user.nickname = userInfo.nickname || userInfo.nickName || ''
}

const go = (url: string) => {
  uni.navigateTo({ url })
}

const showDeveloping = () => {
  uni.showToast({ title: '功能开发中', icon: 'none' })
}

const logout = () => {
  uni.removeStorageSync('token')
  uni.removeStorageSync('userInfo')
  uni.reLaunch({ url: '/pages/index/index' })
}

onShow(loadUser)
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f5f7fa;
  padding: 24rpx;
  box-sizing: border-box;
}

.user-card,
.menu-card {
  background: #ffffff;
  border-radius: 12rpx;
  padding: 24rpx;
}

.user-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 20rpx;
}

.avatar {
  width: 140rpx;
  height: 140rpx;
  border-radius: 50%;
  background: #eef3f9;
}

.nickname {
  color: #303133;
  font-size: 30rpx;
  font-weight: 700;
}

.menu-item {
  padding: 24rpx 4rpx;
  border-bottom: 1rpx solid #f2f6fc;
  color: #303133;
  font-size: 28rpx;
}

.menu-item:last-child {
  border-bottom: 0;
}

.logout-btn {
  margin-top: 30rpx;
  background: #f56c6c;
  color: #ffffff;
  border-radius: 10rpx;
  border: none;
}
</style>
