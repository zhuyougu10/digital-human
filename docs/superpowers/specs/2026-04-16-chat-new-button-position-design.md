# 患者端「新对话」按钮位置调整设计（chat 页面）

- 日期：2026-04-16
- 目标页面：`medical-mp/src/pages/chat/chat.vue`
- 需求：将患者端「新对话」按钮移到上半屏数字人区域右下角；样式保持不变；完成提交并推送。

## 1. 现状与问题

当前 `new-chat-btn` 位于 `.live2d-overlay` 顶部横向布局中（与助手信息卡并排，靠右），不符合“贴数字人区域右下角”的交互要求。

## 2. 方案选择

采用最小改动方案：

1. 保持按钮 DOM 仍在 Live2D 区域内（不改点击逻辑）；
2. 将按钮从顶部 overlay 流布局中分离出来，改为挂在 `.live2d-area` 下独立绝对定位；
3. `.live2d-area` 已是 `position: relative`，可直接作为定位上下文；
4. 按钮样式维持原参数（字体、圆角、背景、边框等）不变，仅新增定位属性。

## 3. 具体改动

### 3.1 模板结构

- 在 `<view class="live2d-area">` 内：
  - 保留 `<view class="live2d-overlay">` 中的 `assistant-card`；
  - 将 `<view class="new-chat-btn" @click="handleNewChat">` 从 `live2d-overlay` 内移动到其后，作为 `live2d-area` 的直接子节点。

### 3.2 样式调整

- `live2d-overlay`：改为仅负责顶部信息卡展示（`justify-content` 从 `space-between` 调整为 `flex-start`）；
- `new-chat-btn`：新增绝对定位：
  - `position: absolute;`
  - `right: 24rpx;`
  - `bottom: 24rpx;`
  - `z-index: 3;`
- 按钮原有视觉样式保持不变。

## 4. 验收标准

1. 「新对话」按钮显示在数字人画布区域右下角；
2. 按钮点击行为与原先一致（弹窗确认、结束旧会话、创建新会话）；
3. 数字人画布渲染与顶部状态卡不受影响；
4. 聊天输入区布局不受影响。

## 5. 风险与回滚

- 风险低：仅布局层改动，不改业务逻辑。
- 回滚方式：还原 `chat.vue` 的模板与样式改动即可。

## 6. 实施与提交计划

1. 修改 `chat.vue` 模板与样式；
2. 本地快速构建/检查（至少确保无语法错误）；
3. `git commit`；
4. `git push` 到当前分支。
