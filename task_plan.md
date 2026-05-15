# 任务计划记录

## 已完成事项

### 1. UniApp 前端重构
- [x] 梳理 `medical-mp` 页面结构、全局样式与组件复用点。
- [x] 统一视觉语言，重构关键页面布局、配色、间距和交互状态。
- [x] 完成 `medical-mp` 类型检查与小程序构建验证。

### 2. 数字人表情 / 动作联动
- [x] 调研前端 Live2D/TTS/聊天编排与后端 AI 流式链路。
- [x] 设计并实现基于 `metadata.avatarCue` 的前后端语义 cue 契约。
- [x] 完成前端 bucket → Live2D 表情/动作映射。
- [x] 修复 cue 双触发与中性状态复位问题。
- [x] 完成 `medical-ai-service` 目标测试和 `medical-mp` 类型/构建验证。

### 3. Docker 与管理端联调
- [x] 启动后端 Docker 编排。
- [x] 修复 `doctor-service` / `appointment-service` 的 Seata 启动失败问题。
- [x] 定位并修复 `medical-admin-web` 的 `/api/api/...` 请求路径问题（源码修复 + 运行容器热修补）。

## 当前遗留注意事项
- `medical-admin-web` 当前运行实例包含容器内热修补；后续重新 build 镜像时，需要确保新 bundle 带上最新源码修复。
- `medical-admin` 的 Docker 重建曾受镜像拉取网络问题影响，若后续重建失败需优先检查镜像源可达性。
- Xiaomi MiMo TTS 现通过服务端 `wav -> mp3` 转码来兼容既有前端契约；若后续要开放非 mp3 格式，应单独做接口契约升级，而不是在本次 provider 切换里混改。

## 风险
- 运行中容器的热修补不会自动反映到未来的新镜像构建结果。
- 数字人动作能力仍受当前 Live2D 模型现有表达式和 motion 资源上限约束。
