# Findings

## 项目结构
- 小程序框架：uni-app（Vue 3 + TypeScript）
- Live2D 实现：`medical-mp/live2d-h5/` — 独立 Vite 项目，pixi.js v6 + pixi-live2d-display v0.4
- 聊天页：`src/pages/chat/chat.vue` — 通过 web-view 嵌入 live2d-h5
- 模型格式：Cubism 4（.model3.json / .moc3）
- Cubism Core：`live2dcubismcore.min.js` 已有
- SSE 工具：`src/utils/sse.js` — 基于 uni.request enableChunked
- 现有组件：ChatMessage.vue、TtsPlayer.vue、DoctorCard.vue、SlotPicker.vue

## 关键技术点
- 小程序 `<canvas type="webgl">` 支持 WebGL 1.0
- 需要适配的浏览器 API：HTMLCanvasElement、Image、document.createElement、requestAnimationFrame
- Cubism SDK for Web 的渲染层可以直接用 WebGL，不强依赖 pixi
- pixi-live2d-display 封装了加载/渲染/动作/表情/物理，需要在适配层重新实现这些

## 小程序 Canvas 限制
- 无 DOM，不能用 document.createElement('canvas')
- Image 需要用 wx.createImage() 或 canvas.createImage()
- 无 window 对象
- requestAnimationFrame 由 canvas.requestAnimationFrame 提供
- WebGL context 通过 canvas.getContext('webgl') 获取
