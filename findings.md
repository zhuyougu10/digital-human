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

## Phase 6: API 联调全面审查 (2026-03-02)

### 审查方法
5 批并行审查: 对比每个后端 Controller 的每个端点与前端 API 模块/视图的调用，检查 URL 路径、HTTP 方法、请求参数、响应结构。

### Gateway 路由规则
所有路由 `StripPrefix=2`: `/api/user/**` → 后端 `/**`, `/api/doctor/**` → 后端 `/**`, etc.

### 发现问题总汇 (按严重性排序)

#### CRITICAL (运行时必崩)

| # | 服务 | 问题 | 文件 | 修复方案 |
|---|------|------|------|----------|
| C1 | user | admin `request.js` 读 `res.message` 但后端 `R.java` 字段为 `msg` | `medical-admin/src/api/request.js:31,39` | 改为 `res.msg` |
| C2 | user | MP `getUserInfo` 调 `/user/auth/info`(不存在), 应为 `/user/user/info` | `medical-mp/src/api/auth.js:35-38` | 改 URL |
| C3 | user | MP `request.js` 未解包 `R<T>`, 页面直接访问 `result.token` 为 undefined | `medical-mp/src/api/request.js` + `auth.js:15` | request 拦截器解包 `res.data.data` when `code===200` |
| C4 | knowledge | 前端读 `res.data.list` 但 `PageResult` 字段为 `records` (3处) | `KnowledgeBase.vue:100`, `DocumentManagement.vue:173,224` | 改为 `res.data.records` |
| C5 | knowledge | DocumentManagement 表格 column prop 与 VO 字段名不匹配 (name→fileName等, 8处) | `DocumentManagement.vue:14-24,99,209` | 逐一修正 prop 名 |
| C6 | ai | MP `getSessionList` URL `/session/list` 不存在, 应为 `/sessions` → 404 | `medical-mp/src/api/chat.js:13` | 改 URL |
| C7 | ai | MP `createSession` 发 `{type}` 但 DTO 字段为 `sessionType` → null | `medical-mp/src/api/chat.js:7` | 改为 `{sessionType: type}` |
| C8 | ai | MP SSE 发 `content` 但 DTO 字段为 `message` → null | `medical-mp/src/pages/chat/chat.vue:263` | 改为 `message: text` |
| C9 | appointment | `getDoctorTodayAppointments` 调 `/doctor/today`(不存在) → 404 | `medical-admin/src/api/appointment.js:74` | 改 URL 为 `/doctor` + `date=today` |
| C10 | appointment | Doctor Appointments.vue 用 `getMyAppointments`(患者端点), 应用 doctor 端点 | `medical-admin/src/views/doctor/Appointments.vue:69` | 新增 getDoctorAppointments API, 调 `/appointment/doctor` |
| C11 | appointment | admin AppointmentManagement 读 `res.data.list` 但 PageResult 为 `records` | `AppointmentManagement.vue:138` | 改为 `res.data.records` |

#### MEDIUM (功能异常/数据缺失)

| # | 服务 | 问题 | 文件 | 修复方案 |
|---|------|------|------|----------|
| M1 | ai | Admin ChatPanel SSE 未解析 JSON, 直接拼接原始 data → 显示乱码 | `ChatPanel.vue:206-214` | 解析 JSON 提取 content |
| M2 | doctor | Department/Doctor 搜索栏发 keyword 但后端不接受 → 搜索无效 | `DepartmentController.java` + `DoctorController.java` | 后端添加 keyword 参数 |
| M3 | doctor | Profile.vue 用 `updateDoctor(id)` 需 ADMIN 角色 → 医生 403 | `Profile.vue:216` | 改用 `PUT /doctor/my-profile` |
| M4 | knowledge | ChunkManualDTO 缺 `title` 字段, 前端 title 输入被丢弃 | `ChunkManualDTO.java` + `DocumentManagement.vue:78` | 后端添加 title 字段 |
| M5 | knowledge | 下拉菜单 slot `#footer` 应为 `#dropdown` | `KnowledgeBase.vue:18` | 改 slot 名 |
| M6 | appointment | AppointmentQueryDTO.status 为 Integer 但前端发 String | `AppointmentQueryDTO.java` + `AppointmentManagement.vue` | 后端改 String 或前端改数字 |
| M7 | appointment | AppointmentQueryDTO 缺 startDate/endDate 字段 | `AppointmentQueryDTO.java` | 后端添加字段 |
| M8 | appointment | AppointmentListVO 缺 patientName/patientPhone | `AppointmentListVO.java` | 后端添加字段并查询填充 |
| M9 | knowledge | parseStatus 为 Integer 但前端按 String 匹配 | `DocumentManagement.vue:272-279` | 改 map key 为整数 |

#### LOW (功能可用但不完善)

| # | 问题 |
|---|------|
| L1 | Profile.vue 用 getDoctorById + 列表查找, 应用 GET /doctor/my-profile |
| L2 | user removeRole 后端端点无前端调用 |
| L3 | admin 调用 /user/inner/{id} 内部端点 |
| L4 | MP logout 未调后端注销 |
| L5 | Schedule 无删除模板 UI |
| L6 | /doctor/search、/schedule/slots/department 无前端调用 |
| L7 | /chat/session/{id}/end 无前端调用 |
| L8 | /encyclopedia/sessions, /encyclopedia/session/{id}/messages 无前端调用 |
| L9 | MP appointment 页面未用 API 模块, 直接 import request |
| L10 | Backend /appointment/doctor 返回 List 但前端用分页 |

- [2026-03-02] `medical-knowledge-service` 与 `medical-appointment-service` 的 DTO/VO 路径使用 `domain/dto`、`domain/vo`（不是 `dto`、`vo` 顶层目录），后续修复需按实际包路径修改以避免误改或找不到文件。
- [2026-03-02] 在当前 WSL 环境执行 `uni build -p mp-weixin` 可能因 `os.networkInterfaces()` 抛出 `uv_interface_addresses` 系统错误；设置环境变量 `CI=1` 可跳过 `@dcloudio/uni-cli-shared` 的更新检查并正常完成构建。
- [2026-03-02] **测试基础设施现状**: 项目当前 0 个测试类、0 个 src/test/ 目录。5 个 service module 已有 `spring-boot-starter-test`（JUnit5+Mockito+MockMvc），但 gateway/common/api 模块均无。
- [2026-03-02] **共 55 个 REST 端点** (46 public + 9 inner/Feign)，分布于 10 个 Controller 类。
- [2026-03-02] **需要 mock 的外部依赖 (9 项)**: MySQL, Redis, Nacos, Milvus, DeepSeek API, DashScope Embedding, Aliyun TTS, WeChat API, OpenFeign 跨服务调用。
- [2026-03-02] **测试策略**: 采用 `@WebMvcTest` + `@MockBean` + `@AutoConfigureMockMvc(addFilters=false)` 进行 Controller 层隔离测试，不依赖任何外部服务。SSE 端点需特殊处理（WebTestClient 或简化验证）。
- [2026-03-02] **发现缺陷**: `RemoteAppointmentService` 声明了 `cancelAppointment(Long)` 映射到 `POST /inner/cancel`，但 AppointmentController 无对应端点，Feign 调用会 404。
- [2026-03-02] **发现缺陷**: `RemoteAppointmentService.createAppointment` 仅声明 3 个参数 (patientId/doctorId/slotId)，但 Controller 的 innerCreate 接受 5 个参数（多了 optional departmentId/sessionId），Feign 侧无法传递。
- [2026-03-02] 接口测试计划: `docs/plans/13-api-testing.md`，共 10 个测试类、109 个测试用例，覆盖全部 55 个端点。
- [2026-03-02] Controller tests must align with real binding behavior: many endpoints (e.g., user/doctor/appointment/knowledge create/update APIs) do **not** use `@Valid`, so `400` should be asserted mainly for binding/type errors (missing required `@RequestParam`, bad date format, malformed JSON), while business errors should be simulated via `BusinessException` from mocked services.
- [2026-03-02] Auth context is obtained through static methods: most controllers call `SecurityUtil.getUserId()`, while `AppointmentController` calls `StpUtil.getLoginIdAsLong()` directly. Tests need static mocking for the matching utility to avoid auth-related runtime failures under `@WebMvcTest`.
- [2026-03-02] GlobalExceptionHandler returns `R.fail(...)` without `@ResponseStatus`, so most business/validation exceptions in controller tests still return HTTP 200 with non-200 `$.code`; assertions should prioritize `$.code/$.msg` contract over HTTP status for these paths.
- [2026-03-02] AI controller DTOs (`CreateSessionDTO`, `ChatRequestDTO`) currently have no Bean Validation annotations; "invalid" test cases should be modeled via mocked `BusinessException` paths rather than relying on automatic 400 validation.
- [2026-03-02] `AppointmentController` does not use `SecurityUtil`; it directly calls static `StpUtil.getLoginIdAsLong()`. Controller tests for appointment module require static mocking `StpUtil` even with `addFilters = false`.
- [2026-03-02] `KnowledgeBaseController` bypasses service for some endpoints (`listDocuments`, `deleteChunk`) and directly calls mappers + `VectorStoreService`; `@WebMvcTest` must `@MockBean` `KnowledgeDocumentMapper/KnowledgeChunkMapper/KnowledgeBaseMapper/VectorStoreService` to load context and cover those branches.
- [2026-03-05] Real gateway base URL for integration tests in this environment is `http://localhost:9090/api` (not `8080` in earlier docs), so Python E2E tests must target port 9090 for all `/api/*` routes.
- [2026-03-05] `DoctorController#create` currently returns `R<Void>` and `DoctorProfileServiceImpl#create` sets `userId=null`, so tests that need created doctor IDs must re-query `/doctor/doctor/list`, and `my-profile` behavior depends on pre-seeded doctor-profile/user bindings.
- [2026-03-05] `AppointmentServiceImpl#createAppointment` enforces non-null `patientId/doctorId/departmentId/slotId`; integration tests must always include `departmentId` when calling `POST /appointment/appointment`.
- [2026-03-05] **pytest Round 1**: 40 PASSED / 20 FAILED. 4 根因已定位，Codex 正在修复中。
- [2026-03-05] **DDL 缺失列**（information_schema 确认）: chat_message/conversation_summary 缺 update_time+deleted; knowledge_chunk 缺 update_time+deleted; doctor_department 缺 create_time+update_time+deleted; sys_user_role 缺 create_time+update_time+deleted。
- [2026-03-05] **RC1 修复**: DoctorProfileDTO 新增 userId 字段, create() 改为 `dto.getUserId() != null ? dto.getUserId() : SecurityUtil.getUserId()`，同时 test_04_doctor.py 传入 state.doctor_user_id。
- [2026-03-05] **RC3 修复**: KnowledgeBaseServiceImpl uploadPath 默认值改为 /data/uploads，knowledge-service Dockerfile 添加 `RUN mkdir -p /data/uploads`。
- [2026-03-05] **RC4 说明**: embedding 404 是 DASHSCOPE_API_KEY 占位符问题，test_add_manual_chunk 和 test_search_kb 属于预期失败，不算后端 bug。

## Phase 10: Admin UI 重构美化 — Design Specification (2026-03-03)

### 审计现状
- **UI 框架**: Element Plus 2.13.2 (全局注册) + @element-plus/icons-vue
- **图表**: ECharts 6.0.0
- **无自定义主题**: 所有颜色为硬编码十六进制值，无 CSS 变量
- **无全局工具类**: `.m-t-20`/`.m-r-5` 在多个文件中重复定义
- **布局**: 暗色侧边栏(#304156) + 白色导航 + 灰色内容区(#f0f2f5)
- **20 个 .vue 文件**，总计约 4015 行

### 设计方向：「医疗级 SaaS」— 专业、可信、现代

**Tone**: Refined clinical meets modern SaaS — 不是过度装饰的华丽风格，而是克制精致的专业风格。医疗系统需要传达信任感和专业度。

### 色彩系统 (CSS Custom Properties)

```css
/* 主色 — 医疗蓝/青 */
--primary: #1677FF;          /* Element Plus 覆盖 */
--primary-light: #4096FF;
--primary-lighter: #BAE0FF;
--primary-bg: #E6F4FF;

/* 辅色 */
--success: #52C41A;
--warning: #FAAD14;
--danger: #FF4D4F;
--info: #8C8C8C;

/* 中性色 */
--text-primary: #1F2937;
--text-secondary: #6B7280;
--text-placeholder: #9CA3AF;
--border: #E5E7EB;
--border-light: #F3F4F6;
--bg-page: #F7F8FA;
--bg-card: #FFFFFF;

/* 侧边栏 — 深蓝渐变 */
--sidebar-bg: linear-gradient(180deg, #0C2340 0%, #1B3A5C 100%);
--sidebar-active: rgba(22, 119, 255, 0.15);
--sidebar-text: rgba(255, 255, 255, 0.65);
--sidebar-text-active: #FFFFFF;

/* 阴影层级 */
--shadow-sm: 0 1px 2px 0 rgba(0, 0, 0, 0.03), 0 1px 6px -1px rgba(0, 0, 0, 0.02);
--shadow-md: 0 2px 8px 0 rgba(0, 0, 0, 0.06), 0 4px 16px -2px rgba(0, 0, 0, 0.04);
--shadow-lg: 0 4px 16px 0 rgba(0, 0, 0, 0.08), 0 8px 32px -4px rgba(0, 0, 0, 0.06);

/* 圆角 */
--radius-sm: 6px;
--radius-md: 8px;
--radius-lg: 12px;
--radius-xl: 16px;
```

### 字体

```css
font-family: 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
```

(使用系统中文字体栈，不引入外部字体文件，保证加载性能)

### Element Plus 主题覆盖

通过 CSS 变量覆盖 Element Plus 默认主题色:
```css
:root {
  --el-color-primary: #1677FF;
  --el-color-success: #52C41A;
  --el-color-warning: #FAAD14;
  --el-color-danger: #FF4D4F;
  --el-border-radius-base: 6px;
  --el-fill-color-light: #F7F8FA;
}
```

### 布局改进
1. **侧边栏**: 深蓝渐变背景 + 透明活跃态 + 白色图标文字 + 品牌 logo 区域
2. **导航栏**: 白色背景 + 更精致的阴影代替边框 + 更好的头像下拉
3. **内容区**: 更大的圆角卡片 + 统一间距(24px) + 更好的过渡动画

### 关键改进点
1. 搜索区域与表格区域合并为单卡片，减少视觉碎片
2. 统计卡片增加微妙的渐变背景和图标容器
3. 表格行增加 hover 效果，操作按钮改为图标按钮组
4. 对话框增加圆角和更好的间距
5. 分页组件与表格的间距优化
6. 全局 NProgress 顶部加载条
7. 空状态使用定制插图或更好的文案

### 文件变更清单

| Task | 文件 | 变更类型 |
|------|------|----------|
| T1 | `src/style.css` | **重写** → 设计系统变量 + 全局样式 + 工具类 + Element Plus 覆盖 |
| T1 | `src/App.vue` | 修改全局样式块 |
| T1 | `src/main.js` | 添加 NProgress 导入 + 路由守卫集成 |
| T1 | `src/components/Layout/AppLayout.vue` | **重写** scoped CSS |
| T1 | `src/components/Layout/Sidebar.vue` | **重写** scoped CSS + 品牌 logo |
| T1 | `src/components/Layout/Navbar.vue` | **重写** scoped CSS |
| T2 | `src/views/login/index.vue` | **重写** 完整页面 |
| T2 | `src/views/dashboard/index.vue` | **重写** scoped CSS + ECharts 主题 |
| T3 | 8 个 admin views | **重写** scoped CSS，保留所有 JS 逻辑 |
| T4 | 5 个 doctor views | **重写** scoped CSS，保留所有 JS 逻辑 |
| T5 | ChatPanel.vue + RichEditor.vue | **重写** scoped CSS |

### 约束 (所有 Task 通用)
- **不改变任何 JS/TS 业务逻辑**: 只修改 `<template>` 结构布局和 `<style>` 样式
- **不新增依赖**: 只用现有 Element Plus + ECharts + @element-plus/icons-vue
- **保持所有 Element Plus 组件引用不变**: el-table, el-form, el-dialog 等
- **保持所有 API 调用和 store 引用不变**
- **中文 UI 文案不变**
- **路由结构不变**
- [2026-03-05] Python integration tests should prefer `http://localhost:9090/api` but auto-fallback to `http://localhost:8080/api` when running against the Docker Compose gateway mapping, otherwise all tests fail at fixture login with connection-refused.
- [2026-03-05] In the current dockerized dataset, `admin/admin123` logs in successfully but returns roles `["DOCTOR"]` instead of `ADMIN`; all role-protected admin endpoints return `code=403` and cannot be validated without environment seed/data fix.
- [2026-03-05] `docker/docker-compose.yml` already passes `DASHSCOPE_API_KEY` into both `ai-service` and `knowledge-service`; embedding failures with code `5003` are caused by placeholder API keys, not missing env propagation.
- [2026-03-05] `DoctorProfileServiceImpl#create` now uses `dto.userId` first and falls back to `SecurityUtil.getUserId()`, which matches `doctor_profile.user_id NOT NULL` constraints for admin-created doctor profiles.
- [2026-03-05] Integration sequence dependency: `test_03_department` deletes the created department, so downstream doctor/schedule tests must not reuse cached `state.department_id` without re-validation, or they fail with `DEPARTMENT_NOT_FOUND` and `参数类型错误` cascades.
- [2026-03-05] `test_09_e2e_flow` on persistent Docker data can hit `appointment code=4003 (重复预约)`; making the test iterate doctor/slot candidates and optionally generate same-day slots avoids false negatives from reused data.
- [2026-03-05] In PowerShell heredoc commands, absolute paths containing Chinese characters can be mangled for Python `Path(...)`; running scripts with `workdir=D:\\project\\数字人` and relative paths (e.g. `medical-ai/docker/mysql/init.sql`) avoids `OSError: [Errno 22] Invalid argument`.
- [2026-03-05] Current MySQL in `medical-mysql` rejects `ALTER TABLE ... ADD COLUMN IF NOT EXISTS ...` syntax (ERROR 1064); for compatibility, use plain `ADD COLUMN` after checking existing columns.
- [2026-03-05] MySQL container init can still mis-handle Chinese seed inserts if connection charset is not forced at connect time; adding `--init-connect='SET NAMES utf8mb4'` to mysql service command in `docker-compose.yml` mitigates this at container initialization.
