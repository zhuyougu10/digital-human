# App 端全页面 UI 中重构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `medical-mp` 的登录、问诊、医生、预约、我的和 TabBar 全部统一为医疗专业感 UI，并落地“数字人医疗助手 / 我是你的医疗助手安禾”的品牌文案体系。

**Architecture:** 本轮仅做前端 UI 中重构，不改后端接口契约。先建立一套可复用的页面视觉系统与共用样式/组件约定，再按“登录 -> 问诊 -> 医生 -> 预约 -> 我的 -> TabBar”顺序逐页落地，最后统一验收全局视觉一致性和构建结果。

**Tech Stack:** uni-app, Vue 3, 微信小程序, existing component stack (`ChatMessage`, `DoctorCard`, `AppointmentCard`, `SlotPicker`, `TtsPlayer`)

---

## File Map

### Core app shell and config
- Modify: `medical-mp/src/pages.json`
  - 职责：页面注册、标题、TabBar 文案与 icon 配置
- Modify: `medical-mp/src/App.vue`
  - 职责：全局页面基础样式、可放通用设计令牌（若现有结构适合）
- Create or Modify: `medical-mp/src/utils/index.js`
  - 职责：若需要抽离轻量 UI 帮助函数，放共用逻辑，不承载大样式系统

### Reusable UI pieces
- Modify: `medical-mp/src/components/DoctorCard.vue`
  - 职责：医生卡片统一视觉
- Modify: `medical-mp/src/components/AppointmentCard.vue` (若不存在则创建；当前如页面内直接写结构则本任务不强制创建)
  - 职责：预约卡片统一视觉
- Modify: `medical-mp/src/components/SlotPicker.vue`
  - 职责：预约时段选择视觉统一
- Modify: `medical-mp/src/components/ChatMessage.vue`
  - 职责：聊天消息气泡风格统一

### Pages
- Modify: `medical-mp/src/pages/login/login.vue`
- Modify: `medical-mp/src/pages/chat/chat.vue`
- Modify: `medical-mp/src/pages/doctors/list.vue`
- Modify: `medical-mp/src/pages/doctors/detail.vue`
- Modify: `medical-mp/src/pages/appointment/list.vue`
- Modify: `medical-mp/src/pages/appointment/detail.vue`
- Modify: `medical-mp/src/pages/mine/index.vue`

### Optional asset/config paths
- Create: `medical-mp/src/static` or `medical-mp/static/tabbar/*` only if local TabBar icon assets are needed

---

## Task 1: 定义全局视觉规则与品牌文案落点

**Files:**
- Modify: `medical-mp/src/App.vue`
- Modify: `medical-mp/src/pages.json`
- Test: `medical-mp/src/App.vue`, `medical-mp/src/pages.json`

- [ ] **Step 1: 盘点现有页面标题与品牌文案入口**

检查以下位置：

```bash
cd /home/zhuyou/.openclaw/workspace/digital-human
grep -R "安禾\|AI 问诊\|我的预约\|登录\|医疗" -n medical-mp/src medical-mp/src/pages.json
```

Expected:
- 找到当前聊天页标题、欢迎语、TabBar 文案和各页面导航标题
- 明确哪些位置需要替换成统一品牌语气

- [ ] **Step 2: 在 App.vue 放入全局设计令牌骨架**

若 `App.vue` 当前样式很薄，加入类似下面的基础变量：

```vue
<style>
page {
  --brand-primary: #2e7ea7;
  --brand-primary-soft: #e8f4f8;
  --brand-text-main: #1f2d3d;
  --brand-text-subtle: #5f6b76;
  --brand-border: #d9e7ef;
  --brand-surface: #ffffff;
  --brand-bg: #f5f8fb;
  --brand-success: #41a56a;
  --brand-warning: #d89b2b;
  --brand-danger: #d85b5b;
  background: var(--brand-bg);
  color: var(--brand-text-main);
}
</style>
```

要求：
- 不引入庞大样式系统
- 只放本轮 UI 重构会反复用到的基础变量

- [ ] **Step 3: 更新 pages.json 的品牌文案基线**

将全局标题和页面标题调整到新的产品语气，例如：

```json
{
  "globalStyle": {
    "navigationBarTitleText": "数字人医疗助手"
  }
}
```

并检查各页面标题是否需要更产品化，例如：
- `AI 问诊` -> `数字人医疗助手`
- `找医生` 可保持，但与整体语气统一

- [ ] **Step 4: Run build to ensure config is valid**

Run:
```bash
cd /home/zhuyou/.openclaw/workspace/digital-human/medical-mp
npm run build:mp-weixin
```

Expected:
- pages.json 与 App.vue 变更后构建成功

- [ ] **Step 5: Commit**

```bash
git add medical-mp/src/App.vue medical-mp/src/pages.json
git commit -m "feat: define app visual foundation for ui refactor"
```

## Task 2: 重构登录页为产品入口页

**Files:**
- Modify: `medical-mp/src/pages/login/login.vue`
- Test: `medical-mp/src/pages/login/login.vue`

- [ ] **Step 1: 阅读当前登录页结构并列出保留功能**

确认以下功能仍保留：
- 账号输入
- 密码输入
- 登录提交
- 原有登录接口调用与跳转逻辑

不要改：
- 登录接口路径
- token 存储机制
- 基础鉴权流程

- [ ] **Step 2: 重构页面结构为“品牌头 + 表单卡片”**

页面目标结构：

```vue
<view class="login-page">
  <view class="brand-section">
    <text class="brand-title">数字人医疗助手</text>
    <text class="brand-subtitle">智能问诊与预约服务</text>
  </view>

  <view class="hero-card">
    <text class="hero-copy">我是你的医疗助手</text>
    <text class="hero-desc">登录后即可开始智能问诊与预约服务</text>
  </view>

  <view class="login-card">
    <!-- 现有表单输入与按钮 -->
  </view>
</view>
```

要求：
- 不再像测试登录页
- 不新增复杂插画依赖
- 结构清晰、留白充足

- [ ] **Step 3: 将表单样式统一到新视觉体系**

至少统一：
- 输入框高度
- 圆角
- 按钮主色
- 辅助说明文字颜色层级

不要把表单验证逻辑揉碎重写。

- [ ] **Step 4: Verify existing login action still works structurally**

检查 `handleLogin` 是否仍然：
- 校验空值
- 调用原登录 API
- 成功后跳转

不改逻辑，只改 UI 承载。

- [ ] **Step 5: Run build**

Run:
```bash
cd /home/zhuyou/.openclaw/workspace/digital-human/medical-mp
npm run build:mp-weixin
```

Expected:
- 登录页改造后构建成功

- [ ] **Step 6: Commit**

```bash
git add medical-mp/src/pages/login/login.vue
git commit -m "feat: redesign login page as product entry"
```

## Task 3: 重构问诊页为主场景

**Files:**
- Modify: `medical-mp/src/pages/chat/chat.vue`
- Modify: `medical-mp/src/components/ChatMessage.vue`
- Test: `medical-mp/src/pages/chat/chat.vue`, `medical-mp/src/components/ChatMessage.vue`

- [ ] **Step 1: 保留现有 Live2D / SSE / TTS 逻辑边界**

在改 UI 前明确这些逻辑不得被破坏：
- Live2D 初始化与 loading
- SSE 流式问诊
- TTS 播放队列
- 历史消息恢复

要求：先改结构与样式，不动这几条业务链的接口。

- [ ] **Step 2: 重构顶部品牌区文案**

在聊天页顶部改成统一品牌位，例如：

```vue
<view class="assistant-card">
  <text class="assistant-title">数字人医疗助手</text>
  <text class="assistant-subtitle">我是你的医疗助手安禾</text>
</view>
```

将技术感更强的文案收敛成服务型文案。

- [ ] **Step 3: 优化数字人区域与消息区域层次**

要求：
- 数字人区域是主视觉中心
- 背景、卡片和按钮风格统一到医疗专业感
- 新对话按钮、状态栏、欢迎语风格同步调整

- [ ] **Step 4: 重构聊天消息气泡视觉**

在 `ChatMessage.vue` 中统一：
- 用户消息与助手消息的色彩区分
- 圆角、边距、字号
- 医疗产品风格，而不是普通 IM 风格

不要改消息数据结构。

- [ ] **Step 5: 保持对话内自称规则**

聊天欢迎语与空状态应使用：

```text
我是你的医疗助手安禾
```

要求：
- 统一落在欢迎语、空状态或引导语中
- 不在页面视觉上过度重复堆文案

- [ ] **Step 6: Run build**

Run:
```bash
cd /home/zhuyou/.openclaw/workspace/digital-human/medical-mp
npm run build:mp-weixin
```

Expected:
- 问诊页与消息组件改造后构建成功

- [ ] **Step 7: Commit**

```bash
git add medical-mp/src/pages/chat/chat.vue medical-mp/src/components/ChatMessage.vue
git commit -m "feat: redesign chat experience around medical assistant"
```

## Task 4: 重构医生列表与医生详情页

**Files:**
- Modify: `medical-mp/src/pages/doctors/list.vue`
- Modify: `medical-mp/src/pages/doctors/detail.vue`
- Modify: `medical-mp/src/components/DoctorCard.vue`
- Test: `medical-mp/src/pages/doctors/list.vue`, `medical-mp/src/pages/doctors/detail.vue`, `medical-mp/src/components/DoctorCard.vue`

- [ ] **Step 1: 统一医生卡片信息层级**

在 `DoctorCard.vue` 中保证以下信息层级：
- 姓名
- 职称
- 科室
- 擅长
- 可预约状态
- CTA

要求：推荐医生卡看起来像医疗推荐卡，而不是普通列表项。

- [ ] **Step 2: 重构医生列表页为“推荐医生”语境**

列表页至少包含：
- 顶部说明区
- 搜索/轻筛选区
- 医生卡片列表
- 空状态

若页面本身既支持普通浏览也支持问诊推荐，要让视觉文案尽量优先兼容“推荐”语境，但不改接口入参。

- [ ] **Step 3: 重构医生详情页为“预约决策页”**

详情页应包含：
- 医生头卡
- 擅长与简介
- 出诊/可预约信息
- 清晰的主按钮（立即预约）

要求：重点放在帮助用户判断“是否适合预约”。

- [ ] **Step 4: Run build**

Run:
```bash
cd /home/zhuyou/.openclaw/workspace/digital-human/medical-mp
npm run build:mp-weixin
```

Expected:
- 医生页改造后构建成功

- [ ] **Step 5: Commit**

```bash
git add medical-mp/src/pages/doctors/list.vue medical-mp/src/pages/doctors/detail.vue medical-mp/src/components/DoctorCard.vue
git commit -m "feat: redesign doctor discovery flow"
```

## Task 5: 重构预约列表与预约详情页

**Files:**
- Modify: `medical-mp/src/pages/appointment/list.vue`
- Modify: `medical-mp/src/pages/appointment/detail.vue`
- Modify: `medical-mp/src/components/SlotPicker.vue`
- Test: `medical-mp/src/pages/appointment/list.vue`, `medical-mp/src/pages/appointment/detail.vue`, `medical-mp/src/components/SlotPicker.vue`

- [ ] **Step 1: 将预约列表页重构为“管理页”**

页面结构应包含：
- 标题区
- 状态筛选区
- 预约卡片列表
- 空状态

要求：让用户一眼看清当前预约状态与下一步动作。

- [ ] **Step 2: 将预约详情页重构为“服务确认单”**

页面结构应包含：
- 状态头卡
- 核心预约信息
- 就诊说明
- 操作区
- 回流入口

要求：不再像数据库详情页。

- [ ] **Step 3: 同步时段选择器视觉**

若 `SlotPicker.vue` 在预约流程中可见，统一其：
- 选中态
- 未选中态
- 边框、色彩与字号

不改其数据选择逻辑。

- [ ] **Step 4: Run build**

Run:
```bash
cd /home/zhuyou/.openclaw/workspace/digital-human/medical-mp
npm run build:mp-weixin
```

Expected:
- 预约页与时段选择器改造后构建成功

- [ ] **Step 5: Commit**

```bash
git add medical-mp/src/pages/appointment/list.vue medical-mp/src/pages/appointment/detail.vue medical-mp/src/components/SlotPicker.vue
git commit -m "feat: redesign appointment management pages"
```

## Task 6: 重构我的页并补全回流主链路

**Files:**
- Modify: `medical-mp/src/pages/mine/index.vue`
- Test: `medical-mp/src/pages/mine/index.vue`

- [ ] **Step 1: 重构我的页结构为“个人服务台”**

页面结构应包含：
- 个人头卡
- 快捷入口区
- 服务设置区
- 安禾入口区

要求：减少信息堆叠，增强产品服务感。

- [ ] **Step 2: 增加回流主流程的入口表达**

页面中必须存在明确回流：
- 去问诊
- 看预约
- 找医生

不要求新增路由，只重构已有入口展示方式。

- [ ] **Step 3: Run build**

Run:
```bash
cd /home/zhuyou/.openclaw/workspace/digital-human/medical-mp
npm run build:mp-weixin
```

Expected:
- 我的页改造后构建成功

- [ ] **Step 4: Commit**

```bash
git add medical-mp/src/pages/mine/index.vue
git commit -m "feat: redesign mine page as personal service hub"
```

## Task 7: 为 TabBar 补齐 icon 并统一导航风格

**Files:**
- Modify: `medical-mp/src/pages.json`
- Create: `medical-mp/static/tabbar/*` (only if needed)
- Test: `medical-mp/src/pages.json`, `medical-mp/static/tabbar/*`

- [ ] **Step 1: 决定 icon 实现方式并保持维护简单**

优先策略：
- 若 uni-app TabBar 需要静态图片，则使用本地图标资源
- 不引入维护成本高的额外图标框架

建议资源命名：
- `consult.png`, `consult-active.png`
- `appointment.png`, `appointment-active.png`
- `mine.png`, `mine-active.png`

- [ ] **Step 2: 在 pages.json 中补齐 TabBar icon**

将每个 tab 配置成类似：

```json
{
  "pagePath": "pages/chat/chat",
  "text": "问诊",
  "iconPath": "static/tabbar/consult.png",
  "selectedIconPath": "static/tabbar/consult-active.png"
}
```

要求：
- 问诊 / 预约 / 我的 三个 tab 全补齐
- 选中态颜色与全局主色一致

- [ ] **Step 3: Run build**

Run:
```bash
cd /home/zhuyou/.openclaw/workspace/digital-human/medical-mp
npm run build:mp-weixin
```

Expected:
- TabBar icon 配置生效且构建成功

- [ ] **Step 4: Commit**

```bash
git add medical-mp/src/pages.json medical-mp/static/tabbar
git commit -m "feat: add branded tabbar icons"
```

## Task 8: 最终统一校验与收口

**Files:**
- Modify: `medical-mp/src/pages/login/login.vue`
- Modify: `medical-mp/src/pages/chat/chat.vue`
- Modify: `medical-mp/src/pages/doctors/list.vue`
- Modify: `medical-mp/src/pages/doctors/detail.vue`
- Modify: `medical-mp/src/pages/appointment/list.vue`
- Modify: `medical-mp/src/pages/appointment/detail.vue`
- Modify: `medical-mp/src/pages/mine/index.vue`
- Modify: `medical-mp/src/pages.json`
- Modify: component files touched above

- [ ] **Step 1: 做品牌文案统一检查**

Run:
```bash
cd /home/zhuyou/.openclaw/workspace/digital-human
grep -R "安禾\|医疗助手\|数字人医疗助手" -n medical-mp/src medical-mp/src/pages.json
```

Expected:
- 页面品牌位统一使用 `数字人医疗助手`
- 对话自称统一使用 `我是你的医疗助手安禾`
- 小空间文案使用 `我是你的医疗助手`
- 不出现旧的零散品牌说法

- [ ] **Step 2: 做最终构建验证**

Run:
```bash
cd /home/zhuyou/.openclaw/workspace/digital-human/medical-mp
npm run build:mp-weixin
```

Expected:
- 完整构建成功
- 无语法错误
- 允许存在 Sass legacy warning，但不能有构建失败

- [ ] **Step 3: 检查最终 diff 只包含本轮 UI 范围**

Run:
```bash
git diff --stat origin/master...HEAD
```

Expected:
- 变更集中在页面、组件、pages.json、必要图标资源
- 没有误改后端或无关业务逻辑

- [ ] **Step 4: Final commit**

```bash
git add medical-mp/src/pages medical-mp/src/components medical-mp/src/App.vue medical-mp/src/pages.json medical-mp/static/tabbar
git commit -m "feat: refactor app pages with unified medical ui"
```
