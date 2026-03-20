# Findings & Decisions

> 项目：AI 数字人医疗小助手系统（毕业设计）
> 最后更新：2026-03-21

## Requirements

- **三端架构**：患者端 (UniApp+Live2D+TTS) / 医生端 (Vue3) / 管理端 (Vue3)
- **核心业务流**：患者描述症状 → 导诊 Agent 多轮问诊 → 匹配科室+医生 → 查号源 → 创建预约 → 异步摘要 → 医生接诊
- **4 个 AI Agent**：导诊 / 医疗问答 / 对话摘要 / 医生百科
- **技术栈**：Spring Cloud 微服务 + Spring AI + RAG + SSE 流式 + Live2D + Docker Compose

## Architecture

```
                    ┌─────────────┐
                    │  微信小程序   │ UniApp + Live2D + TTS
                    └──────┬──────┘
                           │
                    ┌──────┴──────┐
                    │  Vue3 网页端  │ 医生端 + 管理端
                    └──────┬──────┘
                           │ HTTPS
             ┌─────────────┴─────────────┐
             │   Spring Cloud Gateway     │ Sa-Token + CORS + 路由
             └─────────────┬─────────────┘
                           │ OpenFeign / LoadBalancer
      ┌──────────┬─────────┼──────────┬───────────┐
      ▼          ▼         ▼          ▼           ▼
 user:8081  doctor:8082  ai:8083  appoint:8084  knowledge:8085
      │          │         │          │           │
      ▼          ▼         ▼          ▼           ▼
 MySQL:3306    Redis:6379   Milvus:19530    Nacos:8848
```

## Technical Decisions

| Decision | Rationale |
|----------|-----------|
| 5 微服务 (user/doctor/ai/appointment/knowledge) | 平衡毕设规模与微服务实践 |
| Spring Boot 3.3.6 + Cloud 2023.0.4 + AI 1.0.0-M5 | 版本兼容 |
| DeepSeek (主力) + 通义千问 (Embedding) | 性价比 + 中文能力 |
| Sa-Token 替代 Spring Security | 轻量 + Redis 会话共享 |
| MyBatis-Plus 3.5.9 + Druid | CRUD 简化 + 连接池监控 |
| Milvus 向量数据库 | Spring AI 集成 + 分布式 |
| 全量 H5 聊天 UI (方案 A) | 解决 web-view 遮挡 |
| PixiJS v6 | pixi-live2d-display@0.4.0 兼容性 |
| H5 直连后端 SSE | 绕过 postMessage 不实时 |
| SSE complete 与 TTS 解耦 | 防止 TTS 阻塞文字流 |

## Database Tables

| 服务 | 核心表 |
|------|--------|
| user | sys_user, sys_role, sys_user_role, wx_user_binding |
| doctor | doctor_profile, department, doctor_department, schedule_template, schedule_slot |
| ai | chat_session, chat_message, conversation_summary |
| appointment | appointment |
| knowledge | knowledge_base, knowledge_document, knowledge_chunk + Milvus collection |

## AI Agent Design

| Agent | 用途 | 工具 (Function Calling) |
|-------|------|------------------------|
| 导诊 | 多轮问答收集症状 | searchDoctorBySymptom, getAvailableSlots, createAppointment |
| 医疗问答 | 科普 RAG | searchKnowledge, getRelatedArticles |
| 对话摘要 | 后台异步 | 无工具，纯 Prompt |
| 医生百科 | 专业查询 | searchKnowledge, searchDrugInfo, searchGuideline |

## Gateway Routes

| Path | Service | Port |
|------|---------|------|
| /api/user/** | medical-user-service | 8081 |
| /api/doctor/** | medical-doctor-service | 8082 |
| /api/ai/** | medical-ai-service | 8083 |
| /api/appointment/** | medical-appointment-service | 8084 |
| /api/knowledge/** | medical-knowledge-service | 8085 |

## Architectural Constraints (易踩坑)

1. Sa-Token `token-name` 同时作 HTTP header 名和 Redis key 前缀，Gateway 与服务必须一致
2. Milvus SDK 必须排除 `okhttp` 传递依赖 (Kotlin classpath 冲突)
3. Tika + Milvus 必须排除 `jetty-client` (覆盖 Spring HTTP 客户端)
4. Spring AI M5 需手动注入 `FunctionCallbackResolver` 到 `OpenAiChatModel`
5. 每服务独立数据库，跨服务调用走 Feign (medical-api/)
6. H2 test profile: `@ActiveProfiles("test")` + `TestAiApplication` (排除 DataSource/Redis)
7. `chatModel.stream(prompt)` 后必须 `.publishOn(Schedulers.boundedElastic())` 防止 tool call 阻塞 Netty IO
8. DashScope TTS 需要 OkHttp WebSocket，不能排除 dashscope-sdk 的 okhttp 传递依赖

## Resources

- 架构设计文档：`docs/plans/2026-02-27-medical-ai-assistant-design.md`
- 实施计划 (12 个)：`docs/plans/00-overview.md` ~ `docs/plans/11-docker-deploy.md`
- 交付文档：`README.md`, `docs/deployment-guide.md`, `docs/database-design.md`, `docs/api-reference.md`, `docs/user-guide.md`
