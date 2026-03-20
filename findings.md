# Findings & Decisions

> 项目：AI 数字人医疗小助手系统（毕业设计）
> 创建日期：2026-02-27
> 最后更新：2026-03-20

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
- Live2D 在小程序中需通过 `<web-view>` 内嵌 H5 实现。由于小程序原生 `web-view` 组件强制全屏且层级最高，已选定 **方案 A：全量 H5 化**，即聊天气泡、输入框等 UI 全部迁移至 H5 项目，UniApp 仅作为 SSE 逻辑中转站。
- PixiJS 版本兼容性：`pixi-live2d-display@0.4.0` 强依赖 **PixiJS v6**。使用 v7 会导致 `manager.on` 报错及 `isInteractive` 缺失。已强制降级至 `6.5.9` 并通过 polyfill 修复。
- 口型同步 (Lip-Sync)：通过小程序监听到语音播放事件，实时修改 `web-view` URL 参数（如 `speaking=1`）触发 H5 侧 `LipSyncManager` 基于正弦波驱动 `ParamMouthOpenY` 参数。
- 数字人位姿优化：针对跪坐模型，通过 `anchor(0.5, 1.0)` + `y = sh` + 固定 450px 高度缩放，实现了稳定的“半身/全身站立”视觉效果，且不随屏幕尺寸缩放。
- TTS 方案：阿里云智能语音 API 返回音频 URL → UniApp TtsPlayer 播放 → 发送 START_LIPSYNC 指令至 H5。
- Milvus 向量数据库需要 etcd + minio 作为依赖服务
- 每个微服务独立数据库：medical_user / medical_doctor / medical_ai / medical_appointment / medical_knowledge
- 本轮会话衔接检查后确认工作区仍有未提交改动，重点集中在 H5 TTS 播放链路排障：`medical-mp/live2d-h5/src/main.js`、新增 `medical-mp/live2d-h5/src/audio-player.js`、`medical-mp/src/pages/chat/chat.vue`，以及调试依赖 `vconsole`。
- 当前未提交变更同时包含 `medical-ai-service/pom.xml` 中恢复 DashScope `okhttp` 传递依赖的修复，以及 `tts-audio/` 下 9 个本地语音样本文件，说明上轮排障已走到“生成真实音频并在前端侧复测”的阶段。

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
| 方案 A：全量 H5 聊天 UI | 彻底解决小程序 `web-view` 遮挡原生 UI 的层级问题，保证输入框和气泡在最顶层 |
| PixiJS v6 + Ticker 注册 | 解决 Live2D 静态不动的问题，确保插件兼容性并开启自动 Idle 动画 |
| URL 参数驱动 H5 状态 | 绕过小程序 `postMessage` 实时性差的限制，实现毫秒级口型同步 |
| SSE 流式对话 | 相比 WebSocket 更简单，单向推送适合 LLM 流式输出场景 |
| 会话衔接优先以项目根目录规划文件 + `git diff --stat` 为准 | 当前 catchup 脚本无输出，但工作区存在未提交排障上下文，需以规划文件和 Git 实际差异同步状态 |

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
| 直接套用 planning-with-files 文档中的 PowerShell catchup 命令会在当前 Bash shell 报 `syntax error near unexpected token '('` | 当前环境默认通过 Bash 执行命令，需改用 Bash 形式或直接调用仓库内脚本绝对路径 |
| 当前用户目录下不存在 `~/.opencode/skills/planning-with-files/scripts/session-catchup.py` | 改用仓库内 `.opencode/skills/planning-with-files/scripts/session-catchup.py` 可正常执行 |

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
- [2026-03-20] 会话补全脚本本次未报告未同步上下文；项目根目录已存在 `task_plan.md`、`findings.md`、`progress.md`，当前活动阶段仍为 Phase 21（CosyVoice TTS 集成）。
- [2026-03-20] TTS 新故障定位到 `medical-ai-service` 依赖裁剪：`TtsServiceImpl` 使用 DashScope `SpeechSynthesizer` 走 WebSocket，全栈报错 `NoClassDefFoundError: okhttp3/WebSocketListener`，而 `medical-ai-service/pom.xml` 当前对 `spring-ai-openai-spring-boot-starter` 与 `dashscope-sdk-java` 都显式排除了 `com.squareup.okhttp3:okhttp`，导致 DashScope 运行时缺少 WebSocket 客户端类。
- [2026-03-20] `medical-ai-service` 本身不直接依赖 Milvus，修复 TTS 缺类时无需恢复整套历史冲突链；最小修复是仅恢复 `dashscope-sdk-java` 的 OkHttp 传递依赖，同时继续保留 `spring-ai-openai-spring-boot-starter` 上的 OkHttp 排除。`mvn -pl medical-service/medical-ai-service -am -DskipTests compile -f medical-ai/pom.xml` 已在当前环境验证通过。
- [2026-03-20] `mvn -pl medical-service/medical-ai-service -am dependency:tree -Dincludes=com.squareup.okhttp3:okhttp -f medical-ai/pom.xml` 已确认 `medical-ai-service -> dashscope-sdk-java:2.19.2 -> okhttp:4.12.0`，说明 DashScope 所需 WebSocket 运行时已恢复到 AI 服务模块。
- [2026-03-20] 新一轮排障发现：后端已能生成 `ttsUrl`，前端 H5 播放链路位于 `medical-mp/live2d-h5/src/main.js`。当前实现仅在 `Audio.onloadedmetadata` 中调用 `audio.play()`；若浏览器/小程序 WebView 对自动播放有限制，元数据加载成功但 `play()` 被策略拦截时会表现为“有 URL 但无声音”。同时需要继续核对 `/ai/chat/tts/*` 端点返回头、相对路径拼接和音频请求可达性。
- [2026-03-20] 继续排查发现 H5 当前使用浏览器 `new Audio(audioUrl)` 直接拉取 `ttsUrl`；该请求不会自动带 `Authorization` 头。若 Gateway 仍对 `/api/ai/chat/tts/*` 走鉴权，表现将是“后端已生成音频但前端无声”。另一个可疑点是微信 WebView 自动播放策略，但应先验证音频 GET 请求是否被网关 401 拦截。
- [2026-03-20] 代码链路已坐实上述根因：`AuthFilter` 对 `/**` 统一鉴权，白名单不含 `/api/ai/chat/tts/**`；H5 `main.js` 却用 `new Audio(audioUrl)` 直接取资源，无法附带 `Authorization`。最小修复是在 H5 侧先用 `fetch(audioUrl, { Authorization: Bearer token })` 拉音频 blob，再用 `URL.createObjectURL` 交给 `Audio` 播放，而不是放开网关匿名访问。
- [2026-03-20] 为便于微信小程序内嵌 H5 调试，`medical-mp/live2d-h5` 已接入 `vconsole`，在 `src/main.js` 启动时创建单例调试面板，可直接在小程序 WebView 中查看日志与网络请求。
- [2026-03-20] `vconsole` 已收敛为仅开发环境启用：`medical-mp/live2d-h5/src/main.js` 使用 `import.meta.env.DEV` 判定，避免生产包常驻调试面板。
- [2026-03-20] 新日志 `[H5] 播放音频失败: {}` 说明请求鉴权问题已大概率绕过，当前更像 WebView 自动播放策略拦截：`audio.play()` 抛出的 DOMException 在 console 中被序列化成空对象。下一步应在用户点击发送时先执行一次音频解锁（warm-up），并把错误日志展开为 `name/message/stack` 便于继续判断是否为 `NotAllowedError`。
- [2026-03-20] 已在 `medical-mp/live2d-h5/src/audio-player.js` 增加共享 `Audio` 实例与 `unlockAudioPlayback()` 预解锁逻辑，并在 `main.js` 的发送按钮点击链路里先执行 warm-up；同时将播放失败日志展开为 `name/message/stack/audioUrl`，便于区分自动播放限制与资源错误。
- [2026-03-20] 继续排查确认新的主根因是跨域 fetch 选项：`audio-player.js` 给音频 GET 设置了 `credentials: 'include'`，而后端 `/chat/tts/*` 响应头是 `Access-Control-Allow-Origin: *`。浏览器对“携带凭证 + 通配符 Origin”组合会直接拦截，表现为 `TypeError: Failed to fetch`。该请求本身依赖 `Authorization` 头鉴权，不需要 cookies/credentials。
- [2026-03-20] 用户最新反馈表明后端实际使用 8080，不应机械切到 9090。当前更合理的根因是“小程序内嵌 H5 中 `localhost` 指向 WebView 自身环境而不是开发机/网关地址”，因此若 `apiBase` 仍是 `http://localhost:8080/api`，H5 fetch 会直接 `TypeError: Failed to fetch`。修复方向应保留 8080 端口，但把 `localhost/127.0.0.1` 解析为当前 H5 页面可访问的宿主机地址。 
- [2026-03-20] 已按上述结论落地：`medical-mp/live2d-h5/src/main.js` 现在会把 `apiBase` 中的 `localhost/127.0.0.1` 自动替换为 `window.location.hostname`，这样在 H5 页面是通过局域网 IP / 域名打开时，TTS 音频请求会落到同一宿主机的 8080 端口，而不是错误地打到 WebView 自身的 localhost。
- [2026-03-20] 若 `vConsole` 只剩 `音频解锁失败 NotSupportedError: Failed to load because no supported source was found.`，说明 warm-up 使用的 data URL 在当前微信 WebView 不被支持；这条日志本身不等于真实 TTS 播放失败，应降级为静默忽略，避免干扰后续判断真实音频链路。
- [2026-03-20] 在当前小程序架构里，最稳妥的 TTS 播放方式不是 H5 `Audio`，而是 UniApp 原生 `InnerAudioContext`：`chat.vue` 已有隐藏 `TtsPlayer` 与 lip-sync 事件桥，因此可让 H5 只负责 UI 和口型指令，真实音频播放回退到 UniApp 原生层，以绕开 WebView 对 `localhost`/跨域/自动播放的多重限制。
- [2026-03-20] **[TTS 无声 — 系统化排障结论]** 定位到 2 个独立根因组成完整无声链：
  - **BUG 1 (CRITICAL — 后端竞态)**: `ChatServiceImpl.java:137-168` 中 `doOnComplete` 调用阻塞的 `ttsService.synthesize()`（DashScope WebSocket 数秒级），但 `concatWith(Mono.fromCallable)` 不等待 `doOnComplete` 完成就求值 → 前端收到的 `complete.ttsUrl` 永远是 `null`。修复：将 TTS 合成移入 `concatWith` 的 Mono 内部，保证因果顺序。
  - **BUG 2 (HIGH — 前端 audioMode 逻辑矛盾)**: `chat.vue:30` 设置 `audioMode='native'` → H5 `main.js:158` 对 `native` 模式 `continue` 跳过播放 → 但 UniApp 的 `sendMessage()` 从未被调用（H5 直连后端后 SSE 不经过 UniApp），`TtsPlayer` 永远不收到 `ttsUrl` → 两端都不播放。修复：删除 `audioMode=native`，让 H5 用已有的 `audio-player.js`（带 Bearer 鉴权 fetch + blob 播放 + unlock warm-up）直接播放。
- [2026-03-05] In the current dockerized dataset, `admin/admin123` logs in successfully but returns roles `["DOCTOR"]` instead of `ADMIN`; all role-protected admin endpoints return `code=403` and cannot be validated without environment seed/data fix.
- [2026-03-05] `docker/docker-compose.yml` already passes `DASHSCOPE_API_KEY` into both `ai-service` and `knowledge-service`; embedding failures with code `5003` are caused by placeholder API keys, not missing env propagation.
- [2026-03-05] `DoctorProfileServiceImpl#create` now uses `dto.userId` first and falls back to `SecurityUtil.getUserId()`, which matches `doctor_profile.user_id NOT NULL` constraints for admin-created doctor profiles.
- [2026-03-05] Integration sequence dependency: `test_03_department` deletes the created department, so downstream doctor/schedule tests must not reuse cached `state.department_id` without re-validation, or they fail with `DEPARTMENT_NOT_FOUND` and `参数类型错误` cascades.
- [2026-03-05] `test_09_e2e_flow` on persistent Docker data can hit `appointment code=4003 (重复预约)`; making the test iterate doctor/slot candidates and optionally generate same-day slots avoids false negatives from reused data.
- [2026-03-05] In PowerShell heredoc commands, absolute paths containing Chinese characters can be mangled for Python `Path(...)`; running scripts with `workdir=D:\\project\\数字人` and relative paths (e.g. `medical-ai/docker/mysql/init.sql`) avoids `OSError: [Errno 22] Invalid argument`.
- [2026-03-05] Current MySQL in `medical-mysql` rejects `ALTER TABLE ... ADD COLUMN IF NOT EXISTS ...` syntax (ERROR 1064); for compatibility, use plain `ADD COLUMN` after checking existing columns.
- [2026-03-05] MySQL container init can still mis-handle Chinese seed inserts if connection charset is not forced at connect time; adding `--init-connect='SET NAMES utf8mb4'` to mysql service command in `docker-compose.yml` mitigates this at container initialization.
- [2026-03-06] 用户管理角色分配弹窗原逻辑仅调用 `assignRole`，未处理取消勾选的角色；需按角色差集分别调用新增与移除接口，才能正确取消已有角色。
- [2026-03-06] `user-service` 新增用户接口改为返回 `UserVO`（含新用户 `id`）后，前端可在同一事务链路里完成后续绑定动作（如医生画像初始化）。
- [2026-03-06] 用户管理新增用户选择 `DOCTOR` 角色时，前端联动调用医生创建接口并传入 `userId`，可避免医生首次登录 `my-profile` 因无画像返回 `DOCTOR_NOT_FOUND`。
- [2026-03-06] 医生管理“新增医生”应显式要求绑定用户（`userId` 必填），否则画像可能绑定到错误主体；新增“关联用户”下拉后可直接绑定账号。
- [2026-03-06] 医生管理“关联用户”下拉需要仅展示 `DOCTOR` 角色用户（接口参数筛选 + 前端兜底过滤），可避免误绑定管理员/普通用户。
- [2026-03-06] 当前医生管理表单字段仍存在 `description` 与后端 `introduction` 命名不一致的历史问题，后续应统一字段名以减少编辑回填偏差风险。
- [2026-03-06] 管理员从“医生管理”点击“排班”若跳转 `'/doctor/schedule'` 会被 `requiresRole: DOCTOR` 路由守卫拦截；应改为管理员专用路由并带 `doctorId` 参数进入同一排班页组件。
- [2026-03-06] `schedule_template.period` 在数据库是 `NOT NULL` 且无默认值；若前端不传、后端不兜底会触发 `Field 'period' doesn't have a default value`，需前后端双保险（前端显式传 + 后端按 startTime 推断）。
- [2026-03-06] 医生端排班 `doctorId` 解析不能只依赖列表接口 `list` 字段；后端分页常返回 `records`，应优先使用 `my-profile` 的 `id`，并兼容 `records/list` 两种结构。
- [2026-03-06] `ScheduleServiceImpl.saveTemplate` 以 `(doctorId, dayOfWeek, period)` 查重更新，`period` 空值会同时导致新增失败和查重失效；服务层统一 `resolvePeriod` 可稳定模板写入与后续号源生成。
- [2026-03-07] 删除排班模板若只删 `schedule_template` 不联动 `schedule_slot`，会遗留可预约号源；应同步清理未预约号源，并将已预约号源置为不可用，避免继续对外放号。
- [2026-03-07] 医生端可用号源“剩余”显示长期为0的根因是字段映射错误：后端返回 `availableSlots`，前端仅读取 `remaining/availableCount`；需补齐 `availableSlots` 并保留 `totalSlots-bookedSlots` 兜底。
- [2026-03-07] 排班模板头部展示“医生ID”可读性差；在已有 `doctorId` 解析链路中补充姓名解析（路由 doctorId / my-profile / list 匹配）后，展示“医生姓名”更符合业务场景。

## 2026-03-07 Dependency Finding (Knowledge Service)
- In this codebase, `org.eclipse.jetty:jetty-client` was still present after Tika exclusion because it is also pulled by `io.milvus:milvus-sdk-java -> org.apache.hadoop:hadoop-client -> hadoop-yarn-client -> org.eclipse.jetty.websocket:websocket-client -> jetty-client`.
- Practical fix required excluding Jetty artifacts from both `tika-parsers-standard-package` and `milvus-sdk-java` in `medical-knowledge-service/pom.xml`.

## 2026-03-07 Dependency Finding (AI Service)
- `OpenAiChatModel` built with the 2-arg constructor (`new OpenAiChatModel(api, options)`) cannot resolve `withFunctions(...)` names from Spring container callbacks in Spring AI 1.0.0-M5.
- Fix is to inject `FunctionCallbackContext` and use constructor `new OpenAiChatModel(api, options, functionCallbackContext, RetryTemplate.defaultInstance())` so tool names (e.g. `searchKnowledge`) map to registered callback beans.

- [2026-03-07] New seeding task requires base URL priority http://localhost:9090/api with fallback to http://localhost:8080/api, which is opposite of current 	ests/config.py defaults (8080 primary).

- [2026-03-07] KnowledgeBaseServiceImpl.search(Long kbId, ...) had a RAG blocker: it called getKbEntity(kbId) before handling kbId=null, causing KNOWLEDGE_BASE_NOT_FOUND and empty Feign results for /kb/inner/search without kbId; fix requires a cross-KB branch with single-query embedding reuse and global topK merge by score.

- [2026-03-07] Implemented KB intelligent routing in knowledge-service search(null,...): route by cosine(queryEmbedding, kbProfileEmbedding(name+description)), then targeted KB search, and fallback to all-KB search when routing score < 0.4 or top result score < 0.5; use SLF4J '{}' placeholders (not '{:.4f}').

- [2026-03-07] ChatPanel SSE streaming in admin must parse by complete SSE event blocks (split by '\n\n') instead of per-chunk '\n' lines; otherwise partial JSON across network chunks can be appended as raw text and pollute assistant output.
- [2026-03-07] KB routing quality improved by embedding KB profile with sampled chunk topics (first line/title from up to 10 chunks) in addition to name/description, avoiding misrouting when KB descriptions are template-similar.

- [2026-03-07] ChatPanel markdown rendering should use a real parser (marked) instead of regex transforms; regex-based \n��<br/> and ad-hoc list wrapping breaks heading/list/code semantics and causes visible '#'/formatting defects in AI answers.

- [2026-03-07] In medical-admin, some route/meta titles and sidebar labels can contain malformed quote/tag text after encoding-heavy edits (e.g., missing closing quote in meta.title, malformed <span>...</span>), which causes Vite import-analysis parse failures; run 
pm.cmd run build after router/sidebar edits to catch this immediately.

- [2026-03-07] In Sidebar.vue under el-menu router mode, wrapping el-menu-item with external <div> and disabling pointer events breaks collapsed icon-only behavior/tooltips; use a sibling custom <li> styled to match menu items for external-link actions.

- [2026-03-07] For router/index.js and Sidebar.vue, partial in-place fixes are brittle after encoding corruption; full-file UTF-8 rewrite is safer to remove broken quotes/tags and restore stable build parsing.
- [2026-03-08] **Live2D Centering Strategy**: To maintain a consistent "Close-up Portrait" look across different aspect ratios, align the model's "Chest Center" (approx 75% of model height from feet) to the screen's "Visual Center" (35% from top), rather than centering the whole model or head.
- [2026-03-08] **PixiJS Transparency**: When using `pixi-live2d-display` with PixiJS v6+, simply setting `transparent: true` is not enough; `backgroundAlpha: 0` must be explicitly set in `Application` config, and CSS `canvas { background: transparent }` is recommended to override default browser stylesheets.
- [2026-03-08] **PixiJS Compatibility**: `renderer.clearBeforeRender` is read-only in some PixiJS v6 sub-versions bundled with UniApp/Vite; wrapping property assignments in `try-catch` prevents app crash during initialization.
- [2026-03-08] medical-user-service 微信登录 invalid appid (40013) 根因是本地运行时未注入 WX_APPID/WX_SECRET 环境变量，默认占位值 your-appid/your-secret 导致请求失败。

## Phase 21: CosyVoice TTS 集成研究 (2026-03-20)

### 当前 TTS 状态 — 完全不可用
- `TtsServiceImpl.synthesize()` 是一个 stub，始终返回 `null`（第43行）
- Maven 声明了 `com.alibaba.nls:nls-sdk-tts:2.2.1`（阿里云 NLS SDK），但**从未在任何 Java 类中 import 或使用**
- 没有 `dashscope-sdk-java` 依赖，`com.alibaba.dashscope.audio.ttsv2` 类不可用
- 配置使用旧 NLS 鉴权（access-key-id/secret/app-key），不是 DashScope API Key

### SSE 完成事件 ttsUrl 从未设置（结构性 Bug）
- `SseMessageVO` 有 `ttsUrl` 字段，但 `ChatServiceImpl.chat()` 中：
  - `doOnComplete` 调用 `ttsService.synthesize()` 并存 DB（但返回 null）
  - `concatWith(Mono.fromCallable(...))` 发出 complete 事件但**未设置 ttsUrl**
  - `doOnComplete` 是 side effect，其结果无法传递到 `concatWith` 的 Mono 中
- 前端（H5 main.js:137 + chat.vue:64）已经实现了 `ttsUrl` 消费逻辑，但永远收不到

### CosyVoice 方案设计

| 项目 | 决策 | 理由 |
|------|------|------|
| SDK | `com.alibaba:dashscope-sdk-java` (最新版 >= 2.19.0) | 官方 DashScope Java SDK，含 `audio.ttsv2` 包 |
| 模型 | `cosyvoice-v3-flash` | 低延迟、性价比高，适合实时交互 |
| 音色 | `longanyang` | v3-flash 系统音色，中文男声自然 |
| API Key | 复用 `DASHSCOPE_API_KEY`（已用于 LLM） | 减少配置项，同一个百炼账号 |
| WebSocket URL | `wss://dashscope.aliyuncs.com/api-ws/v1/inference` | 北京地域默认 |
| 调用方式 | 非流式 `call(text)` 阻塞返回 ByteBuffer | 简单可靠，在 `boundedElastic` 线程池上运行不影响 SSE |
| 音频格式 | MP3 (默认 22.05kHz) | 浏览器和小程序原生支持，无需转码 |
| 音频存储 | 本地文件系统 `/data/tts-audio/{messageId}.mp3` | 简单直接，Docker 可挂载 volume |
| 音频服务 | `GET /chat/tts/{messageId}` 新端点 | 通过 Gateway 路由 `/api/ai/chat/tts/{id}` 访问 |
| Markdown 过滤 | 合成前正则剥离 Markdown 符号 | `enable_markdown_filter` 仅支持复刻音色，系统音色需手动处理 |
| 文本长度限制 | 保留 300 字截断（已有） | CosyVoice 限制 20000 字符，300 字足够 |

### 架构修复：ttsUrl 传递到 SSE complete 事件
方案：使用 `AtomicReference<String>` 在 `doOnComplete`（执行 TTS 合成）和 `concatWith`（发出 complete 事件）之间共享 ttsUrl。

### DashScope SDK 潜在依赖冲突
- 可能传递 OkHttp 依赖（同 Milvus 问题），需排除 `com.squareup.okhttp3:okhttp`
- 需要 WebSocket 客户端支持（OkHttp 或 Java 原生）

### 前端就绪状态
- H5 `main.js:137-143`: `if (payload.ttsUrl) { new Audio(payload.ttsUrl) }` — 需拼接 apiBase 前缀
- UniApp `chat.vue:64-65`: `currentTtsUrl.value = payload.ttsUrl` → TtsPlayer 播放 — 与 H5 会双重播放
- TtsPlayer.vue: `uni.createInnerAudioContext()` + lip-sync 事件 — 正常工作
- **注意**: H5 直连 SSE 模式下，UniApp 的 `sendMessage` 不会被调用（Phase 17 修复），因此 TtsPlayer 不会触发，不存在双重播放问题

## 2026-03-20 智能导诊功能审计

### 导诊 Agent 架构
- **TriageAgent** 绑定 3 个工具：`searchDoctorBySymptom`、`getAvailableSlots`、`createAppointment`
- 系统提示词要求多轮问诊(2-3轮) → 推荐科室+医生 → 查号源 → 创建预约
- 工具通过 `@Bean` + `@Description` 注册到 Spring AI `FunctionCallbackResolver`
- 远程调用链：DoctorSearchTool → Feign → doctor-service；AppointmentTool → Feign → appointment-service

### 发现的 3 个严重问题

| 编号 | 问题 | 影响 | 修复方案 |
|------|------|------|----------|
| **P0** | `createAppointment` 要求 LLM 提供 `patientId`，但 LLM 无从得知当前登录用户 ID | 预约必定失败或 LLM 瞎编 ID | 在 `ChatServiceImpl.buildChatMessages()` 中将 userId 注入 system prompt，LLM 调用工具时自动使用 |
| **P1** | Function Calling 的 tool 消息（tool_call / tool_result）未持久化到 DB | 多轮对话中 LLM 丢失之前工具调用结果，可能重复调用 | 降低优先级——assistant 最终回复已含工具结果文本，LLM 可通过上文推断 |
| **P2** | 小程序 `sse.js` SSE 解析器无法处理含 `event:` 行的 SSE 块 + `chat.vue` 类型判断 `'text'` 与后端 `'token'` 不匹配 | **所有 AI 回复 token 被静默丢弃**，H5 聊天页无 AI 消息 | 修复 sse.js 按 `\n` 拆分块内行并提取 `event:`/`data:`；chat.vue 中 `'text'` → `'token'` |

### 线程上下文分析（P0 方案选择依据）
- Sa-Token 使用 ThreadLocal 存储登录态，`publishOn(Schedulers.boundedElastic())` 后切换线程，ThreadLocal 失效
- Spring AI M5 的 Function Calling 在 stream 内部执行，工具回调线程不可控
- **选定方案**：在 system prompt 中动态注入 `patientId = {userId}`，无需跨线程传递上下文，LLM 自然使用

### SSE 数据流详细分析（P2 修复依据）
- 后端 `ChatController.chat()` 使用 `ServerSentEvent.builder().event(msg.getType()).data(msg).build()`
- 输出格式：`event:token\ndata:{"type":"token","content":"你",...}\n\n`
- `sse.js` 按 `\n\n` 分割得到事件块，检查 `startsWith('data:')` → **块以 `event:` 开头，全部被跳过**
- 修复：在事件块内按 `\n` 分割各行，分别提取 `event:` 和 `data:`
- `chat.vue` 中 `payload.type === 'text'` 永远不匹配后端的 `'token'`，需改为 `'token'`

## Phase 16: 小程序与服务端 API 对接审查 (2026-03-20)

### 审查方法
Gemini 逐文件读取前端 API 模块（api/*.js + utils/sse.js + pages/**/*.vue + live2d-h5/）与后端 Controller 源码，按 Gateway StripPrefix=2 规则对比 URL/方法/参数/响应结构。

### 审查总结
- 总端点数: 18
- 已对接且正确: 11
- 存在问题: 7

### CRITICAL 问题 (运行时必崩/功能完全不可用)

| # | 前端文件 | 后端端点 | 问题描述 | 建议修复方案 |
|---|---------|---------|---------|------------|
| C1 | `api/doctor.js` | `/schedule/slots` | URL 不匹配。前端调用 `/doctor/schedule/available`，后端实际路径为 `/schedule/slots`。→ 404 | 前端 URL 改为 `/doctor/schedule/slots` |
| C2 | `pages/doctors/detail.vue` | `/schedule/slots` | 缺少必填参数 `date`。后端 `getAvailableSlots` 要求 `LocalDate date`，前端仅传 `doctorId`。→ 400 | `fetchSlots` 中传入当前日期或用户选定日期（格式 yyyy-MM-dd） |
| C3 | `pages/doctors/list.vue` | `/doctor/list` | 分页数据解析错误。`request.js` 已解包 `R.data` 返回 `PageResult` 对象，前端 `res.data?.records` 中 `res.data` 为 undefined，最终 `.map()` 对 PageResult 对象操作，报 `map is not a function` | 改为 `(res.records \|\| []).map(...)` |
| C4 | `pages/appointment/list.vue` | `/appointment/my` | 同 C3。`res` 是 PageResult 对象，前端误当数组处理 | 同上 |

### MEDIUM 问题 (功能异常/数据缺失/体验差)

| # | 前端文件 | 后端端点 | 问题描述 | 建议修复方案 |
|---|---------|---------|---------|------------|
| M1 | `api/auth.js` | `/auth/wx-login` | 响应字段不匹配。后端 `LoginVO` 返回 `user`，前端查找 `userInfo`，登录后 `userInfo` 存入 Storage 为空 | 前端改为 `result.user \|\| {}` |
| M2 | `pages/appointment/list.vue` | `/appointment/my` | 状态映射失效。后端返回 Integer (0/1/2)，前端 statusMap 仅支持字符串键 ('pending' 等)，UI 显示数字 | 前端增加 0→'待就诊' / 1→'已完成' / 2→'已取消' 映射 |
| M3 | `pages/appointment/detail.vue` | `/appointment/{id}` | 字段不匹配。前端 `appointment.date`/`appointment.time`，后端 VO 为 `appointmentDate`/`startTime` | 前端模板改为 `appointmentDate`/`startTime` |

### LOW 问题

| # | 前端文件 | 后端端点 | 问题描述 |
|---|---------|---------|---------|
| L1 | `pages/index/index.vue` | `/encyclopedia/**` | 健康科普为静态硬编码，未调用后端百科 API |
| L2 | `pages/chat/chat.vue` | `/ai/chat/send` | SSE 双重检查 eventType 和 data.type，略冗余 |

### 未对接的后端端点

| # | 后端端点 | 说明 |
|---|---------|------|
| 1 | `DELETE /ai/chat/session/{sessionId}` | 删除会话 |
| 2 | `POST /ai/chat/session/{sessionId}/end` | 结束会话 |
| 3 | `GET /ai/summary/appointment/{appointmentId}` | 预约对话摘要 |
| 4 | `POST /ai/encyclopedia/**` | 百科对话端点 |
| 5 | `GET /doctor/doctor/search` | 症状搜索医生（前端仅用带参数的 list） |

### 前端调用了但后端不存在的端点

| # | 前端文件 | 调用 URL | 实际后端端点 |
|---|---------|---------|------------|
| 1 | `api/doctor.js` | `/doctor/schedule/available` | `/schedule/slots` |

## 数字人界面消息发送无响应 — 根因分析 (2026-03-20)

### 完整消息链路
```
H5 handleSend()
  → window.parent.postMessage({type:'USER_SEND', data:text})   ← 标准 iframe API
  → window.uni.postMessage({data:{type:'USER_SEND',content:text}}) ← 小程序 web-view API
  → UniApp @message="onWebviewMessage"
  → sendMessage(text)
  → createSSERequest('/ai/chat/send', {sessionId, message})
  → 后端 ChatController.chat()
```

### 根因：H5 → UniApp 通信通道在微信小程序中不可用

| 通信方式 | 代码位置 | 问题 |
|---------|---------|------|
| `window.parent.postMessage()` | main.js:86-89 | 这是标准 iframe 通信 API，但微信小程序 `<web-view>` **不是** iframe，此调用完全无效 |
| `window.uni.postMessage()` | main.js:92-94 | 这是微信小程序正确的 H5→小程序 API，但 **`bindmessage` 仅在以下时机触发**：用户后退、组件销毁、分享。**不会实时触发！** 消息被排队但永远不会在当前页面生命周期内传递 |

**结论**: 用户在 H5 输入框发送消息后，`postMessage` 被排队但永远不送达 UniApp 的 `onWebviewMessage`，`sendMessage()` 从未被调用，后端完全不会收到请求。

### 修复方案：H5 直接 SSE 通信

既然架构已选定"方案 A：全量 H5 化"，且 H5 已通过 URL 参数拿到 `token` 和 `sessionId`，应将 SSE 通信逻辑直接内嵌到 H5 中，绕过不可靠的 postMessage 通道。

**前提条件验证**:
- ✅ Gateway CORS 已配置 `allowedOriginPatterns: "*"` + `allowedHeaders: "*"` + `allowCredentials: true`
- ✅ H5 通过 URL params 拿到 `token` 和 `sessionId`（main.js:57-62）
- ✅ SSE 端点 `POST /api/ai/chat/send` body `{sessionId, message}` header `Authorization: Bearer <token>`

**修改文件**:
1. `live2d-h5/src/main.js` — 新增 `sendToBackend()` 使用 `fetch + ReadableStream` 直接调 SSE 端点
2. `live2d-h5/vite.config.js` — 开发环境添加 `/api` 代理到 Gateway
3. `chat.vue` — 传递 `apiBase` URL 参数给 H5

## Phase 18: H5 SSE 跨域请求被 Sa-Token 拦截 — 根因分析 (2026-03-20)

### 症状
- 小程序数字人界面发送消息后提示"抱歉，服务暂时不可用，请稍后重试"
- 后端 Gateway 日志中无对应 POST 请求记录

### 全链路追踪
```
用户在 H5 发送消息
  → main.js:168 handleSend() → sendToBackend(text)
  → main.js:80  检查 sessionId/token (通过，URL params 存在)
  → main.js:90  fetch('http://localhost:8080/api/ai/chat/send', {
                   Authorization: 'Bearer xxx',
                   Content-Type: 'application/json',
                   Accept: 'text/event-stream'
                 })
  → 浏览器检测到跨域 (H5 origin: localhost:5173 → Gateway: localhost:8080)
  → 浏览器自动发送 OPTIONS 预检请求 (不携带 Authorization header)
  → Gateway SaReactorFilter 拦截 OPTIONS → StpUtil::checkLogin → 无 token → 401
  → 401 响应不含 Access-Control-Allow-* CORS 头
  → 浏览器判定 CORS 预检失败 → 阻止实际 POST 请求
  → fetch Promise reject (TypeError: Failed to fetch)
  → main.js:156 catch 块 → 显示 "抱歉，服务暂时不可用，请稍后重试"
```

### 根因确认

**P0 (CRITICAL): `AuthFilter.java` 未放行 HTTP OPTIONS 方法**

```java
// AuthFilter.java:14-30 — 当前代码
return new SaReactorFilter()
    .addInclude("/**")          // ← 包含所有请求（含 OPTIONS）
    .addExclude(/* 只排除了特定 URL 路径，未排除 HTTP 方法 */)
    .setAuth(obj -> SaRouter.match("/**", StpUtil::checkLogin));  // ← OPTIONS 也被鉴权
```

- CORS 预检 (OPTIONS) 不携带 `Authorization` header（浏览器规范）
- Sa-Token `StpUtil::checkLogin` 对 OPTIONS 返回 401
- Spring Cloud Gateway 的 `globalcors` CORS 处理在 `HandlerMapping` 层，运行在 `SaReactorFilter` (WebFilter) **之后**
- 因此 CORS headers 永远无法添加到 OPTIONS 的 401 响应中

**P1 (MEDIUM): `chat.vue` `postToH5()` 更新 URL 丢失 `apiBase`**

```javascript
// chat.vue:35 — postToH5 更新 URL 不含 apiBase
live2dUrl.value = `${live2dBaseUrl}?token=...&sessionId=...#msg=...`
// 缺少 &apiBase=...
```

若 TTS 口型事件触发 `postToH5()`，web-view 会重载 H5，丢失 `apiBase` 参数。

**P2 (LOW): 硬编码 localhost + 无 initChat 失败提示**

```javascript
// chat.vue:26 — live2d H5 地址硬编码
const live2dBaseUrl = 'http://localhost:5173'
// chat.vue:99 — Gateway 地址硬编码  
const apiBase = 'http://localhost:8080/api'
// chat.vue:101-103 — 会话创建失败仅 console.error，无用户提示
```

