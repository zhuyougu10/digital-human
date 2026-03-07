<template>
  <view class="login-page">
    <view class="bg"></view>
    <view class="content">
      <view class="logo-area">
        <view class="logo-placeholder">AI</view>
        <text class="app-name">智能医疗助手</text>
        <text class="app-slogan">专业 · 智能 · 便捷</text>
      </view>

      <view class="action-area">
        <button class="login-btn" @click="handleLogin">微信一键登录</button>
        <view class="protocol">
          <checkbox :checked="agreed" @click="agreed = !agreed" />
          <text class="text">我已阅读并同意 <text class="link">用户协议</text> 和 <text class="link">隐私政策</text></text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { wxLogin, getUserInfo } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const agreed = ref(false)

onLoad(() => {
  const token = uni.getStorageSync('token')
  if (token) {
    uni.reLaunch({
      url: '/pages/chat/chat'
    })
  }
})

const handleLogin = async () => {
  if (!agreed.value) {
    uni.showToast({ title: '请先同意协议', icon: 'none' })
    return
  }

  uni.showLoading({ title: '登录中...' })
  try {
    await wxLogin()
    const info = await getUserInfo()
    userStore.setUserInfo(info)
    uni.hideLoading()
    uni.reLaunch({
      url: '/pages/chat/chat'
    })
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: '登录失败', icon: 'none' })
    console.error('Login failed', e)
  }
}
</script>

<style scoped lang="scss">
.login-page {
  width: 100vw;
  height: 100vh;
  position: relative;
  overflow: hidden;
}

.bg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: $bg-gradient-full;
  z-index: 0;
}

.content {
  position: relative;
  z-index: 1;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 0 64rpx;
}

.logo-area {
  margin-top: -100rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.logo-placeholder {
  width: 160rpx;
  height: 160rpx;
  background: #ffffff;
  border-radius: 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 64rpx;
  font-weight: bold;
  color: #1E5AA8;
  margin-bottom: 32rpx;
  box-shadow: 0 16rpx 32rpx rgba(0, 0, 0, 0.1);
}

.app-name {
  font-size: 48rpx;
  font-weight: bold;
  color: #ffffff;
  margin-bottom: 12rpx;
}

.app-slogan {
  font-size: 28rpx;
  color: rgba(255, 255, 255, 0.8);
}

.action-area {
  position: absolute;
  bottom: 120rpx;
  width: 100%;
  padding: 0 64rpx;
  box-sizing: border-box;
}

.login-btn {
  width: 100%;
  height: 96rpx;
  background: #ffffff;
  color: #1E5AA8;
  border-radius: 48rpx;
  font-size: 32rpx;
  font-weight: bold;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.1);
  border: none;
}

.protocol {
  margin-top: 32rpx;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  
  .text {
    font-size: 24rpx;
    color: rgba(255, 255, 255, 0.7);
    margin-left: 8rpx;
    line-height: 36rpx;
  }
  
  .link {
    color: #ffffff;
    font-weight: 500;
  }
}
</style>
