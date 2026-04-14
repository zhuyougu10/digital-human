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
  /* 基础品牌色系统 - 医疗蓝 */
  --brand-primary: #2563EB;
  --brand-primary-hover: #1D4ED8;
  --brand-primary-soft: #EFF6FF;
  
  /* 辅助状态色 */
  --brand-success: #10B981;
  --brand-warning: #F59E0B;
  --brand-danger: #EF4444;
  
  /* 文本色阶 */
  --text-main: #1E293B;
  --text-regular: #475569;
  --text-subtle: #94A3B8;
  --text-light: #CBD5E1;
  
  /* 背景和边框系统 */
  --bg-page: #F8FAFC;
  --bg-card: #FFFFFF;
  --border-color: #E2E8F0;
  
  /* 圆角和阴影 */
  --radius-sm: 8rpx;
  --radius-md: 16rpx;
  --radius-lg: 24rpx;
  --radius-xl: 32rpx;
  --radius-full: 9999rpx;
  
  --shadow-sm: 0 2rpx 8rpx rgba(30, 41, 59, 0.04);
  --shadow-md: 0 8rpx 24rpx rgba(30, 41, 59, 0.08);
  --shadow-lg: 0 16rpx 48rpx rgba(30, 41, 59, 0.12);
  
  /* 基础间距 */
  --spacing-xs: 8rpx;
  --spacing-sm: 16rpx;
  --spacing-md: 24rpx;
  --spacing-lg: 32rpx;
  --spacing-xl: 48rpx;
  
  background-color: var(--bg-page);
  color: var(--text-main);
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Helvetica Neue", Helvetica, "Microsoft YaHei", sans-serif;
  -webkit-font-smoothing: antialiased;
}
</style>
