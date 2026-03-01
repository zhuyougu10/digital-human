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
- [2026-03-01] `medical-mp` 当前仍是 UniApp 初始骨架：`src` 仅包含默认 `pages/index/index.vue`、基础配置和静态资源，`components/`、二级页面、`api/` 尚未落盘；`pages.json`/`manifest.json` 仍为默认模板，需按 `10-frontend-mp` Tasks 7/8/10/11/12/13 补齐。
- [2026-03-01] `medical-mp` 并行代码已落盘：`src/api/doctor.js` 导出 `getDoctorList/getDoctorById/getDepartmentList/getAvailableSlots`，`src/api/appointment.js` 导出 `getMyAppointments/getAppointmentById/cancelAppointment`（取消接口路径为 `/appointment/appointment/{id}/cancel` + `PUT`）。
- [2026-03-01] `src/api/request.js` 使用 `VITE_API_BASE`（默认 `http://localhost:9090/api`）作为统一前缀并直接 `resolve(res.data)`，页面侧需兼容返回体既可能是业务包装对象也可能是裸数据数组。
- [2026-03-01] 小程序页面请求应统一走 `@/api/request`，传入业务路径（如 `/doctor/doctor/list`、`/appointment/appointment/{id}/cancel`），避免页面侧再拼接 `/api` 前缀导致双重前缀风险。
- [2026-03-01] 小程序对话接口中 `createSession` 约定为 `createSession('TRIAGE')`（字符串入参），`createSSERequest` 的 `onMessage` 回调当前返回字符串内容（可能是纯文本 token，也可能是 JSON 字符串），对话页需自行 `JSON.parse` 并分流消息类型。
- [2026-03-01] `medical-mp/live2d-h5` 的 Live2D 静态资源（`models/` + `live2dcubismcore.min.js`）已迁移到 Vite `public/` 目录，运行时使用绝对路径 `/models/...`、`/lib/...`，以确保 build 后原样复制到 `dist/`。
- [2026-03-01] **ai-service Docker 启动失败**：`java.lang.NoSuchFieldError: Companion` at `okhttp3.internal.Util.<clinit>`。根因：Milvus SDK 传递依赖 `okhttp-4.12.0`（需要 Kotlin stdlib），Spring Boot `RestClientAutoConfiguration` 检测到 OkHttp 并尝试创建 `OkHttp3ClientHttpRequestFactory`，但 Kotlin runtime 不在 classpath。修复方案：在 `medical-ai-service/pom.xml` 中排除 Milvus SDK 的 OkHttp 传递依赖，让 Spring 回退到 JDK HttpClient。
- [2026-03-01] Docker Compose 全栈启动结果：13 容器中 12 个正常（infra: MySQL/Redis/Nacos/Milvus+etcd+minio; services: user/doctor/appointment/knowledge/gateway; frontend: admin-web/live2d-h5），仅 ai-service 因 OkHttp/Kotlin 冲突 Exited(1)。
- [2026-03-01] ai-service OkHttp 修复后重建镜像，14/14 容器全部 UP，ai-service 注册 Nacos 成功 (port 8083)。
- [2026-03-01] `docker/mysql/init.sql` 原来只创建 5 个数据库但不建表，需要手动运行各服务 DDL。Codex 已将 5 个 `V1__init_*_tables.sql` 合并到 `init.sql`，后续 fresh deploy 自动建表+初始数据。
- [2026-03-01] DDL 中 admin 用户的 BCrypt 哈希与密码 `admin123` 不匹配（Python bcrypt 验证失败）。Codex 已重新生成正确哈希 `$2b$10$.Lzfrzpy7U.7xK6GyYkZqOGqyubd/oBF/70BGQsE7ndEL4VMaqVWy` 并更新 DDL 和 init.sql。
- [2026-03-01] 5 个微服务的 `application.yml` 全部缺少 `spring.data.redis` 配置，导致 Sa-Token 在容器内连接 `localhost:6379` 失败。Codex 已为 5 个服务添加 `spring.data.redis.host/port/password` 环境变量化配置。
- [2026-03-01] Gateway 的 `sa-token` 配置为 `token-name: Authorization`, `token-prefix: Bearer`, `is-read-cookie: false`, `is-read-header: true`；而 5 个微服务使用 Sa-Token 默认配置（`token-name: satoken`）。**这导致 Gateway 和微服务的 token 读取方式不一致**。

## 11-docker-deploy 联调阻塞分析 (2026-03-01)

### 当前验证结果
| 测试项 | 状态 | 详情 |
|--------|------|------|
| Nacos 注册 | PASS | 6 个服务全部注册 |
| 管理端页面 (HTTP 80) | PASS | 200 OK |
| Live2D H5 (HTTP 8090) | PASS | 200 OK |
| Admin 登录 (POST /api/user/auth/login) | PASS | 返回 token + 用户信息 |
| 用户列表 (GET /api/user/user/list) | **BLOCKED** | 401 — Gateway Sa-Token 校验失败 |
| 科室列表 (GET /api/doctor/department/list) | **BLOCKED** | 401 — 同上 |

### 当前阻塞点：Gateway Sa-Token token 校验
**现象：**
1. `satoken: <token>` → "未能读取到有效 token"（Gateway 不识别该 header）
2. `Authorization: <token>` → "未按照指定前缀提交 token，prefix=Bearer"
3. `Authorization: Bearer <token>` → "token 无效：<token>"（Gateway 提取到 token 但校验失败）

**根因分析：**
- Redis 中确认 `satoken:login:token:<tokenValue>` key 存在，value 为 userId=1
- Gateway 使用 `sa-token-reactor-spring-boot3-starter` + `sa-token-redis-jackson` + `spring-boot-starter-data-redis`
- Gateway Redis 配置存在 (`host: ${REDIS_HOST:localhost}`)
- 启动日志显示 Redis repository scanning 完成，但未见 Sa-Token Redis DAO 初始化日志
- **高概率原因**：Gateway 的 `sa-token-redis-jackson` 未正确接管 `SaTokenDao`，Sa-Token 回退到内存存储，因此找不到 user-service 写入 Redis 的 token

### 根因确认 (2026-03-01 Session 3)

**根本原因：Sa-Token 的 `token-name` 既是 HTTP Header 名，也是 Redis Key 前缀。**

| 组件 | token-name | Redis Key 前缀 | Login 写入 | CheckLogin 查询 |
|------|-----------|----------------|-----------|----------------|
| user-service | `satoken` (默认) | `satoken:` | `satoken:login:token:xxx` → userId | — |
| Gateway | `Authorization` | `Authorization:` | — | `Authorization:login:token:xxx` → NOT FOUND |

**修复方案：统一所有 5 个微服务的 `sa-token` 配置与 Gateway 一致。**

附加修复：Gateway pom.xml 添加 `spring-boot-starter-data-redis-reactive`（WebFlux 环境最佳实践，确保 LettuceConnectionFactory 完整初始化）。

- [2026-02-28] medical-service/medical-ai-service currently has domain entities/VOs/mappers and agent implementations (including SUMMARY in AgentFactory), plus Spring AI/WebFlux dependencies already declared in pom.xml; service-layer classes for chat/tts/summary were not present before Task 7-9.
- [2026-02-28] medical-service/medical-ai-service currently has no controller package or *Controller.java; Task 10-12 should introduce initial REST controller layer (/chat, /summary, /encyclopedia).
- [2026-02-28] `medical-ai-service` entity classes (`ChatSession`, `ChatMessage`, `ConversationSummary`) must declare explicit primary key field `id` (e.g., `@TableId(type = IdType.AUTO)`), because `BaseEntity` does not contain an id property; otherwise service/VO mapping calls like `getId()` fail at compile time.
- [2026-02-28] Spring AI 1.0.0-M5 的 `OpenAiChatOptions.Builder` 中 `withModel()`, `withTemperature()`, `withMaxTokens()`, `withFunctions()` 已标记 deprecated，但仍可编译通过。
- [2026-02-28] ai-service 同时引入 spring-boot-starter-web 和 spring-boot-starter-webflux，web 用于 REST Controller，webflux 仅用于 Reactor Flux (SSE 流式响应)。
- [2026-02-28] 06-ai-service 全量编译通过（18 模块 BUILD SUCCESS），37 个 Java 源文件。
- [2026-02-28] Running `mvn ... -rf :medical-ai-service` from root only resumes downstream modules (`medical-ai-service` and later), so internal upstream artifacts (e.g., `medical-common-*`, `medical-*-api`) must already be installed in local Maven repo; otherwise dependency resolution fails before compilation.
- [2026-02-28] `medical-api/medical-appointment-api/pom.xml` already includes `org.projectlombok:lombok` with `provided` scope; no additional dependency change is required there for Task 1-3.
- [2026-02-28] Existing `RemoteAppointmentService` currently uses `@RequestParam` parameters (not `@RequestBody CreateAppointmentDTO`); per current task constraints it should remain unchanged during 07-appointment-service Task 1-3.
- [2026-02-28] `medical-appointment-service` imports `RemoteDoctorService`/`RemoteScheduleService` from `medical-doctor-api`, so its `pom.xml` must include `com.medical:medical-doctor-api` in addition to `medical-appointment-api`.
- [2026-02-28] There is a contract mismatch between `medical-api` and `appointment-service`: `RemoteAppointmentService#createAppointment` currently sends only `patientId/doctorId/slotId` via `@RequestParam`, while `AppointmentServiceImpl#createAppointment` validates `departmentId` as required. Controller-level adaptation is needed to avoid breaking existing Feign callers.
- [2026-02-28] `medical-gateway` currently has only `GatewayApplication` (no auth/log/exception classes yet); existing `application.yml` already has basic service routes and Redis but lacks dedicated SSE route timeout metadata and gateway-side global exception handler for WebFlux.
- [2026-02-28] For Spring Cloud Gateway (WebFlux), global exception handling should use `ErrorWebExceptionHandler` (registered as high-priority bean, e.g. `@Order(-1)`) instead of `@ControllerAdvice`; SSE path `/api/ai/chat/send` must be declared before generic `/api/ai/**` route and configured with extended response timeout (120s).

## 09-frontend-admin 当前状态 (Session Recovery 2026-02-28)

- [2026-02-28] Tasks 1-7 已提交 (commit 9004ef7): Vue3 项目骨架、Axios 封装、Pinia 状态、Router 权限守卫、Layout 组件、Login 页面
- [2026-02-28] 文件命名约定与计划不同但可接受: 实际用 `*Management.vue`(admin)、短名称(doctor) vs 计划的 `*Manage.vue`/`My*` 前缀
- [2026-02-28] 后端 API 端点对照: /api/user/**(8081), /api/doctor/**(8082), /api/ai/**(8083), /api/appointment/**(8084), /api/knowledge/**(8085)

### 完整审计结果 (Session 3, 2026-02-28)

**API 模块 (8 files) — 全部存在:**
| 文件 | 行数 | 状态 | 导出函数 |
|------|------|------|----------|
| request.js | 51 | COMPLETE | axios instance, token注入, 401跳转 |
| auth.js | 57 | COMPLETE | login, register, wxLogin, logout, getUserInfo |
| user.js | 59 | COMPLETE | getUserList, getUserById, updateUser, toggleUserStatus, assignRole |
| doctor.js | 97 | COMPLETE | getDoctorList, getDoctorById, createDoctor, updateDoctor, getScheduleTemplates, createScheduleTemplate, generateSlots, getAvailableSlots |
| department.js | 58 | PARTIAL | getDepartmentList, getDepartmentById, createDepartment, updateDepartment, toggleDepartmentStatus — **缺少 deleteDepartment** |
| appointment.js | 71 | PARTIAL | getAppointmentList, getAppointmentById, createAppointment, cancelAppointment, getAppointmentStats, getMyAppointments — **缺少 getStatistics, getDoctorTodayAppointments** |
| knowledge.js | 127 | COMPLETE | 10个函数覆盖KB CRUD/文档上传/分块/搜索 |
| chat.js | 111 | COMPLETE | 9个函数, SSE流式via raw fetch() |

**公共组件 (2 files) — 全部完成:**
| 文件 | 行数 | 状态 |
|------|------|------|
| ChatPanel.vue | 417 | COMPLETE — 会话侧栏+消息+SSE流式+markdown渲染 |
| RichEditor.vue | 112 | COMPLETE — contenteditable+工具栏 |

**Admin Views (8 files) — 全部完成但有import不匹配:**
| 文件 | 行数 | 状态 | 问题 |
|------|------|------|------|
| UserManagement.vue | 200 | COMPLETE | **BUG**: imports `listUsers`/`updateUserStatus` 但 user.js 导出 `getUserList`/`toggleUserStatus`; assignRole 传array但API期望string |
| DoctorManagement.vue | 332 | COMPLETE | OK |
| DepartmentManagement.vue | 218 | COMPLETE | **BUG**: imports `listDepartments`/`deleteDepartment` 但 department.js 导出 `getDepartmentList`/无deleteDepartment |
| AppointmentManagement.vue | 229 | COMPLETE | OK |
| KnowledgeBase.vue | 234 | COMPLETE | OK |
| DocumentManagement.vue | 323 | COMPLETE | OK |
| ConversationManagement.vue | 149 | COMPLETE | OK |
| SystemConfig.vue | 153 | COMPLETE | 仅localStorage无后端持久化(可接受) |

**Dashboard:**
| 文件 | 行数 | 状态 | 问题 |
|------|------|------|------|
| dashboard/index.vue | 243 | COMPLETE | **BUG**: imports `getStatistics`/`getDoctorTodayAppointments` 不存在于 appointment.js |

**Doctor Views (5 files) — 全部为空壳:**
| 文件 | 行数 | 状态 |
|------|------|------|
| Profile.vue | 15 | STUB — 仅 `<h1>` 占位 |
| Schedule.vue | 15 | STUB — 仅 `<h1>` 占位 |
| Appointments.vue | 15 | STUB — 仅 `<h1>` 占位 |
| PatientSummary.vue | 15 | STUB — 仅 `<h1>` 占位 |
| Assistant.vue | 15 | STUB — 仅 `<h1>` 占位 |

### 关键修复清单 (必须在build前完成)
1. **UserManagement.vue** — 修正 import 为 `getUserList`/`toggleUserStatus`; assignRole 改为循环发送单个角色
2. **DepartmentManagement.vue** — 修正 import 为 `getDepartmentList`; 在 department.js 添加 `deleteDepartment`
3. **dashboard/index.vue** — 在 appointment.js 添加 `getStatistics`/`getDoctorTodayAppointments`; 修正 import
4. **5 个 Doctor Views** — 需要完整实现

### 审计复核 (2026-02-28, Batch 1 import mismatch)
- [2026-02-28] 复核 `medical-admin` Batch 1 的 5 个修复点后确认: 代码已实现并与 API 导出一致，无需额外改动。
- [2026-02-28] `department.js` 已存在 `deleteDepartment`; `appointment.js` 已存在 `getStatistics`/`getDoctorTodayAppointments`。
- [2026-02-28] `UserManagement.vue` 已使用 `getUserList`/`toggleUserStatus`，且 `assignRole` 已按单角色循环调用；`DepartmentManagement.vue` 与 `dashboard/index.vue` 的 import 与调用均匹配。
- [2026-02-28] 全局检索确认不存在旧符号 `listUsers`、`updateUserStatus`、`listDepartments` 的遗留引用。
- [2026-02-28] `medical-admin/src/api/doctor.js` contract details: profile update uses `updateDoctor(id, data)` with doctor id in path; schedule template APIs are doctor-scoped (`/schedule/template/{doctorId}`), while `generateSlots` takes only query params `{startDate,endDate}` and `getAvailableSlots` takes query params `{doctorId,date}`.
- [2026-02-28] `useUserStore()` current shape in admin frontend: `userInfo` defaults to `{}` and route guard checks roles via computed `roles`; doctor pages should tolerate missing `userInfo.doctorId` and may need fallback resolution by querying doctor list with current username.
- [2026-02-28] Task 16 route/API integration note: doctor summary page currently registered as `doctor/patient-summary` (no route param), but appointments page requirement navigates to `/doctor/patient-summary/:id`; router should use `doctor/patient-summary/:id?` to support param-based navigation while preserving backward compatibility.
- [2026-02-28] `chat.js#getSummaryByAppointmentId(appointmentId)` provides summary by appointment; message history API is session-scoped (`getMessageList(sessionId)`), so PatientSummary page should resolve `sessionId` from summary payload when present and gracefully handle missing sessions.
- [2026-02-28] `ChatPanel.vue` already encapsulates encyclopedia chat workflow (session CRUD, message history, SSE streaming via `encyclopediaChat`, markdown rendering) and exposes `sessionType` prop; doctor assistant page should be a lightweight container passing `ENCYCLOPEDIA`.
- [2026-03-01] `10-frontend-mp` Task 9 requires a standalone `medical-mp/live2d-h5` Vite project with `pixi.js@^7.3.0` + `pixi-live2d-display@^0.4.0`, transparent full-screen canvas, `window.postMessage` bridge commands (`START_LIPSYNC`/`STOP_LIPSYNC`/`PLAY_MOTION`/`SET_EXPRESSION`), and `vite.config.js` `base: './'` for mini-program web-view relative path loading.

## 10-frontend-mp 完成状态 (2026-03-01)

### 审计结果
- UniApp Vue3+TS 项目 (degit dcloudio/uni-preset-vue#vite-ts), node_modules 已安装, pinia 已注册
- **24 源文件** 创建于 medical-mp/src/
- **5 Live2D 文件** 创建于 medical-mp/live2d-h5/src/

### 文件清单
| 目录 | 文件 | 行数 | 作者 |
|------|------|------|------|
| api/ | request.js | 51 | Gemini |
| api/ | auth.js | 47 | Gemini |
| api/ | chat.js | 23 | Gemini |
| api/ | doctor.js | 31 | Gemini |
| api/ | appointment.js | 31 | Gemini |
| utils/ | sse.js | 57 | Gemini+Codex(fix) |
| utils/ | index.js | 31 | Gemini |
| stores/ | user.js | 35 | Gemini |
| components/ | ChatMessage.vue | 85 | Gemini+Codex(fix) |
| components/ | DoctorCard.vue | 113 | Codex |
| components/ | SlotPicker.vue | 186 | Codex |
| components/ | AppointmentCard.vue | 100 | Codex |
| components/ | TtsPlayer.vue | 123 | Codex |
| pages/index/ | index.vue | 203 | Gemini+Codex(fix) |
| pages/chat/ | chat.vue | 454 | Codex(降级接管) |
| pages/doctors/ | list.vue | 215 | Codex+fix |
| pages/doctors/ | detail.vue | 169 | Codex+fix |
| pages/appointment/ | list.vue | 151 | Codex+fix |
| pages/appointment/ | detail.vue | 150 | Codex+fix |
| pages/mine/ | index.vue | 107 | Codex |
| live2d-h5/src/ | main.js | 53 | Codex |
| live2d-h5/src/ | live2d-manager.js | 114 | Codex |
| live2d-h5/src/ | tts-lip-sync.js | 48 | Codex |

### 编译结果
- `npm run build:mp-weixin`: **BUILD SUCCESS** → dist/build/mp-weixin
- `live2d-h5 npm run build`: **BUILD SUCCESS** → dist/ (805KB JS)

### 修复记录
1. sse.js 裸换行符 → Codex 修复为 `\n\n`
2. main.ts 缺少 Pinia → Codex 添加 createPinia()
3. ChatMessage.vue 错误 import defineProps → Codex 移除
4. index.vue 链接不存在页面 → Codex 修复路径
5. 4个页面内联 requestApi → Codex 改用共享 request
6. appointment/detail 取消预约 API 路径 → Codex 修复
7. chat.vue 仅占位 stub → Codex 降级实现完整 454 行

## 11-docker-deploy Session 3 修复记录 (2026-03-01)

### 修复 1: Sa-Token 401 (根因确认 + 修复)
- **根因**: Sa-Token `token-name` 同时作为 HTTP Header 名和 Redis Key 前缀。Gateway 用 `Authorization` → key 前缀 `Authorization:`；服务默认 `satoken` → key 前缀 `satoken:`
- **修复**: Codex 为 5 个微服务 application.yml 添加统一 sa-token 配置块 (token-name: Authorization, token-prefix: Bearer)
- **附加**: Gateway pom.xml 中 `spring-boot-starter-data-redis` 替换为 `spring-boot-starter-data-redis-reactive` (WebFlux 最佳实践)
- **验证**: 登录后 GET /api/user/user/list 返回 200（之前 401）

### 修复 2: -parameters 编译标志
- **根因**: Spring Boot 3.x / Spring Framework 6.x 不再通过反射获取 `@RequestParam` 参数名，需 `-parameters` 编译选项
- **修复**: Codex 在 medical-ai/pom.xml `<pluginManagement>` 中添加 maven-compiler-plugin `<parameters>true</parameters>`

### 修复 3: DDL 缺少 BaseEntity 审计列
- **根因**: `BaseEntity` 定义 `createBy`/`updateBy` 字段，但 13 张表 DDL 缺少 `create_by`/`update_by` 列，导致 `Unknown column 'create_by' in 'field list'`
- **修复**: Codex 更新 docker/mysql/init.sql 补齐列；OpenCode 执行 ALTER TABLE 补齐现有数据库 13 张表

### 修复 4: 非 user 服务缺 StpInterfaceImpl
- **根因**: `StpInterfaceImpl`（角色查询）仅在 user-service 中实现，其他服务 `@SaCheckRole` 检查返回空角色 → 403
- **修复**: Codex 在 medical-common-security 新建通用 `StpInterfaceImpl`（基于 RemoteUserService Feign 调用 `/user/inner/{userId}`，`@ConditionalOnMissingBean` 确保 user-service 本地实现优先）
- **附加**: OpenCode 为 doctor-service/knowledge-service 添加 `@EnableFeignClients(basePackages="com.medical.api")` + loadbalancer 依赖

### 当前状态 — 联调全通 (2026-03-01 Session 4)
- Maven BUILD SUCCESS (18/18)
- Docker 14/14 containers UP, 6/6 services registered
- **12 项联调验证全部通过**

### 追加修复 (Session 4)
- [修复5] doctor/knowledge 服务: `allow-bean-definition-overriding: true` 避免自身 FeignClientSpecification 冲突
- [修复6] docker-compose.yml: Codex 补齐 5 服务 `REDIS_HOST`/`REDIS_PORT` 环境变量 (knowledge-service 缺失导致 Redis 连接 localhost 失败)
- [修复7] GlobalExceptionHandler: Codex 在 common-core 新建 `AutoConfiguration.imports` 注册 handler (原因: 跨模块 @RestControllerAdvice 未被 Spring Boot 自动发现)
- [发现] `@EnableFeignClients(basePackages="com.medical.api")` 在 doctor/knowledge 服务中会扫描到指向自身的 FeignClient，需配合 `allow-bean-definition-overriding=true`
