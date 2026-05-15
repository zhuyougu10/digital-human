# 发现记录

## 当前已知
- 项目包含 `medical-mp/`（UniApp + TypeScript + Vite）前端。
- 主要页面包括聊天、医生列表/详情、预约列表/详情、登录、我的页面和首页。
- 存在可复用组件：`DoctorCard.vue`、`AppointmentCard.vue`、`ChatMessage.vue`、`TtsPlayer.vue`、`SlotPicker.vue`。
- 需要重点查看 `src/pages.json`、`src/App.vue`、`src/uni.scss`、`src/main.ts` 和各页面组件。
- 视觉语言目前是浅蓝医疗风，但页面之间的卡片、按钮、间距、标题层级不一致，品牌统一性偏弱。
- 多个页面重复实现医生卡、预约卡、空状态和主操作按钮，适合抽象成统一风格。
- 聊天页是核心体验页，且同时包含 Live2D、SSE 和 TTS，改造时要避免打断交互链路。
- 首页、医生列表、预约列表、个人中心、登录页都是高频入口，适合作为第一批重构目标。

## 发现中的问题/风险
- 当前环境下规划脚本路径未直接命中，属于工具路径问题，先记录。
- 页面内存在大量硬编码颜色值和阴影值，若不先收敛到全局 token，后续维护成本会继续增加。

## 数字人表情/动作调研（2026-05-12）

### 前端可用控制点
- `medical-mp/src/pages/chat/chat.vue` 是数字人主编排器，负责 Live2D、SSE、TTS 和口型同步。
- `medical-mp/src/lib/cubism-renderer.js` 已暴露 `playMotion`、`setExpression`、`setMouthOpenY` 等能力。
- `medical-mp/src/components/TtsPlayer.vue` 通过 `LIVE2D_POST_MESSAGE` 驱动口型开始/停止。

### 后端可用语义信号
- `ChatController -> ChatServiceImpl -> SSE` 已具备 `token / complete / tts / error` 事件链路。
- `agentType`（TRIAGE / QA / SUMMARY / ENCYCLOPAEDIA）是最稳定的模式信号。
- `ChatMessage.metadata`、`SseMessageVO.metadata`、`ConversationSummary.severity / aiAssessment` 都能承载 cue 信息。
- `createAppointment`、`searchDoctorBySymptom`、`getAvailableSlots`、`searchKnowledge` 等 tool 结果可作为动作触发信号。

### 推荐的状态模型
- `idle`
- `talking`
- `thinking`
- `emotion_burst`
- `interrupt`

### 推荐优先级
1. `complete` / `tts`：最适合稳定表情与动作
2. `agentType` / tool name：适合模式级动作
3. `metadata` / summary：适合复盘态和长期情绪
4. `token`：只适合口型和轻微跟随

## 本轮实施后的确认结论（2026-05-12）

### 数字人 cue 链路已落地
- 后端 `medical-ai-service` 已在 `ChatServiceImpl` 中为 assistant 完整回复生成 `metadata.avatarCue`，并同时附加到 SSE `complete` / `tts` 事件以及 `ChatMessage.metadata` 持久化字段。
- 前端 `medical-mp/src/pages/chat/chat.vue` 已按 `metadata.avatarCue.bucket` 消费语义 cue，并映射到现有 Live2D 表情/动作。
- 已额外收口两个行为风险：
  - 同一轮回复不会因为 `complete` 和 `tts` 同时带 cue 而重复触发动作。
  - 播放结束 / 新会话 / 状态重置时会显式回到中性表情和 `Idle` 动作。
- `medical-mp/src/lib/cubism-renderer.js` 已补 `resetExpression()` 包装，避免使用无效的 `setExpression('')` 伪复位。

### 验证结论
- `medical-mp` 已通过 `npm run type-check`。
- `medical-mp` 已通过 `npm run build:mp-weixin`。
- `medical-ai-service` 需要使用 `-am` 连同依赖模块一起构建；按该方式执行后，`compile` 和 `ChatServiceImplTest` 均通过。

### Docker / 联调发现
- `doctor-service` 与 `appointment-service` 初始退出的根因是 Seata 配置源指向 Nacos，但 Nacos 中缺少 `service.vgroupMapping.medical_tx_group`。
- 已在 `medical-ai/docker/docker-compose.yml` 中给这两个服务补 `SEATA_CONFIG_TYPE=file`，现在两者都能正常启动。
- `medical-admin-web` 容器中存在旧前端 bundle，实际运行产物曾写死 `baseURL:"/api"`，导致 `/api/api/...`。
- 当前仓库源码侧已把 `medical-admin/.env.development` 修正为空 baseURL；同时对运行中的 `medical-admin-web` 做过容器内热修补以恢复现网请求路径。

### 风险备注
- `medical-admin-web` 的容器内热修补不是长期方案；若后续重建镜像，需要确保能成功拉取前端基础镜像并重新打入最新 bundle。

### Seata 本地启动补充（2026-05-15）
- `doctor-service` / `appointment-service` 在本地直跑时，主配置仍保留 Nacos 语义；仅在 `local` profile 下把 Seata 配置源切到 file。
- 本地手动启动这两个服务前，需要先起 `seata-server`，否则仍会在 Seata 客户端初始化阶段失败。
