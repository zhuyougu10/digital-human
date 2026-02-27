# 10 - 小程序端 (UniApp + Live2D + TTS)

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 使用 UniApp (Vue3) 构建患者端微信小程序，集成 Live2D 数字人、SSE 流式对话、TTS 语音播放，实现从问诊到挂号的完整闭环。

**Architecture:** 小程序主体用 UniApp 开发。Live2D 通过 `<web-view>` 内嵌 H5 页面实现（小程序不支持 WebGL Canvas）。H5 与小程序通过 postMessage 通信。SSE 使用 uni.request + enableChunked 实现流式接收。

**Tech Stack:** UniApp (Vue3 + HBuilderX), pixi.js + pixi-live2d-display (H5), 微信小程序 API

**前置依赖:** `06-ai-service` + `07-appointment-service` 后端就绪

---

## Task 1: UniApp 项目初始化

使用 HBuilderX 创建 UniApp Vue3 项目，或 CLI：

```bash
npx degit dcloudio/uni-preset-vue#vite-ts medical-mp
cd medical-mp
npm install
```

---

## Task 2: 项目结构

```
medical-mp/
├── src/
│   ├── pages/
│   │   ├── index/index.vue           # 首页（数字人入口 + 快捷功能）
│   │   ├── chat/chat.vue             # 对话页（核心页面：Live2D + 聊天）
│   │   ├── doctors/list.vue          # 医生列表
│   │   ├── doctors/detail.vue        # 医生详情
│   │   ├── appointment/list.vue      # 我的预约列表
│   │   ├── appointment/detail.vue    # 预约详情
│   │   └── mine/index.vue            # 个人中心
│   ├── components/
│   │   ├── ChatMessage.vue           # 聊天消息气泡组件
│   │   ├── DoctorCard.vue            # 医生推荐卡片（嵌入对话流）
│   │   ├── SlotPicker.vue            # 号源时间选择器（嵌入对话流）
│   │   ├── AppointmentCard.vue       # 预约结果卡片（嵌入对话流）
│   │   └── TtsPlayer.vue            # TTS 音频播放控制
│   ├── api/
│   │   ├── request.js                # uni.request 封装
│   │   ├── auth.js
│   │   ├── chat.js
│   │   ├── doctor.js
│   │   └── appointment.js
│   ├── stores/
│   │   └── user.js                   # Pinia 用户状态
│   ├── utils/
│   │   ├── sse.js                    # SSE 流式请求封装
│   │   └── index.js
│   ├── static/
│   │   └── images/
│   ├── pages.json
│   ├── manifest.json
│   ├── App.vue
│   └── main.js
├── live2d-h5/                        # Live2D H5 独立项目
│   ├── index.html
│   ├── src/
│   │   ├── main.js
│   │   ├── live2d-manager.js         # Live2D 模型管理
│   │   └── tts-lip-sync.js          # TTS 口型同步
│   ├── models/                       # Live2D 模型文件
│   │   └── doctor/
│   │       ├── doctor.model3.json
│   │       ├── doctor.moc3
│   │       └── textures/
│   └── vite.config.js
└── package.json
```

---

## Task 3: 请求封装 + 微信登录

**Files:**
- Create: `src/api/request.js`

```javascript
const BASE_URL = 'https://your-domain.com/api' // 或本地开发地址

export function request(options) {
  const token = uni.getStorageSync('token')
  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE_URL + options.url,
      method: options.method || 'GET',
      data: options.data,
      header: {
        'Authorization': token ? `Bearer ${token}` : '',
        'Content-Type': 'application/json',
        ...options.header
      },
      success: (res) => {
        if (res.data.code === 200) {
          resolve(res.data)
        } else if (res.data.code === 401) {
          uni.removeStorageSync('token')
          uni.reLaunch({ url: '/pages/index/index' })
          reject(res.data)
        } else {
          uni.showToast({ title: res.data.msg, icon: 'none' })
          reject(res.data)
        }
      },
      fail: reject
    })
  })
}
```

**微信登录流程：**
```javascript
// src/api/auth.js
export function wxLogin() {
  return new Promise((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: async (loginRes) => {
        const res = await request({
          url: '/user/auth/wx-login',
          method: 'POST',
          data: { code: loginRes.code }
        })
        uni.setStorageSync('token', res.data.token)
        uni.setStorageSync('userInfo', res.data.user)
        resolve(res.data)
      },
      fail: reject
    })
  })
}
```

---

## Task 4: SSE 流式请求封装

**Files:**
- Create: `src/utils/sse.js`

微信小程序不支持 EventSource，使用 `uni.request` + `enableChunked: true`：

```javascript
export function createSSERequest(url, data, callbacks) {
  const token = uni.getStorageSync('token')
  const requestTask = uni.request({
    url: BASE_URL + url,
    method: 'POST',
    data: data,
    header: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream'
    },
    enableChunked: true,
    success: () => {
      callbacks.onComplete && callbacks.onComplete()
    },
    fail: (err) => {
      callbacks.onError && callbacks.onError(err)
    }
  })

  // 监听分块数据
  let buffer = ''
  requestTask.onChunkReceived((res) => {
    // 将 ArrayBuffer 转为字符串
    const text = arrayBufferToString(res.data)
    buffer += text

    // 解析 SSE 格式：data: {...}\n\n
    const events = buffer.split('\n\n')
    buffer = events.pop() // 保留未完整的部分

    events.forEach(eventStr => {
      const lines = eventStr.split('\n')
      let eventType = 'message'
      let eventData = ''
      lines.forEach(line => {
        if (line.startsWith('event:')) eventType = line.slice(6).trim()
        if (line.startsWith('data:')) eventData = line.slice(5).trim()
      })
      if (eventData) {
        try {
          const parsed = JSON.parse(eventData)
          callbacks.onMessage && callbacks.onMessage(eventType, parsed)
        } catch (e) {
          callbacks.onMessage && callbacks.onMessage(eventType, eventData)
        }
      }
    })
  })

  return requestTask
}

function arrayBufferToString(buffer) {
  return String.fromCharCode.apply(null, new Uint8Array(buffer))
}
```

---

## Task 5: 首页 (index)

**Files:**
- Create: `src/pages/index/index.vue`

布局：
- 顶部：问候语 + 用户头像
- 中间：大按钮"开始问诊"（跳转对话页）
- 下方：快捷入口卡片（找医生、我的预约、健康科普）
- 首次进入触发微信静默登录

---

## Task 6: 对话页 (chat) - 核心页面

**Files:**
- Create: `src/pages/chat/chat.vue`

布局（从上到下）：

```
┌─────────────────────────┐
│  <web-view>             │  ← 上半区 40%: Live2D 数字人 H5
│  Live2D 数字人           │
│  (通过 src 指向 H5 URL)  │
├─────────────────────────┤
│  消息列表 <scroll-view> │  ← 下半区 50%: 聊天面板
│  ┌─────────────────┐    │
│  │ AI: 您好！       │    │     普通文本消息
│  └─────────────────┘    │
│  ┌─────────────────┐    │
│  │ [医生推荐卡片]    │    │     特殊消息类型（组件渲染）
│  │ [一键预约按钮]    │    │
│  └─────────────────┘    │
├─────────────────────────┤
│ [输入框]        [发送]  │  ← 底部 10%: 输入区
└─────────────────────────┘
```

**核心逻辑：**

1. 进入页面 → 创建对话会话 → 发送问候消息触发 AI 回复
2. 用户输入 → 调用 SSE 接口 → 逐 token 渲染 AI 回复
3. AI 回复中包含 metadata（type: doctor_recommend / slot_picker / appointment_result）→ 渲染特殊卡片组件
4. 用户点击医生卡片 → 将选择作为消息发送 → 触发 Agent 查号源
5. 用户选择时间 → 将选择作为消息发送 → 触发 Agent 创建预约
6. 预约成功 → 渲染预约结果卡片

**消息类型：**
- `text` - 普通文本（Markdown 渲染）
- `doctor_recommend` - 医生推荐列表卡片
- `slot_picker` - 号源时间选择卡片
- `appointment_result` - 预约成功结果卡片
- `tts` - TTS 音频 URL（触发语音播放 + Live2D 口型）

---

## Task 7: 特殊消息卡片组件

**Files:**
- Create: `src/components/DoctorCard.vue` - 医生推荐卡片
  - 头像、姓名、职称、擅长、科室
  - "选择此医生"按钮 → emit 事件
- Create: `src/components/SlotPicker.vue` - 号源选择器
  - 日期 Tab + 上午/下午时段卡片
  - 显示剩余号源数
  - 点击选择 → emit 事件
- Create: `src/components/AppointmentCard.vue` - 预约结果卡片
  - 预约成功图标 + 医生名 + 时间 + 排队号
  - "查看预约详情"按钮

---

## Task 8: TTS 音频播放

**Files:**
- Create: `src/components/TtsPlayer.vue`

```javascript
// 接收 ttsUrl → 使用 uni.createInnerAudioContext 播放
// 同时通过 postMessage 通知 Live2D H5 开始口型动画
// 播放结束后通知 H5 停止口型
```

---

## Task 9: Live2D H5 页面

**Files:**
- Create: `live2d-h5/index.html`
- Create: `live2d-h5/src/main.js`
- Create: `live2d-h5/src/live2d-manager.js`
- Create: `live2d-h5/src/tts-lip-sync.js`

**main.js：**
```javascript
import { Application } from 'pixi.js'
import { Live2DModel } from 'pixi-live2d-display'
import { LipSyncManager } from './tts-lip-sync'

const app = new Application({
  view: document.getElementById('canvas'),
  autoStart: true,
  transparent: true,
  resizeTo: window
})

// 加载 Live2D 模型
const model = await Live2DModel.from('./models/doctor/doctor.model3.json')
app.stage.addChild(model)

// 居中 + 缩放
model.anchor.set(0.5, 0.5)
model.position.set(window.innerWidth / 2, window.innerHeight / 2)

// 监听来自小程序的消息
window.addEventListener('message', (event) => {
  const { type, data } = event.data
  switch (type) {
    case 'START_LIPSYNC':
      // 开始口型动画
      lipSync.start()
      break
    case 'STOP_LIPSYNC':
      // 停止口型动画
      lipSync.stop()
      break
    case 'PLAY_MOTION':
      // 播放指定动画
      model.motion(data.group, data.index)
      break
  }
})

// 口型同步管理器
const lipSync = new LipSyncManager(model)
```

**tts-lip-sync.js：**
```javascript
export class LipSyncManager {
  constructor(model) {
    this.model = model
    this.isPlaying = false
    this.animationId = null
  }

  start() {
    this.isPlaying = true
    this.animate()
  }

  stop() {
    this.isPlaying = false
    if (this.animationId) {
      cancelAnimationFrame(this.animationId)
    }
    // 重置口型
    this.model.internalModel.coreModel.setParameterValueById('ParamMouthOpenY', 0)
  }

  animate() {
    if (!this.isPlaying) return
    // 使用正弦波模拟口型变化
    const value = Math.sin(Date.now() / 100) * 0.5 + 0.5
    this.model.internalModel.coreModel.setParameterValueById('ParamMouthOpenY', value)
    this.animationId = requestAnimationFrame(() => this.animate())
  }
}
```

---

## Task 10: 医生列表 / 详情页

**Files:**
- Create: `src/pages/doctors/list.vue` - 按科室 Tab 展示医生列表
- Create: `src/pages/doctors/detail.vue` - 医生详情（画像 + 排班 + 预约入口）

---

## Task 11: 我的预约

**Files:**
- Create: `src/pages/appointment/list.vue` - 预约列表（卡片样式）
- Create: `src/pages/appointment/detail.vue` - 预约详情（含取消按钮）

---

## Task 12: 个人中心

**Files:**
- Create: `src/pages/mine/index.vue`

- 用户头像 + 昵称（可编辑）
- 菜单列表：我的预约、对话记录、设置、关于

---

## Task 13: pages.json 配置

```json
{
  "pages": [
    { "path": "pages/index/index", "style": { "navigationBarTitleText": "AI医疗助手" } },
    { "path": "pages/chat/chat", "style": { "navigationBarTitleText": "智能问诊", "navigationStyle": "custom" } },
    { "path": "pages/doctors/list", "style": { "navigationBarTitleText": "找医生" } },
    { "path": "pages/doctors/detail", "style": { "navigationBarTitleText": "医生详情" } },
    { "path": "pages/appointment/list", "style": { "navigationBarTitleText": "我的预约" } },
    { "path": "pages/appointment/detail", "style": { "navigationBarTitleText": "预约详情" } },
    { "path": "pages/mine/index", "style": { "navigationBarTitleText": "个人中心" } }
  ],
  "tabBar": {
    "list": [
      { "pagePath": "pages/index/index", "text": "首页", "iconPath": "...", "selectedIconPath": "..." },
      { "pagePath": "pages/doctors/list", "text": "找医生", "iconPath": "...", "selectedIconPath": "..." },
      { "pagePath": "pages/appointment/list", "text": "预约", "iconPath": "...", "selectedIconPath": "..." },
      { "pagePath": "pages/mine/index", "text": "我的", "iconPath": "...", "selectedIconPath": "..." }
    ]
  }
}
```

---

## Task 14: 编译验证

```bash
# UniApp 编译到微信小程序
npm run dev:mp-weixin

# Live2D H5 构建
cd live2d-h5 && npm run build
```

```bash
git add .
git commit -m "feat(frontend-mp): implement patient mini-program with Live2D, SSE chat, TTS"
```

---

## 检查清单

- [ ] UniApp 项目结构
- [ ] 微信静默登录 + Token 管理
- [ ] SSE 流式请求封装（enableChunked）
- [ ] 首页 + Tab 导航
- [ ] 对话页核心：Live2D（上半区）+ 聊天面板（下半区）+ 输入框
- [ ] 特殊消息卡片：医生推荐 / 号源选择 / 预约结果
- [ ] Live2D H5 页面（pixi.js + pixi-live2d-display）
- [ ] TTS 音频播放 + Live2D 口型同步
- [ ] 小程序 ↔ H5 postMessage 通信
- [ ] 医生列表/详情
- [ ] 我的预约列表/详情
- [ ] 个人中心
- [ ] 微信小程序编译通过
- [ ] Live2D H5 构建通过
