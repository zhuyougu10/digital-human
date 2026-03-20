# Progress Log

> 项目：AI 数字人医疗小助手系统
> 最后更新：2026-03-21

## Historical Sessions Summary

| 日期 | Phase | 关键成果 |
|------|-------|---------|
| 02-27 | 1-2 | 需求确认 + 架构设计 + 12 个实施计划 |
| 02-28 | 3 | 01~08 模块实现 (common/user/doctor/knowledge/ai/appointment/gateway) |
| 03-01 | 3-4 | 09~11 前端+部署, Docker 14/14 UP, 联调 12/12 PASS |
| 03-02 | 5-6 | 交付文档 5 篇 + API 联调审查 (11C+9M 问题发现并修复) |
| 03-03 | 7-10 | Mock 修复 + Admin UI 重构 (医疗级 SaaS) + 109 单元测试 |
| 03-05 | 11 | Python 集成测试 60 用例, 4 根因修复 → 58 PASSED 2 SKIPPED |
| 03-06 | 12 | 用户/医生绑定 + 排班权限 + 模板稳定性 |
| 03-07 | 12 | KB 路由/RAG/Embedding/Markdown/SSE 等 12 轮修复 |
| 03-08 | 13 | 小程序 UI 重构 + Live2D 位姿 + 全量 H5 |
| 03-20 | 14-24 | 导诊修复 + 医生数据 + MP API 对接 + CORS + UI 优化 + TTS 集成 + 功能增强 |
| 03-21 | 25 | SSE+TTS 卡死修复 (本次) |

## Session: 2026-03-21 (Phase 25 — SSE+TTS 卡死修复)

### 问题描述
跟数字人对话时，前端消息成功发到后端，后端生成语音后卡死：文字不返回，语音也不返回。

### 根因

| 层 | 根因 |
|----|------|
| 后端 | `concatWith(Mono.fromCallable(...))` 中 TTS 同步阻塞 SSE 流，complete 事件永不发出 |
| 后端 | DashScope `SpeechSynthesizer.call()` 无超时控制 |
| 前端 | `playAuthenticatedAudio` 在 `play()` 就 resolve，音频掐断 |
| 前端 | SSE 解析 `.trim()` 吞空格 token |
| 前端 | 旧 SSE 请求无取消机制 |

### 修复

**后端 (Codex → cherry-pick + BOM 修复)**
- `ChatServiceImpl.java`: 先发 complete(ttsUrl=null)，再异步追加 tts/tts_error 事件，30s timeout
- `TtsServiceImpl.java`: CompletableFuture 30s 超时包裹 DashScope 调用
- 新增 ChatServiceImplTest (134行) + TtsServiceImplTest (68行)

**前端 (Gemini → cherry-pick)**
- `audio-player.js`: onended 才 resolve
- `main.js`: SSE data 行保留空格 + AbortController + tts/tts_error 事件支持

### 验证
- Maven compile: **SUCCESS**
- live2d-h5 build: **SUCCESS** (5.17s)

### Commits
- `194e94b` agent(fix-frontend-sse-tts-v1): gemini result
- `74b0649` fix(ai): 解耦SSE文字流与TTS合成，修复对话卡死问题

### Errors
| Error | Resolution |
|-------|------------|
| Codex 0 分 (idle terminated + 无 Maven + 越界写规划文件) | 代码正确，手动 cherry-pick 排除越界文件 |
| 4 个 Java 文件 UTF-8 BOM | Python 脚本批量移除 |

## Session: 2026-03-21 (Phase 26 — H5 聊天界面只显示 loading)

### 问题描述
数字人聊天界面不显示文字输出内容，只显示 loading（CSS `:empty` 动画 `···`）。

### 调研 (Gemini 前端 + Codex 后端 并行)
- **后端结论**: SSE 事件序列正确 (`type=token` → `complete` → `tts`)，`publishOn` 位置无误
- **前端结论**: Bug 集中在 `medical-mp/live2d-h5/src/main.js`

### 根因

| # | Bug | 位置 | 影响 |
|---|-----|------|------|
| 1 | `buffer = blocks.pop()` — 流结束后残留 buffer 未处理 | main.js L401 | complete 事件可能被吞 |
| 2 | `currentAiBubble = null` 在 RAF 回调之前执行 | main.js L477 | **全部文字渲染被跳过** |

**核心机制**: `await reader.read()` 解析为 microtask，不让位给 `requestAnimationFrame` 宏任务。当 LLM 回复快或网络批量到达时，整个 while 循环在一帧内跑完，一次 RAF 都没执行。循环结束后 `currentAiBubble = null` 导致 RAF 回调 bail out，bubble.innerHTML 从未被写入，CSS `:empty` 持续显示 `···`。

### 修复 (Boss 微操)
- `main.js`: while 循环退出后、状态重置前:
  1. 刷新残留 buffer（处理最后一个事件）
  2. 同步调用 `updateAiBubble()`（保证最终渲染）

### 验证
- live2d-h5 build: **SUCCESS** (3.28s)
