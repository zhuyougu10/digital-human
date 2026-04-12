# Task Plan: 微信小程序原生 Canvas WebGL 渲染 Live2D

## 背景
微信小程序个人开发者无法使用 `web-view` 组件。当前 `chat.vue` 通过 `web-view` 嵌入 `live2d-h5` 页面来渲染 Live2D 数字人并提供聊天 UI。需要将 Live2D 渲染和聊天 UI 全部迁移到小程序原生实现。

## 技术决策
- 放弃 pixi.js + pixi-live2d-display 路线（对浏览器 DOM 依赖太深，shim 成本极高）
- 改用 **Cubism SDK for Web 底层 WebGL 渲染** + 小程序 `<canvas type="webgl">` 直接绘制
- 聊天 UI 从 H5 迁回小程序原生组件

## 当前模型信息
- 格式：Cubism 4（`.model3.json` / `.moc3`）
- 模型：`wariza.model3.json`
- 纹理：`Wariza.4096/texture_00.png`（4096px）
- 表情：7 个 `.exp3.json`
- 动作：3 个 `.motion3.json`（idle + shake hand）
- 物理：`Wariza.physics3.json`
- Core SDK：`live2dcubismcore.min.js`（已有）

## 阶段

### Phase 1: 前端 — 小程序 Canvas WebGL 适配层 + Live2D 渲染 [status: ready]
**执行者：Codex**（这个任务核心是 JS/WebGL 底层适配，不是 UI 设计）

工作内容：
1. 创建 `src/lib/weapp-canvas-adapter.js` — 小程序 canvas 环境 shim
   - 模拟 `HTMLCanvasElement`、`Image`、`document.createElement` 等 Cubism SDK 依赖的浏览器 API
   - 包装小程序 `wx.createOffscreenCanvas`、`wx.createImage` 等
2. 创建 `src/lib/cubism-renderer.js` — 基于 Cubism SDK 原生 WebGL 渲染
   - 加载 `.model3.json` → `.moc3` → 纹理 → 创建模型实例
   - 每帧更新物理、动作、表情
   - 渲染到小程序 WebGL canvas
3. 创建 `src/lib/live2d-lip-sync.js` — 口型同步（从 H5 版 `tts-lip-sync.js` 迁移）
4. 将 `live2dcubismcore.min.js` 复制到 `src/lib/`
5. 模型资源从 `live2d-h5/public/models/` 复制到 `static/models/`

验收标准：
- 小程序里 `<canvas type="webgl">` 能加载并渲染 Live2D 模型
- idle 动作正常播放
- 表情切换正常
- 口型同步参数可驱动

### Phase 2: 前端 — 重写 chat.vue 原生聊天页 [status: ready]
**执行者：Codex**（涉及小程序 canvas + SSE + 状态管理，属于全栈工作）

工作内容：
1. 重写 `src/pages/chat/chat.vue`
   - 去掉 `web-view`
   - 上半屏：`<canvas type="webgl">` 渲染 Live2D
   - 下半屏：原生聊天消息列表 + 输入框
2. SSE 聊天逻辑保持不变（复用现有 `createSSERequest`）
3. TTS 播放保持不变（复用 `TtsPlayer` 组件）
4. 聊天消息渲染复用 `ChatMessage` 组件
5. 新会话、历史消息加载等逻辑从 H5 版迁移过来

验收标准：
- 页面无 `web-view`
- Live2D 渲染正常
- 聊天发送、流式接收、TTS 播放均正常
- 历史消息加载正常
- 新会话创建正常

### Phase 3: 集成测试与清理 [status: ready]
**执行者：brain（主会话）**

工作内容：
1. 编译小程序，检查报错
2. 验证完整聊天 + Live2D + TTS 流程
3. 清理不再需要的 `web-view` 相关代码
4. 更新 progress.md

## 风险与注意事项
- 小程序 canvas WebGL 支持有限，可能缺少某些 WebGL 扩展
- `live2dcubismcore.min.js` 引用了 `WebAssembly`，小程序需确认 WASM 支持
- 4096px 纹理在低端机可能需要降级
- 小程序包大小限制（moc3 + 纹理约 1-2MB）
