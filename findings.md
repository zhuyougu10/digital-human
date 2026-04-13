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

## 后台登录 502 调试发现
- 通过 admin-web 入口 `POST http://127.0.0.1/api/user/auth/login` 返回 502。
- 同样请求直接打 gateway `POST http://127.0.0.1:8080/api/user/auth/login` 返回 200，并能成功登录。
- 这说明问题不在 user-service 业务逻辑，而在 `medical-admin-web` nginx 到 `medical-gateway` 的代理层。
- `medical-admin/nginx.conf` 当前写法是固定上游：`proxy_pass http://medical-gateway:8080/api/;`。
- 在 Docker 环境下，nginx 默认不会持续重新解析这个上游域名。gateway 容器重建后 IP 改变，而 admin-web 若未重启，就会继续使用旧 IP，导致 502。
- 手工 `docker restart medical-admin-web` 后登录立刻恢复为 200，证实根因是 admin-web 容器内 nginx 的上游地址缓存/静态解析。
