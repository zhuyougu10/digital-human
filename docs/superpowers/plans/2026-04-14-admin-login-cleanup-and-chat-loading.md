# Admin 登录默认值清理与聊天页首屏 Loading Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 去掉管理端登录页默认账号密码，并让小程序聊天页在数字人首次加载完成前显示全屏 loading，同时保证加载失败或超时时不会永久卡住页面。

**Architecture:** 这次只做前端范围改动，保持接口和页面结构基本不变。管理端通过删除登录表单的初始默认值实现“首屏为空”；小程序端通过新增 `isPageBootLoading` 与 Live2D ready/timeout/failure 三种结果控制全屏 loading 的展示和退出。

**Tech Stack:** Vue 3, Element Plus, uni-app, 微信小程序, Live2D/Cubism renderer

---

## File Map

- Modify: `medical-admin/src/views/login/index.vue`
  - 职责：管理端登录页表单状态与提交逻辑
- Modify: `medical-mp/src/pages/chat/chat.vue`
  - 职责：聊天页布局、Live2D 初始化、全屏 loading 控制

## Task 1: 清理管理端登录页默认账号密码

**Files:**
- Modify: `medical-admin/src/views/login/index.vue`
- Test: `medical-admin/src/views/login/index.vue`

- [ ] **Step 1: 写出要达成的界面状态检查点**

确认页面当前代码中这段初始值：

```js
const loginForm = reactive({
  username: 'admin',
  password: 'admin123'
})
```

目标是改成：

```js
const loginForm = reactive({
  username: '',
  password: ''
})
```

- [ ] **Step 2: 修改默认值为全空**

在 `medical-admin/src/views/login/index.vue` 中把登录表单初始化改成：

```js
const loginForm = reactive({
  username: '',
  password: ''
})
```

- [ ] **Step 3: 自查登录校验逻辑未被破坏**

确认提交逻辑仍保留：

```js
if (!loginForm.username || !loginForm.password) {
  ElMessage.warning('请输入用户名和密码')
  return
}
```

要求：不要改提交接口、不要改按钮 loading、不要新增记住密码逻辑。

- [ ] **Step 4: 运行前端构建或最小检查**

Run:
```bash
cd /home/zhuyou/.openclaw/workspace/digital-human/medical-admin
npm run build
```

Expected:
- 构建成功
- 没有因为表单初始值修改引入编译错误

- [ ] **Step 5: 提交这一部分改动**

```bash
git add medical-admin/src/views/login/index.vue
git commit -m "fix: remove default admin login credentials"
```

## Task 2: 给小程序聊天页增加全屏 loading 蒙层

**Files:**
- Modify: `medical-mp/src/pages/chat/chat.vue`
- Test: `medical-mp/src/pages/chat/chat.vue`

- [ ] **Step 1: 写出页面状态扩展草案**

在现有状态定义附近新增这几个状态：

```js
const isPageBootLoading = ref(true)
const live2dReady = ref(false)
let live2dLoadingTimeoutId = null
```

约束：
- `isPageBootLoading` 默认 `true`
- `live2dReady` 只在模型首次成功加载后变为 `true`
- `live2dLoadingTimeoutId` 用于超时兜底，卸载时清理

- [ ] **Step 2: 在模板中加入全屏 loading 结构**

在 `chat-page` 内新增一个覆盖层，示例结构：

```vue
<view v-if="isPageBootLoading" class="page-loading-overlay">
  <view class="page-loading-card">
    <view class="page-loading-spinner"></view>
    <text class="page-loading-title">安禾正在准备中</text>
    <text class="page-loading-desc">正在加载数字人，请稍候...</text>
  </view>
</view>
```

要求：
- 覆盖整个页面
- 文案温和、简短
- 不删除现有页面结构，只做覆盖

- [ ] **Step 3: 在 Live2D 成功回调里关闭 loading**

将 `renderer.loadModel(...).then(() => { ... })` 扩成如下逻辑：

```js
.then(() => {
  console.log('[Chat] Live2D 模型加载成功')
  live2dReady.value = true
  isPageBootLoading.value = false
  if (live2dLoadingTimeoutId) {
    clearTimeout(live2dLoadingTimeoutId)
    live2dLoadingTimeoutId = null
  }
  lipSync = new Live2dLipSync((value) => renderer?.setMouthOpenY(value))
  startLipSyncTicker()
})
```

要求：
- 只在首次成功时关闭全屏 loading
- 保留原有 lipSync 初始化逻辑

- [ ] **Step 4: 在失败分支里增加兜底退出**

把 `catch((err) => { ... })` 改成带 loading 收尾的逻辑：

```js
.catch((err) => {
  console.error('[Chat] Live2D 加载失败:', err)
  if (live2dLoadingTimeoutId) {
    clearTimeout(live2dLoadingTimeoutId)
    live2dLoadingTimeoutId = null
  }
  isPageBootLoading.value = false
  uni.showToast({ title: '数字人加载失败', icon: 'none' })
})
```

要求：
- 失败时不能一直停在 loading
- 保留错误日志

- [ ] **Step 5: 在 mounted 时增加超时兜底**

在 `onMounted(() => { ... })` 中，初始化 Live2D 前后加入超时保护：

```js
live2dLoadingTimeoutId = setTimeout(() => {
  if (!live2dReady.value) {
    console.warn('[Chat] Live2D 加载超时，关闭首屏 loading')
    isPageBootLoading.value = false
    uni.showToast({ title: '数字人加载较慢，请稍候', icon: 'none' })
  }
}, 10000)

setTimeout(() => initLive2D(), 300)
```

要求：
- 超时时间固定写清楚，本轮用 `10000`
- 只做一次超时兜底，不做自动重试

- [ ] **Step 6: 在卸载时清理超时定时器**

在 `onUnmounted(() => { ... })` 里加入：

```js
if (live2dLoadingTimeoutId) {
  clearTimeout(live2dLoadingTimeoutId)
  live2dLoadingTimeoutId = null
}
```

- [ ] **Step 7: 为全屏 loading 补样式**

在 `style scoped` 中新增覆盖层样式，至少包含：

```css
.page-loading-overlay {
  position: fixed;
  inset: 0;
  z-index: 999;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, #e8f4f8 0%, #f5f7fa 100%);
}

.page-loading-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20rpx;
}

.page-loading-spinner {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  border: 6rpx solid rgba(74, 144, 217, 0.2);
  border-top-color: #4A90D9;
  animation: spin 0.9s linear infinite;
}

.page-loading-title {
  font-size: 34rpx;
  font-weight: 600;
  color: #1f4f6f;
}

.page-loading-desc {
  font-size: 26rpx;
  color: #6b7a88;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
```

要求：
- 不覆盖现有 `pulse` 动画名
- 样式不依赖 web-only 特性

- [ ] **Step 8: 运行小程序构建验证**

Run:
```bash
cd /home/zhuyou/.openclaw/workspace/digital-human/medical-mp
npm run build:mp-weixin
```

Expected:
- 构建成功
- `chat.vue` 新增状态和样式没有引入语法错误

- [ ] **Step 9: 提交这一部分改动**

```bash
git add medical-mp/src/pages/chat/chat.vue
git commit -m "feat: add chat page boot loading overlay"
```

## Task 3: 最终联查与单提交整理

**Files:**
- Modify: `medical-admin/src/views/login/index.vue`
- Modify: `medical-mp/src/pages/chat/chat.vue`

- [ ] **Step 1: 检查最终 diff 只包含本轮需求**

Run:
```bash
git diff -- medical-admin/src/views/login/index.vue medical-mp/src/pages/chat/chat.vue
```

Expected:
- admin 只包含默认值删除
- chat 页只包含 loading 状态、模板、样式、成功/失败/超时兜底改动

- [ ] **Step 2: 运行最终验证命令**

Run:
```bash
cd /home/zhuyou/.openclaw/workspace/digital-human/medical-admin && npm run build
cd /home/zhuyou/.openclaw/workspace/digital-human/medical-mp && npm run build:mp-weixin
```

Expected:
- 两边都成功

- [ ] **Step 3: 合并成最终提交**

如果前面是分提交执行，整理为最终提交时使用：

```bash
git add medical-admin/src/views/login/index.vue medical-mp/src/pages/chat/chat.vue
git commit -m "feat: improve login and chat page loading experience"
```

如果已有清晰提交，则保持提交历史整洁即可，不必重复提交。
