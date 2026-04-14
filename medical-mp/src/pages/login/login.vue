<template>
  <view class="login-page">
    <view class="brand-section">
      <text class="brand-title">数字人医疗助手</text>
      <text class="brand-subtitle">智能问诊与预约服务</text>
    </view>

    <view class="hero-card">
      <view class="hero-icon-wrapper">
        <uni-icons type="staff" size="48" color="#2563EB" class="hero-icon"></uni-icons>
      </view>
      <text class="hero-copy">我是你的医疗助手</text>
      <text class="hero-desc">登录后即可开始智能问诊与预约服务</text>
    </view>

    <view class="login-card">
      <button class="login-btn" hover-class="login-btn-hover" @click="handleLogin">
        <uni-icons type="weixin" size="20" color="#ffffff" class="btn-icon"></uni-icons>
        微信一键登录
      </button>
      <view class="protocol">
        <checkbox :checked="agreed" color="#2563EB" @click="agreed = !agreed" class="protocol-checkbox" />
        <view class="protocol-text">
          我已阅读并同意 <text class="link" @click.stop="showProtocol('user')">用户协议</text> 和 <text class="link" @click.stop="showProtocol('privacy')">隐私政策</text>
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

const showProtocol = (type) => {
  // 协议展示逻辑，不修改原脚本逻辑，仅作为模板占位
}
</script>

<style scoped lang="scss">
.login-page {
  width: 100vw;
  height: 100vh;
  position: relative;
  overflow: hidden;
  background-color: var(--bg-page, #F8FAFC);
  display: flex;
  flex-direction: column;
  padding: 0 48rpx;
  box-sizing: border-box;
}

.brand-section {
  margin-top: 160rpx;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  
  .brand-title {
    font-size: 56rpx;
    font-weight: 600;
    color: var(--text-main, #1E293B);
    margin-bottom: 16rpx;
    letter-spacing: 2rpx;
  }
  
  .brand-subtitle {
    font-size: 32rpx;
    color: var(--text-regular, #475569);
    font-weight: 400;
  }
}

.hero-card {
  margin-top: 80rpx;
  background: var(--bg-card, #FFFFFF);
  border-radius: var(--radius-lg, 24rpx);
  padding: 64rpx 48rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: var(--shadow-sm, 0 2rpx 8rpx rgba(30, 41, 59, 0.04));
  border: 2rpx solid var(--border-color, #E2E8F0);

  .hero-icon-wrapper {
    width: 160rpx;
    height: 160rpx;
    background: var(--brand-primary-soft, #EFF6FF);
    border-radius: var(--radius-full, 9999rpx);
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 40rpx;
  }

  .hero-copy {
    font-size: 40rpx;
    font-weight: 600;
    color: var(--text-main, #1E293B);
    margin-bottom: 16rpx;
  }
  
  .hero-desc {
    font-size: 28rpx;
    color: var(--text-subtle, #94A3B8);
    text-align: center;
    line-height: 1.5;
  }
}

.login-card {
  margin-top: auto;
  margin-bottom: 120rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
}

.login-btn {
  width: 100%;
  height: 96rpx;
  background: var(--brand-primary, #2563EB);
  color: #ffffff;
  border-radius: var(--radius-md, 16rpx);
  font-size: 32rpx;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  box-shadow: 0 8rpx 16rpx rgba(37, 99, 235, 0.15);
  transition: all 0.2s ease;
  
  .btn-icon {
    margin-right: 16rpx;
  }
}

.login-btn-hover {
  background: var(--brand-primary-hover, #1D4ED8);
  transform: translateY(2rpx);
  box-shadow: 0 4rpx 8rpx rgba(37, 99, 235, 0.1);
}

.protocol {
  margin-top: 32rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  
  .protocol-checkbox {
    transform: scale(0.7);
    margin-right: -4rpx;
  }
  
  .protocol-text {
    font-size: 24rpx;
    color: var(--text-regular, #475569);
    line-height: 40rpx;
  }
  
  .link {
    color: var(--brand-primary, #2563EB);
    font-weight: 500;
    margin: 0 4rpx;
    padding: 0 4rpx;
  }
}
</style>
