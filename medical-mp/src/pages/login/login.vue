<template>
  <view class="login-page">
    <view class="bg-decoration">
      <view class="circle circle-1"></view>
      <view class="circle circle-2"></view>
    </view>
    <view class="content">
      <view class="logo-area">
        <view class="logo-wrapper">
          <view class="logo-placeholder">AI</view>
          <view class="logo-glow"></view>
        </view>
        <text class="app-name">智能医疗助手</text>
        <view class="slogan-wrapper">
          <text class="slogan-item">专业</text>
          <text class="slogan-divider">·</text>
          <text class="slogan-item">智能</text>
          <text class="slogan-divider">·</text>
          <text class="slogan-item">便捷</text>
        </view>
      </view>

      <view class="action-area">
        <button class="login-btn" hover-class="login-btn-hover" @click="handleLogin">
          <uni-icons type="weixin" size="20" color="#ffffff" class="btn-icon"></uni-icons>
          微信一键登录
        </button>
        <view class="protocol">
          <checkbox :checked="agreed" color="#2563eb" @click="agreed = !agreed" class="protocol-checkbox" />
          <view class="protocol-text">
            我已阅读并同意 <text class="link" @click.stop="showProtocol('user')">用户协议</text> 和 <text class="link" @click.stop="showProtocol('privacy')">隐私政策</text>
          </view>
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
  background-color: #f8faff;
  font-family: "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
}

.bg-decoration {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
  background: radial-gradient(circle at 0% 0%, rgba(37, 99, 235, 0.15) 0%, transparent 50%),
              radial-gradient(circle at 100% 100%, rgba(13, 148, 136, 0.1) 0%, transparent 50%),
              linear-gradient(135deg, #f8faff 0%, #eef2ff 100%);

  .circle {
    position: absolute;
    border-radius: 50%;
    filter: blur(80rpx);
  }

  .circle-1 {
    width: 600rpx;
    height: 600rpx;
    background: rgba(37, 99, 235, 0.1);
    top: -100rpx;
    right: -100rpx;
  }

  .circle-2 {
    width: 400rpx;
    height: 400rpx;
    background: rgba(13, 148, 136, 0.08);
    bottom: 100rpx;
    left: -50rpx;
  }
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
  margin-top: -160rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.logo-wrapper {
  position: relative;
  margin-bottom: 48rpx;
}

.logo-placeholder {
  width: 180rpx;
  height: 180rpx;
  background: #ffffff;
  border-radius: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 72rpx;
  font-weight: 800;
  color: #2563eb;
  position: relative;
  z-index: 2;
  box-shadow: 0 20rpx 40rpx rgba(37, 99, 235, 0.12);
  border: 4rpx solid rgba(255, 255, 255, 0.8);
}

.logo-glow {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 240rpx;
  height: 240rpx;
  background: radial-gradient(circle, rgba(37, 99, 235, 0.2) 0%, transparent 70%);
  z-index: 1;
}

.app-name {
  font-size: 52rpx;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 20rpx;
  letter-spacing: 2rpx;
}

.slogan-wrapper {
  display: flex;
  align-items: center;
  
  .slogan-item {
    font-size: 28rpx;
    color: #64748b;
    letter-spacing: 4rpx;
  }
  
  .slogan-divider {
    font-size: 24rpx;
    color: #cbd5e1;
    margin: 0 16rpx;
  }
}

.action-area {
  position: absolute;
  bottom: 140rpx;
  width: 100%;
  padding: 0 64rpx;
  box-sizing: border-box;
}

.login-btn {
  width: 100%;
  height: 100rpx;
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  color: #ffffff;
  border-radius: 50rpx;
  font-size: 32rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 12rpx 30rpx rgba(37, 99, 235, 0.3);
  border: none;
  transition: all 0.2s ease;
  
  .btn-icon {
    margin-right: 12rpx;
  }
}

.login-btn-hover {
  transform: translateY(2rpx);
  box-shadow: 0 6rpx 20rpx rgba(37, 99, 235, 0.25);
  filter: brightness(1.05);
}

.protocol {
  margin-top: 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  
  .protocol-checkbox {
    transform: scale(0.7);
    margin-right: -4rpx;
  }
  
  .protocol-text {
    font-size: 24rpx;
    color: #64748b;
    line-height: 40rpx;
  }
  
  .link {
    color: #2563eb;
    font-weight: 500;
    margin: 0 4rpx;
    padding: 0 4rpx;
  }
}
</style>
