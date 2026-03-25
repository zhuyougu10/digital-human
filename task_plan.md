# Task Plan: AI 数字人医疗小助手系统

## Goal
构建基于 Spring Cloud + Spring AI + RAG + AI Agents + Vue3 + UniApp 的 AI 数字人医疗小助手系统（毕业设计）

## Current Phase
Planning Catchup — complete

## Current Session Checklist

- [x] 盘点运行中的服务、容器、路由与业务模块数量
- [x] 识别核心接口与已有测试/脚本入口
- [x] 测量核心接口响应时间、SSE 首包/全程耗时、知识检索表现
- [x] 验证导诊到挂号闭环成功率与稳定性压测结果
- [x] 汇总部署效率、覆盖范围与证据来源

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
| 27 | 系统指标盘点与验证 | 运行态规模、接口时延、SSE、闭环、稳定性、部署效率、覆盖证据汇总 | complete |
| 28 | 知识检索 5003/404 排障 | 定位并修复 knowledge-service DashScope embedding `base-url` 缺少 `/v1` 的问题 | complete |
| 29 | Sentinel 限流与熔断降级保护 | 完成 gateway 入口限流、AI/知识检索熔断降级、预约/号源热点保护的首批本地规则落地 | complete |
| 30 | Seata 分布式事务设计与实施计划 | 完成 AT 模式方案设计、spec review 与实施计划编写 | complete |

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
- 2026-03-23 catchup: 工作区存在 2 个未提交改动（全局异常处理、live2d-h5 `marked` CDN 版本固定）和 3 个 JVM 崩溃日志待清理/确认
- 2026-03-23 规划文件复核：当前工作区除规划文件自身外，仍有 `GlobalExceptionHandler.java`、`medical-mp/live2d-h5/index.html` 两处代码改动，以及删除文件 `语音合成.md` 待确认是否需要恢复/保留删除
- Phase 27 已完成：系统规模、路由、覆盖范围、核心接口时延、SSE 时延、预约闭环、稳定性与部署效率均已形成文字证据，唯一未闭合项为知识检索 `5003/404` 异常需单独排障
- Phase 28 启动：按 systematic-debugging 流程先做复现、证据收集、变更对比和责任边界定位，再决定是否进入修复
- Phase 28 已完成：`medical-knowledge-service` 的 DashScope `spring.ai.openai.base-url` 已从 `.../compatible-mode` 修正为 `.../compatible-mode/v1`，并通过配置比对与 `KnowledgeBaseControllerTest` (`18/18`) 验证
- Phase 29 启动：Sentinel 采用混合方案，网关先做登录/AI对话/知识检索入口限流，服务内覆盖 chatStream、TTS、knowledge embedding、预约创建与号源热点保护
- Phase 29 已完成：5 个模块已接入 Sentinel 依赖和基础配置，gateway 已落地统一 block 响应与首批 API 规则，`ai-service`/`knowledge-service`/`appointment-service`/`doctor-service` 已完成关键资源限流或熔断逻辑，并通过 5 组定向测试与跨模块 compile 验证
- 2026-03-24 catchup：已按 `planning-with-files` 复核工作区；repo-local `session-catchup.py` 运行无输出，当前待同步改动仍主要集中在 `.gitignore`、`GlobalExceptionHandler.java`、`medical-mp/live2d-h5/index.html`、规划文件，以及删除项 `语音合成.md`
- 2026-03-25 catchup：再次按 `planning-with-files` 复核工作区；repo-local `session-catchup.py` 仍无输出，但 `git status --short` 显示除规划文件外，仍存在 `.gitignore`、`GlobalExceptionHandler.java`、`medical-mp/live2d-h5/index.html`、删除项 `语音合成.md`，以及未跟踪目录 `docs/superpowers/`（含 Sentinel 设计/实施文档）待同步
- 2026-03-25 Phase 30 完成：已产出 `docs/superpowers/specs/2026-03-25-seata-distributed-transaction-design.md` 与 `docs/superpowers/plans/2026-03-25-seata-distributed-transaction-implementation.md`，可在新窗口按计划执行 Seata 集成
- 2026-03-25 catchup（本次会话）：再次执行 repo-local `session-catchup.py` 仍无输出；`git diff --stat` / `git status --short` 结果与上一轮一致，当前待同步改动仍集中在 `.gitignore`、`GlobalExceptionHandler.java`、`medical-mp/live2d-h5/index.html`、删除项 `语音合成.md` 与未跟踪目录 `docs/superpowers/`
- `medical-ai/docker/mysql/init/*.sql` 通过 `/docker-entrypoint-initdb.d` 仅在 MySQL 首次初始化或新 volume 时自动执行；已有 volume 需手动补 SQL 或重建 volume。
- `tests/test_10_seata.py` 当前定位为集成冒烟/一致性检查，不单独证明故障注入下的 Seata 回滚语义。
- 2026-03-25 运行态验证补充：已在 Docker 环境手动补齐 Nacos `service.vgroupMapping.medical_tx_group=default` 与现有 MySQL volume 的 `undo_log` 表后，`pytest tests/test_10_seata.py -v` 实测 `3 passed`
- Update phase status as you progress: pending → in_progress → complete
