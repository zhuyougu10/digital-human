# Findings & Decisions

> 项目：AI 数字人医疗小助手系统（毕业设计）
> 创建日期：2026-02-27
> 最后更新：2026-02-27

## Requirements

- **项目类型**：毕业设计，面向医疗咨询与导诊场景的 AI 数字人系统
- **三端架构**：
  - 患者端：UniApp 小程序 + Live2D 数字人 + TTS 语音，提供数字人医生对话、病情咨询、导诊、一键挂号预约
  - 医生端：Vue3 网页，提供画像维护、查看预约患者对话摘要、百科助手
  - 管理端：Vue3 网页，提供用户管理、知识库管理、科室管理、系统配置、数据看板
- **核心业务流**：患者描述症状 → 导诊 Agent 多轮问诊 → 匹配科室+医生 → 选医生 → 查号源 → 创建预约 → 异步生成对话摘要 → 医生接诊
- **4 个 AI Agent**：导诊 Agent、医疗问答 Agent、对话摘要 Agent、医生百科 Agent
- **技术要求**：Spring Cloud 微服务、Spring AI + RAG、SSE 流式对话、Live2D 数字人、Docker Compose 部署

## Research Findings

- 架构方案对比后选定 **方案A：5微服务架构**（user/doctor/ai/appointment/knowledge），平衡了毕设规模与微服务实践
- Spring AI 1.0.0-M5 需要 Spring Milestones 仓库（`https://repo.spring.io/milestone`）
- Sa-Token 在 Gateway（WebFlux）和 Service（WebMVC）中使用不同 starter：Gateway 用 `sa-token-reactor-spring-boot3-starter`，Service 用 `sa-token-spring-boot3-starter`
- Live2D 在小程序中需通过 `<web-view>` 内嵌 H5 实现（pixi.js + pixi-live2d-display），H5 与小程序通过 postMessage 双向通信
- TTS 方案：阿里云智能语音 API 返回音频 URL → H5 播放音频 + 解析音量 → 驱动 Live2D mouth 参数
- Milvus 向量数据库需要 etcd + minio 作为依赖服务
- 每个微服务独立数据库：medical_user / medical_doctor / medical_ai / medical_appointment / medical_knowledge

## Technical Decisions

| Decision | Rationale |
|----------|-----------|
| 5 微服务拆分（user/doctor/ai/appointment/knowledge） | 平衡毕设规模与微服务实践，每个服务职责清晰 |
| Spring Boot 3.3.6 + Spring Cloud 2023.0.4 | 当前稳定版，与 Spring Cloud Alibaba 2023.0.3.2 版本兼容 |
| Spring AI 1.0.0-M5 | 支持 Function Calling / RAG，是 Spring 生态 AI 集成的最新里程碑版本 |
| DeepSeek API（主力）+ 通义千问 API（备选/Embedding） | DeepSeek 性价比高且中文能力强，通义千问作为 Embedding 和备选 |
| Sa-Token 替代 Spring Security | 更轻量、API 友好、支持 Redis 会话共享，适合微服务 |
| MyBatis-Plus 3.5.9 + Druid 连接池 | 国内生态成熟，代码生成+CRUD 简化，Druid 监控能力强 |
| Milvus 作为向量数据库 | 比 ChromaDB 更适合生产环境，支持分布式，Spring AI 有 Milvus VectorStore 集成 |
| Nacos 同时作为注册中心和配置中心 | 减少基础设施依赖，一个组件解决服务发现+配置管理 |
| 每服务独立数据库 | 微服务数据隔离原则，避免跨库事务 |
| Maven 多模块聚合结构 | 父 POM 统一版本管理，common/api/service/gateway 四层分离 |
| Knife4j（OpenAPI3）文档 | 基于 Swagger 增强，提供友好的在线 API 文档 |
| MapStruct 对象映射 | 编译期生成映射代码，零反射开销，类型安全 |
| Docker Compose 部署 | 毕设场景足够，一键启动全部基础设施+服务 |
| Live2D via web-view 内嵌 H5 | 小程序原生不支持 WebGL Live2D，H5 桥接是唯一可行方案 |
| SSE 流式对话 | 相比 WebSocket 更简单，单向推送适合 LLM 流式输出场景 |

## Architecture Overview

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
                    │   Spring Cloud Gateway     │ 统一入口 / JWT 鉴权 / 路由
                    └─────────────┬─────────────┘
                                  │ OpenFeign / LoadBalancer
          ┌───────────┬───────────┼───────────┬───────────┐
          ▼           ▼           ▼           ▼           ▼
    ┌──────────┐┌──────────┐┌──────────┐┌──────────┐┌──────────┐
    │  user-   ││ doctor-  ││   ai-    ││ appoint- ││knowledge-│
    │ service  ││ service  ││ service  ││ ment-svc ││ service  │
    │  :8081   ││  :8082   ││  :8083   ││  :8084   ││  :8085   │
    └────┬─────┘└────┬─────┘└────┬─────┘└────┬─────┘└────┬─────┘
         │           │           │           │           │
         ▼           ▼           ▼           ▼           ▼
    ┌─────────────────────┐ ┌─────────┐ ┌──────────────────┐
    │    MySQL :3306      │ │Redis:6379│ │  Milvus :19530   │
    └─────────────────────┘ └─────────┘ └──────────────────┘
                    ┌─────────────────────┐
                    │   Nacos :8848       │
                    └─────────────────────┘
```

## Backend Project Structure

```
medical-ai/
├── pom.xml                              # 父 POM（版本锁定）
├── medical-common/
│   ├── medical-common-core/             # 通用工具、异常、响应体、Knife4j
│   ├── medical-common-security/         # Sa-Token 鉴权
│   ├── medical-common-mybatis/          # MyBatis-Plus + Druid 配置
│   └── medical-common-redis/            # Redis 缓存配置
├── medical-gateway/                     # Spring Cloud Gateway + Sa-Token
├── medical-service/
│   ├── medical-user-service/            # 用户注册登录、角色权限、微信登录
│   ├── medical-doctor-service/          # 医生画像、科室管理、排班
│   ├── medical-ai-service/              # 对话管理、4个Agent、RAG、TTS
│   ├── medical-appointment-service/     # 号源查询、预约挂号
│   └── medical-knowledge-service/       # 知识库管理、文档解析、向量检索
├── medical-api/                         # Feign 接口定义（服务间调用契约）
│   ├── medical-user-api/
│   ├── medical-doctor-api/
│   ├── medical-appointment-api/
│   └── medical-knowledge-api/
└── docker/
    ├── docker-compose.yml               # MySQL + Redis + Nacos + Milvus
    ├── mysql/init.sql                   # 5 个数据库初始化
    └── nginx/
```

## Core Database Tables

| 服务 | 核心表 |
|------|--------|
| user-service | `sys_user`, `sys_role`, `sys_user_role`, `wx_user_binding` |
| doctor-service | `doctor_profile`, `department`, `doctor_department`, `schedule_template`, `schedule_slot` |
| ai-service | `chat_session`, `chat_message`, `conversation_summary` |
| appointment-service | `appointment`, `appointment_slot` |
| knowledge-service | `knowledge_base`, `knowledge_document`, `knowledge_chunk` + Milvus collection |

## AI Agent Design

| Agent | 用途 | 绑定工具 (Function Calling) |
|-------|------|---------------------------|
| 导诊 Agent | 医疗分诊，多轮问答收集症状 | `searchDoctorBySymptom`, `getAvailableSlots`, `createAppointment` |
| 医疗问答 Agent | 科普助手，基于 RAG 回答 | `searchKnowledge`, `getRelatedArticles` |
| 对话摘要 Agent | 后台异步，生成结构化摘要 | 无工具，纯 Prompt 驱动 |
| 医生百科 Agent | 面向医生的专业查询 | `searchKnowledge`, `searchDrugInfo`, `searchGuideline` |

## Gateway Route Mapping

| Path | Service | Port |
|------|---------|------|
| `/api/user/**` | medical-user-service | 8081 |
| `/api/doctor/**` | medical-doctor-service | 8082 |
| `/api/ai/**` | medical-ai-service | 8083 |
| `/api/appointment/**` | medical-appointment-service | 8084 |
| `/api/knowledge/**` | medical-knowledge-service | 8085 |

## Issues Encountered

| Issue | Resolution |
|-------|------------|
| Codex 通过 PowerShell 写入 Java 文件带 UTF-8 BOM | 后续委派时提醒 Codex 使用无 BOM 编码 |
| Codex PowerShell \`r\`n 转义失败变字面量 | 对多行文本内容优先由 OpenCode 用 Write 工具直接写入 |
| MyBatis-Plus 3.5.9 PaginationInnerInterceptor 找不到 | 需额外依赖 `mybatis-plus-jsqlparser`，Codex 自动修复 |
| CCB ask 命令需要 CCB_CALLER 环境变量 | `export CCB_CALLER=claude` 后调用 `ask codex` |

## Resources

- 架构设计文档：`docs/plans/2026-02-27-medical-ai-assistant-design.md`
- 实施计划总览：`docs/plans/00-overview.md`
- 项目初始化计划：`docs/plans/01-project-init.md`（12 Tasks）
- 公共模块计划：`docs/plans/02-common-modules.md`（10 Tasks）
- 用户服务计划：`docs/plans/03-user-service.md`（12 Tasks）
- 医生服务计划：`docs/plans/04-doctor-service.md`（10 Tasks）
- 知识库服务计划：`docs/plans/05-knowledge-service.md`（12 Tasks）
- AI 服务计划：`docs/plans/06-ai-service.md`（13 Tasks）
- 预约服务计划：`docs/plans/07-appointment-service.md`（6 Tasks）
- 网关服务计划：`docs/plans/08-gateway.md`（5 Tasks）
- 管理端前端计划：`docs/plans/09-frontend-admin.md`（18 Tasks）
- 小程序端计划：`docs/plans/10-frontend-mp.md`（14 Tasks）
- 部署联调计划：`docs/plans/11-docker-deploy.md`（9 Tasks）
- **总计约 230 个微任务**

## Dependency / Execution Order

```
01-project-init
    │
    ▼
02-common-modules
    │
    ├──────────┬──────────┬──────────┐
    ▼          ▼          ▼          ▼
03-user    04-doctor  05-knowledge 08-gateway
    │          │          │
    ├──────────┤          │
    ▼          ▼          ▼
07-appoint  06-ai-service ◄────────┘
    │          │
    ▼          ▼
09-frontend-admin
10-frontend-mp
    │
    ▼
11-docker-deploy
```

**建议执行策略**：串行核心路径 01→02→03→06→10（患者端核心链路优先可演示），并行辅助模块 04/05/07/08。

---
*Update this file after every 2 view/browser/search operations*
*This prevents visual information from being lost*
- [2026-02-28] `medical-common-core` contains `BaseEntity` with MyBatis-Plus annotations, so the module must declare `com.baomidou:mybatis-plus-annotation` (or equivalent MyBatis-Plus dependency) to compile independently.
- [2026-02-28] `medical-api/medical-user-api` uses Lombok (`@Data` in DTOs), so `pom.xml` should include `org.projectlombok:lombok` with `provided` scope.
- [2026-02-28] `medical-service/medical-user-service` requires both `org.projectlombok:lombok` (`provided`) and `org.springframework.boot:spring-boot-starter-web` for Lombok annotations and `@RestController` usage.
- [2026-02-28] `medical-service/medical-doctor-service` currently contains only the bootstrap class (`DoctorServiceApplication`) and no domain/mapper/resources for doctor data yet; Tasks 1-4 need full initial data-layer scaffolding.
- [2026-02-28] Both `medical-service/medical-doctor-service/pom.xml` and `medical-api/medical-doctor-api/pom.xml` are missing Lombok (`provided`), and doctor-service also needs `spring-boot-starter-web` for upcoming controller layer compatibility.
- [2026-02-28] `UserConstants` role keys are uppercase (`ADMIN`/`DOCTOR`/`PATIENT`), so all `@SaCheckRole` checks in doctor-service should use those uppercase constants directly.
- [2026-02-28] In `medical-doctor-service` DDL, `doctor_profile.user_id` is `NOT NULL`, while `DoctorProfileDTO` (Task 4 spec) does not include `userId`; service-side create/update flow must account for this mismatch to avoid runtime insert failures.
- [2026-02-28] Root medical-ai/pom.xml already imports spring-ai-bom (1.0.0-M5) and manages io.milvus:milvus-sdk-java (currently via property milvus-sdk.version=2.4.3), so knowledge-service can add those artifacts without local version declarations.
- [2026-02-28] Local environment has no cached io.milvus:milvus-sdk-java artifacts under C:\Users\���͹�\.m2\repository, and mvn is unavailable, so Milvus v2 API usage must follow documented class signatures without local compile-time verification.
- [2026-02-28] Milvus Java v2 API docs indicate SearchReq uses io.milvus.v2.service.vector.request.data.FloatVec and returns List<List<SearchResp.SearchResult>>; collection creation supports schema via client.createSchema() + AddFieldReq + IndexParam with COSINE metric.
- [2026-02-28] PageQuery in medical-common-core is a plain DTO (no helper to build MyBatis Page), so service pagination must instantiate 
ew Page<>(pageNum, pageSize) explicitly and map to PageResult.of(...).
- [2026-02-28] Existing services/controllers use com.medical.common.core.domain.R (no com.medical.common.core.result.R package), so new knowledge-service controller/Feign interfaces should import domain.R for consistency and compilation.
- [2026-02-28] Full reactor compile currently fails before reaching knowledge-service due unrelated doctor-service imports (com.medical.api.doctor.dto.DoctorInfoDTO / SlotInfoDTO) not resolving, so cross-module baseline must be fixed for end-to-end mvn clean compile -f medical-ai/pom.xml verification.
- [2026-02-28] `tika-parsers-standard-package:2.9.1` 只包含各 parser module，不传递依赖 `tika-core`（`org.apache.tika.Tika` facade 类所在包），必须显式添加 `tika-core:2.9.1`。
- [2026-02-28] Milvus Java SDK v2 `SearchResp.SearchResult.getScore()` 返回 `Float`，而业务 VO 用 `Double`，需显式 `getScore().doubleValue()` 转换。
- [2026-02-28] `@Async` 注解的方法需要通过代理调用才能生效；在同一个类内部调用时需使用 `@Lazy` 自注入模式（`private final @Lazy KnowledgeBaseService self`）绕过 this 调用失效问题。
- [2026-02-28] Codex 主动扩展了 `ErrorCode` 枚举，添加了 `DOCUMENT_PARSE_ERROR(5002)`, `EMBEDDING_ERROR(5003)`, `KNOWLEDGE_BASE_NOT_FOUND(5004)`, `PARAM_ERROR(4001)`, `NOT_FOUND(4004)` 等。
- [2026-02-28] 05-knowledge-service 全量编译通过（18 模块 BUILD SUCCESS），25 个 Java 源文件。
- [2026-02-28] `medical-api/medical-appointment-api` 当前仅有 `package-info.java`，且 `pom.xml` 仅含 common-core + openfeign，尚未声明 Lombok；新增 DTO 时需要补 `lombok`（`provided`）。
- [2026-02-28] 现有模块实体/Mapper 约定为：实体使用 `@Data + @EqualsAndHashCode(callSuper = true) + @TableName`，Mapper 使用 `@Mapper + BaseMapper<T>`；`medical-ai-service` 当前仅有启动类，数据层需从零补建。

- [2026-02-28] medical-service/medical-ai-service currently has domain entities/VOs/mappers and agent implementations (including SUMMARY in AgentFactory), plus Spring AI/WebFlux dependencies already declared in pom.xml; service-layer classes for chat/tts/summary were not present before Task 7-9.
- [2026-02-28] medical-service/medical-ai-service currently has no controller package or *Controller.java; Task 10-12 should introduce initial REST controller layer (/chat, /summary, /encyclopedia).
- [2026-02-28] `medical-ai-service` entity classes (`ChatSession`, `ChatMessage`, `ConversationSummary`) must declare explicit primary key field `id` (e.g., `@TableId(type = IdType.AUTO)`), because `BaseEntity` does not contain an id property; otherwise service/VO mapping calls like `getId()` fail at compile time.
- [2026-02-28] Spring AI 1.0.0-M5 的 `OpenAiChatOptions.Builder` 中 `withModel()`, `withTemperature()`, `withMaxTokens()`, `withFunctions()` 已标记 deprecated，但仍可编译通过。
- [2026-02-28] ai-service 同时引入 spring-boot-starter-web 和 spring-boot-starter-webflux，web 用于 REST Controller，webflux 仅用于 Reactor Flux (SSE 流式响应)。
- [2026-02-28] 06-ai-service 全量编译通过（18 模块 BUILD SUCCESS），37 个 Java 源文件。
- [2026-02-28] Running `mvn ... -rf :medical-ai-service` from root only resumes downstream modules (`medical-ai-service` and later), so internal upstream artifacts (e.g., `medical-common-*`, `medical-*-api`) must already be installed in local Maven repo; otherwise dependency resolution fails before compilation.
