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
