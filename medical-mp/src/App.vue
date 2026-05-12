<script setup lang="ts">
import { onLaunch, onShow, onHide } from "@dcloudio/uni-app";
onLaunch(() => {
  console.log("App Launch");
});
onShow(() => {
  console.log("App Show");
});
onHide(() => {
  console.log("App Hide");
});
</script>
<style>
page {
  --brand-primary: #2563eb;
  --brand-primary-hover: #1d4ed8;
  --brand-primary-soft: #eff6ff;
  --brand-primary-muted: #dbeafe;
  --brand-secondary: #0ea5e9;
  --brand-success: #10b981;
  --brand-warning: #f59e0b;
  --brand-danger: #ef4444;

  --text-main: #0f172a;
  --text-regular: #334155;
  --text-subtle: #64748b;
  --text-light: #cbd5e1;

  --bg-page: #f7fafc;
  --bg-card: #ffffff;
  --bg-muted: #f1f5f9;
  --border-color: #e2e8f0;

  --radius-sm: 12rpx;
  --radius-md: 20rpx;
  --radius-lg: 28rpx;
  --radius-xl: 36rpx;
  --radius-full: 9999rpx;

  --shadow-sm: 0 4rpx 14rpx rgba(15, 23, 42, 0.05);
  --shadow-md: 0 12rpx 34rpx rgba(15, 23, 42, 0.08);
  --shadow-lg: 0 20rpx 54rpx rgba(15, 23, 42, 0.12);

  --spacing-xs: 8rpx;
  --spacing-sm: 16rpx;
  --spacing-md: 24rpx;
  --spacing-lg: 32rpx;
  --spacing-xl: 48rpx;

  background: linear-gradient(180deg, #f8fbff 0%, #f7fafc 36%, #f7fafc 100%);
  background-color: var(--bg-page);
  color: var(--text-main);
  font-family: "PingFang SC", "Hiragino Sans GB", "Noto Sans SC", "Microsoft YaHei", sans-serif;
  -webkit-font-smoothing: antialiased;
}

view,
text,
image,
button,
input,
textarea,
scroll-view {
  box-sizing: border-box;
}

.ui-card {
  background: var(--bg-card);
  border: 1rpx solid rgba(226, 232, 240, 0.85);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.ui-card-strong {
  background: var(--bg-card);
  border: 1rpx solid rgba(219, 234, 254, 0.72);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
}

.ui-section-title {
  font-size: 34rpx;
  font-weight: 700;
  color: var(--text-main);
  letter-spacing: 0.2rpx;
}

.ui-section-subtitle {
  font-size: 24rpx;
  line-height: 1.5;
  color: var(--text-subtle);
}

.ui-pill {
  display: inline-flex;
  align-items: center;
  padding: 6rpx 16rpx;
  border-radius: var(--radius-full);
  background: var(--brand-primary-soft);
  color: var(--brand-primary);
  font-size: 22rpx;
  font-weight: 600;
}

.ui-btn-primary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 96rpx;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--brand-primary) 0%, var(--brand-secondary) 100%);
  color: #ffffff;
  box-shadow: 0 12rpx 28rpx rgba(37, 99, 235, 0.18);
}

.ui-empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 120rpx 40rpx;
  color: var(--text-subtle);
}
</style>
