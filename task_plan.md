# Task Plan: AI 数字人医疗小助手系统

## Goal
构建基于 Spring Cloud + Spring AI + RAG + AI Agents + Vue3 + UniApp 的 AI 数字人医疗小助手系统（毕业设计）

## Current Phase
Phase 25 — SSE+TTS 卡死修复 — complete（全部阶段已完成）

## Phase Summary

| # | Phase | 描述 | Status |
|---|-------|------|--------|
| 1 | Requirements & Discovery | 架构方案对比、设计文档 | complete |
| 2 | Planning & Task Decomposition | 12 个实施计划 (docs/plans/00~11) | complete |
| 3 | Implementation | 01~11 全模块 TDD 开发 (~230 微任务) | complete |
| 4 | Testing & Verification | 端到端联调 12/12 PASS | complete |
| 5 | Delivery | README/部署/数据库/API/用户文档 | complete |
| 6 | API 联调审查与修复 | 5 批审查 + 8 批修复 (C1~C11, M1~M9) | complete |
| 7 | Mock 数据修复 | 9 项 mock→真实 API 替换 | complete |
| 8 | Final Review & Polishing | UI/文档/临时文件清理 | complete |
| 9 | 接口测试 | 109 用例全 GREEN (H2 隔离) | complete |
| 10 | Admin UI 重构美化 | 「医疗级 SaaS」设计系统, 20 个 .vue | complete |
| 11 | 真实接口集成测试 (Python) | 58 PASSED, 2 SKIPPED, 0 FAILED | complete |
| 12 | Feature Enhancements | 用户/排班/知识库/百科/RAG/Markdown 等 16 轮修复 | complete |
| 13 | 小程序 UI 重构与数字人集成 | 全量 H5 + Live2D + 口型同步 | complete |
| 14 | 智能导诊功能修复 | patientId 注入 + SSE 解析 + 消息类型 | complete |
| 15 | 医生数据补充与导诊闭环 | 10 医生/100 模板/120 号源, 5 轮 SSE 全通 | complete |
| 16 | 小程序与服务端对接审查 | 4C+3M+2L 修复 | complete |
| 17 | 数字人消息发送无响应 | H5 直连后端 SSE (绕过 postMessage) | complete |
| 18 | H5 SSE 跨域被拦截 | OPTIONS 放行 + apiBase 透传 | complete |
| 19 | H5 聊天界面优化 | 医疗蓝+绿双色系 redesign | complete |
| 20 | 小程序全页面 UI 优化 | 4 批 Gemini 并行 | complete |
| 21 | CosyVoice TTS 集成 | DashScope SDK 替换 NLS | complete |
| 22 | TTS WebSocket 依赖修复 | 恢复 dashscope okhttp 传递依赖 | complete |
| 23 | H5 TTS 无声修复 | 鉴权 fetch + blob 播放 + CORS 去重 | complete |
| 24 | 小程序功能增强 | TTS 分段合成/历史记录/会话管理/音频队列 | complete |
| 25 | SSE+TTS 卡死修复 | complete→tts 解耦 + 30s 超时 + AbortController | complete |

## Key Decisions

| Decision | Rationale |
|----------|-----------|
| 5 微服务拆分 (user/doctor/ai/appointment/knowledge) | 平衡毕设规模与微服务实践 |
| Spring AI M5 手动注入 FunctionCallbackResolver | 自动配置不支持 tool 注册 |
| 方案 A：全量 H5 聊天 UI | 解决 web-view 遮挡原生 UI |
| PixiJS v6 + Ticker 注册 | pixi-live2d-display 兼容性 |
| SSE complete 与 TTS 解耦 | 防止 TTS 阻塞文字流 |
| H5 直连后端 SSE | 绕过 postMessage 不实时的限制 |

## Critical Errors Reference

| Error | Resolution |
|-------|------------|
| Sa-Token `token-name` 同时作 HTTP header 和 Redis key 前缀 | 统一 5 服务 sa-token 配置 |
| Milvus OkHttp/Kotlin classpath 冲突 | 排除 okhttp 传递依赖 |
| Tika + Milvus Jetty 冲突 | 排除 jetty-client |
| Spring AI M5 FunctionCallbackResolver | 手动注入到 OpenAiChatModel 构造器 |
| DashScope TTS 需要 OkHttp WebSocket | 仅恢复 dashscope-sdk 的 okhttp 依赖 |
| TTS 同步阻塞 SSE Flux | 拆分为 complete + 异步 tts 事件, 30s timeout |
| Codex PowerShell 写 UTF-8 BOM | 合并后 Python 脚本批量移除 |
| `.publishOn(Schedulers.boundedElastic())` 必须紧跟 `chatModel.stream()` | 否则 tool call 在 Netty IO 线程 block |

## Notes
- 项目 docs/plans/ 下有 12 个详细实施计划 (00-overview ~ 11-docker-deploy)
- 全部 230+ 微任务已完成
- Update phase status as you progress: pending → in_progress → complete
