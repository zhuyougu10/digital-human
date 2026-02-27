# Progress Log

> 项目：AI 数字人医疗小助手系统（毕业设计）
> 创建日期：2026-02-27

## Session: 2026-02-27

### Phase 1: Requirements & Discovery
- **Status:** complete
- **Started:** 2026-02-27
- **Completed:** 2026-02-27
- Actions taken:
  - 通过 Brainstorming 澄清用户需求：毕设项目，AI 数字人医疗小助手
  - 确认三端定位：患者小程序（Live2D）、医生 Web、管理 Web
  - 架构方案对比：5 微服务方案 vs 3 微服务方案 → 确认选方案 A（5 微服务）
  - 分段呈现系统设计并逐段获得用户确认
  - 撰写完整架构设计文档
- Files created/modified:
  - `docs/plans/2026-02-27-medical-ai-assistant-design.md` (created) — 完整架构设计文档
  - `findings.md` (updated) — 初始化知识库
  - `task_plan.md` (updated) — Phase 1 标记完成

### Phase 2: Planning & Task Decomposition
- **Status:** complete
- **Started:** 2026-02-27
- **Completed:** 2026-02-27
- Actions taken:
  - 创建实施计划总览文件 `00-overview.md`，梳理 11 个模块的依赖关系和执行顺序
  - 逐模块撰写详细实施计划（01-11），合计约 230 个微任务
  - 每个计划包含：Task 编号、涉及文件、详细步骤、代码模板、验证命令、Commit 节点
  - 计划覆盖：项目初始化 → 公共模块 → 5个业务服务 → 网关 → 两端前端 → Docker 部署
- Files created/modified:
  - `docs/plans/00-overview.md` (created) — 总览计划
  - `docs/plans/01-project-init.md` (created) — 12 Tasks: Maven 多模块骨架 + Docker 基础设施
  - `docs/plans/02-common-modules.md` (created) — 10 Tasks: core/security/mybatis/redis 公共模块
  - `docs/plans/03-user-service.md` (created) — 12 Tasks: 用户注册登录、角色权限、微信登录
  - `docs/plans/04-doctor-service.md` (created) — 10 Tasks: 医生画像、科室管理、排班配置
  - `docs/plans/05-knowledge-service.md` (created) — 12 Tasks: 知识库 CRUD、文档解析、向量检索
  - `docs/plans/06-ai-service.md` (created) — 13 Tasks: 对话管理、4个 Agent、RAG、TTS
  - `docs/plans/07-appointment-service.md` (created) — 6 Tasks: 号源查询、预约挂号
  - `docs/plans/08-gateway.md` (created) — 5 Tasks: Gateway 路由 + Sa-Token 鉴权
  - `docs/plans/09-frontend-admin.md` (created) — 18 Tasks: 管理端+医生端前端
  - `docs/plans/10-frontend-mp.md` (created) — 14 Tasks: 小程序端 + Live2D
  - `docs/plans/11-docker-deploy.md` (created) — 9 Tasks: Docker Compose 全量部署 + 联调
  - `task_plan.md` (updated) — Phase 2 标记完成

### Phase 3: Implementation (TDD)
- **Status:** in_progress
- **Started:** 2026-02-27

#### 01-project-init (12 Tasks) — COMPLETE
- Actions taken:
  - Task 1-6: Codex 创建父 POM + medical-common 聚合 + 4 个 common 子模块骨架
  - Task 7-9: Codex 创建 medical-api 聚合 + 4 个 Feign API + medical-service 聚合 + 5 个业务服务 + Gateway
  - Task 10-11: Codex 创建 Docker Compose (MySQL/Redis/Nacos/Milvus) + .gitignore
  - Task 12: 全量编译验证 BUILD SUCCESS (18 模块全部通过)
- Errors encountered:
  - [Attempt 1] Codex 生成的 package-info.java 含 UTF-8 BOM → 委派 Codex 修复
  - [Attempt 2] Codex 用 PowerShell 写入时 `r`n 变为字面量而非换行符 → OpenCode 直接用 Write 工具修复 (不重复 Codex 方案)
- Files created (41 files):
  - `medical-ai/pom.xml` — 父 POM
  - `medical-ai/medical-common/pom.xml` + 4 子模块 (core/security/mybatis/redis) 各含 pom.xml + package-info.java
  - `medical-ai/medical-api/pom.xml` + 4 子模块 (user/doctor/appointment/knowledge) 各含 pom.xml + package-info.java
  - `medical-ai/medical-service/pom.xml` + 5 子模块各含 pom.xml + Application.java + application.yml
  - `medical-ai/medical-gateway/pom.xml` + GatewayApplication.java + application.yml
  - `medical-ai/docker/docker-compose.yml` + `docker/mysql/init.sql`
  - `medical-ai/.gitignore`

#### 02-common-modules (10 Tasks) — COMPLETE
- Actions taken:
  - Task 1-6: Codex 创建 common-core 全部 9 个 Java 文件 (R, ErrorCode, BusinessException, GlobalExceptionHandler, PageQuery, PageResult, Constants, UserConstants, BaseEntity)
  - Task 7: Codex 创建 MybatisPlusConfig + MybatisPlusMetaObjectHandler + AutoConfiguration.imports
  - Task 8: Codex 创建 RedisConfig + RedisUtil + AutoConfiguration.imports
  - Task 9: Codex 创建 SaTokenConfig + SecurityUtil + SaTokenExceptionHandler + AutoConfiguration.imports
  - Task 10: 全量编译验证 BUILD SUCCESS (18 模块全部通过)
- Errors encountered:
  - [Attempt 1-4] PaginationInnerInterceptor 在 MyBatis-Plus 3.5.9 中需要 mybatis-plus-jsqlparser 依赖 → Codex 自动修复 POM
- Files created (18 files):
  - 9 Java files under `medical-common-core/src/main/java/com/medical/common/core/`
  - 2 Java files + 1 imports under `medical-common-mybatis/`
  - 2 Java files + 1 imports under `medical-common-redis/`
  - 3 Java files + 1 imports under `medical-common-security/`

#### 03-user-service (12 Tasks) — COMPLETE
- Actions taken:
  - Task 1: Codex 创建 DDL (V1__init_user_tables.sql) — 4 表 + 初始数据
  - Task 2-4: Codex 创建 Entity(4) + Mapper(4) + DTO/VO(6) = 14 Java 文件
  - Task 5: Codex 创建 StpInterfaceImpl (Sa-Token 权限适配)
  - Task 6-7: Codex 创建 AuthService/Impl + WxService/Impl + 更新 application.yml (wx.miniapp)
  - Task 8: Codex 创建 SysUserService/Impl (用户 CRUD + 分页 + 角色管理)
  - Task 9-10: Codex 创建 AuthController + SysUserController (含 /inner/{userId} 内部接口)
  - Task 11: Codex 创建 RemoteUserService Feign + UserInfoDTO + 修复 medical-user-api lombok 依赖
  - Task 12: 全量编译验证 BUILD SUCCESS (18 模块全部通过, 24 源文件)
- Errors encountered:
  - [Attempt 1] medical-user-service 缺少 lombok(provided) + spring-boot-starter-web → Codex 修复 POM，第二次编译通过
- Files created (26 files):
  - 1 SQL: `V1__init_user_tables.sql`
  - 4 Entity: SysUser/SysRole/SysUserRole/WxUserBinding
  - 4 Mapper: SysUserMapper/SysRoleMapper/SysUserRoleMapper/WxUserBindingMapper
  - 6 DTO/VO: LoginDTO/RegisterDTO/WxLoginDTO/UserUpdateDTO/UserVO/LoginVO
  - 1 Config: StpInterfaceImpl
  - 4 Service: AuthService/AuthServiceImpl/WxService/WxServiceImpl
  - 2 Service: SysUserService/SysUserServiceImpl
  - 2 Controller: AuthController/SysUserController
  - 2 Feign API: RemoteUserService/UserInfoDTO
- Files modified:
  - `medical-user-service/pom.xml` (added lombok + spring-boot-starter-web)
  - `medical-user-api/pom.xml` (added lombok)
  - `application.yml` (added wx.miniapp config)

#### 04-doctor-service (10 Tasks) — COMPLETE
- Actions taken:
  - Task 1: Codex 创建 DDL (V1__init_doctor_tables.sql) — 5 表 (department/doctor_profile/doctor_department/schedule_template/schedule_slot) + 10 个初始科室
  - Task 2: Codex 创建 5 个 Entity (Department/DoctorProfile/DoctorDepartment/ScheduleTemplate/ScheduleSlot)
  - POM 修复: Codex 添加 lombok(provided) + spring-boot-starter-web 到 doctor-service，lombok 到 doctor-api
  - Task 3: Codex 创建 5 个 Mapper 接口
  - Task 4: Codex 创建 3 DTO (DepartmentDTO/DoctorProfileDTO/ScheduleTemplateDTO) + 3 VO (DepartmentVO/DoctorVO/ScheduleSlotVO)
  - Task 5: Codex 创建 DepartmentService/Impl (科室 CRUD + toggleStatus)
  - Task 6: Codex 创建 DoctorProfileService/Impl (分页查询/症状搜索/医生自维护)
  - Task 7: Codex 创建 ScheduleService/Impl (排班模板/号源生成定时任务/乐观锁预约)
  - Task 8: Codex 创建 3 Controller (DepartmentController/DoctorController/ScheduleController 含 inner API)
  - Task 9: Codex 创建 Feign API (RemoteDoctorService/RemoteScheduleService + DoctorInfoDTO/SlotInfoDTO)
  - Task 10: 全量编译验证 BUILD SUCCESS (18 模块全部通过, 26 源文件)
  - Codex 更新 DoctorServiceApplication 添加 @EnableScheduling
- Errors encountered:
  - [Attempt 1] ScheduleSlot#getAvailableSlots() 的 @TableField 注解不能用于 method → Codex 自行修复移除

#### 05-knowledge-service (12 Tasks) — COMPLETE
- Actions taken:
  - Task 1: Codex 补充 POM 依赖 (spring-ai-openai, milvus-sdk-java, tika-parsers-standard-package, lombok, web)
  - Task 2: Codex 创建 DDL (V1__init_knowledge_tables.sql) — 3 表 (knowledge_base/knowledge_document/knowledge_chunk)
  - Task 3: Codex 创建 3 Entity + 3 Mapper + 2 DTO + 4 VO = 12 Java 文件
  - Task 4: Codex 创建 MilvusConfig + 更新 application.yml (milvus + spring.ai.openai)
  - Task 5: Codex 创建 VectorStoreService/Impl + VectorData (Milvus CRUD + COSINE 搜索)
  - Task 6: Codex 创建 DocumentParseService/Impl (Tika 解析 + 文本分块)
  - Task 7: Codex 创建 EmbeddingService/Impl (Spring AI EmbeddingModel)
  - Task 8: Codex 创建 KnowledgeBaseService/Impl (核心编排：KB CRUD/文档上传/@Async 解析流水线/语义检索/手动条目)
  - Task 9: Codex 创建 AsyncConfig (@EnableAsync + documentProcessExecutor 线程池)
  - Task 10: Codex 创建 KnowledgeBaseController (12 个 REST API + inner/search)
  - Task 11: Codex 创建 Feign API (RemoteKnowledgeService + KnowledgeSearchRequest/Result)
  - Task 12: 全量编译验证 BUILD SUCCESS (18 模块全部通过, 25 源文件)
- Errors encountered:
  - [Attempt 1] tika-parsers-standard-package 不传递依赖 tika-core → Codex 添加 tika-core:2.9.1
  - [Attempt 2] VectorStoreServiceImpl Float→Double 类型转换错误 → Codex 修复 getScore().doubleValue()
  - Codex 还主动添加了 ErrorCode 枚举值 (DOCUMENT_PARSE_ERROR, EMBEDDING_ERROR, KNOWLEDGE_BASE_NOT_FOUND 等)

#### 06-ai-service (13 Tasks) — COMPLETE
- Actions taken:
  - 前置任务: Codex 补建 appointment-api (RemoteAppointmentService + AppointmentDTO + pom.xml 添加 lombok)
  - Task 1: Codex 更新 ai-service pom.xml (添加 spring-ai-openai, webflux, web, doctor/appointment/user-api, lombok, nls-sdk-tts)
  - Task 2: Codex 创建 DDL (V1__init_ai_tables.sql) — 3 表 (chat_session/chat_message/conversation_summary)
  - Task 3: Codex 创建 3 Entity + 3 Mapper + 2 DTO + 4 VO = 12 Java 文件, 更新 AiServiceApplication (@EnableFeignClients + @MapperScan)
  - Task 4: Codex 创建 AiModelConfig (DeepSeek @Primary + Qwen 双模型), 更新 application.yml (ai/aliyun/spring.ai.openai 配置)
  - Task 5: Codex 创建 3 Tool 类 (DoctorSearchTool/AppointmentTool/KnowledgeSearchTool) + 4 Request DTO
  - Task 6: Codex 创建 Agent 接口 + 4 Agent 实现 (TriageAgent/MedicalQaAgent/SummaryAgent/EncyclopediaAgent) + AgentFactory
  - Task 7: Codex 创建 ChatService/ChatServiceImpl (核心 SSE 流式对话, Function Calling, 上下文管理)
  - Task 8: Codex 创建 TtsService/TtsServiceImpl (阿里云 TTS 占位实现, 可降级)
  - Task 9: Codex 创建 SummaryService/SummaryServiceImpl (@Async 异步摘要, JSON 解析) + AsyncConfig
  - Task 10-12: Codex 创建 ChatController + SummaryController + EncyclopediaController
  - Task 13: 全量编译验证 BUILD SUCCESS (18 模块全部通过, 37 源文件)
- Errors encountered:
  - [Attempt 1] Entity 缺少 id 字段 (BaseEntity 不含 id) → Codex 补充 @TableId(type=IdType.AUTO) private Long id
  - 7 个 deprecation warnings (Spring AI withModel/withTemperature/withMaxTokens/withFunctions 已标记过时, 但不影响编译)
- Files created (40+ files):
  - appointment-api: RemoteAppointmentService + AppointmentDTO + pom.xml 更新
  - ai-service: 37 Java 源文件
    - 3 Entity: ChatSession/ChatMessage/ConversationSummary
    - 3 Mapper: ChatSessionMapper/ChatMessageMapper/ConversationSummaryMapper
    - 2 DTO: ChatRequestDTO/CreateSessionDTO
    - 4 VO: ChatSessionVO/ChatMessageVO/ConversationSummaryVO/SseMessageVO
    - 1 Interface: Agent
    - 4 Agent: TriageAgent/MedicalQaAgent/SummaryAgent/EncyclopediaAgent
    - 1 Factory: AgentFactory
    - 3 Tool: DoctorSearchTool/AppointmentTool/KnowledgeSearchTool
    - 4 Request: SearchDoctorRequest/GetSlotsRequest/CreateAppointmentRequest/KnowledgeSearchRequest
    - 2 Config: AiModelConfig/AsyncConfig
    - 3 Service: ChatService+Impl, TtsService+Impl, SummaryService+Impl
    - 3 Controller: ChatController/SummaryController/EncyclopediaController
    - 1 DDL: V1__init_ai_tables.sql
  - pom.xml 更新, application.yml 更新, AiServiceApplication.java 更新
- Files created (28+ files):
  - 1 SQL: V1__init_knowledge_tables.sql
  - 3 Entity: KnowledgeBase/KnowledgeDocument/KnowledgeChunk
  - 3 Mapper: KnowledgeBaseMapper/KnowledgeDocumentMapper/KnowledgeChunkMapper
  - 2 DTO: KnowledgeBaseDTO/ChunkManualDTO
  - 4 VO: KnowledgeBaseVO/KnowledgeDocumentVO/KnowledgeChunkVO/SearchResultVO
  - 1 Domain: VectorData
  - 2 Config: MilvusConfig/AsyncConfig
  - 8 Service: VectorStoreService+Impl/DocumentParseService+Impl/EmbeddingService+Impl/KnowledgeBaseService+Impl
  - 1 Controller: KnowledgeBaseController
  - 4 Feign API: RemoteKnowledgeService/KnowledgeSearchRequest/KnowledgeSearchResult/package-info
- Files created (27 files):
  - 1 SQL: `V1__init_doctor_tables.sql`
  - 5 Entity: Department/DoctorProfile/DoctorDepartment/ScheduleTemplate/ScheduleSlot
  - 5 Mapper: DepartmentMapper/DoctorProfileMapper/DoctorDepartmentMapper/ScheduleTemplateMapper/ScheduleSlotMapper
  - 6 DTO/VO: DepartmentDTO/DoctorProfileDTO/ScheduleTemplateDTO/DepartmentVO/DoctorVO/ScheduleSlotVO
  - 6 Service: DepartmentService+Impl/DoctorProfileService+Impl/ScheduleService+Impl
  - 3 Controller: DepartmentController/DoctorController/ScheduleController
  - 4 Feign API: RemoteDoctorService/RemoteScheduleService/DoctorInfoDTO/SlotInfoDTO
- Files modified:
  - `medical-doctor-service/pom.xml` (added lombok + spring-boot-starter-web)
  - `medical-doctor-api/pom.xml` (added lombok)
  - `DoctorServiceApplication.java` (added @EnableScheduling)

### Phase 4: Testing & Verification
- **Status:** pending
- Actions taken:
  - （尚未开始）

### Phase 5: Delivery
- **Status:** pending
- Actions taken:
  - （尚未开始）

## Test Results

| Test | Input | Expected | Actual | Status |
|------|-------|----------|--------|--------|
| （Phase 3 开始后填写） | | | | |

## Error Log

| Timestamp | Error | Attempt | Resolution |
|-----------|-------|---------|------------|
| 2026-02-27 23:41 | Java 文件含 UTF-8 BOM (\\ufeff) | 1 | 委派 Codex 重写文件 |
| 2026-02-27 23:43 | Codex 用 PowerShell 写入 \`r\`n 成字面量 | 2 | OpenCode 直接用 Write 工具修复 (不重复 Codex 方案) |

## 5-Question Reboot Check

| Question | Answer |
|----------|--------|
| Where am I? | Phase 3 进行中，06-ai-service 已完成，下一步 07-appointment-service |
| Where am I going? | 07-appointment → 08-gateway → 09/10-frontend → 11-deploy |
| What's the goal? | 构建基于 Spring Cloud + Spring AI + RAG + AI Agents + Vue3 + UniApp 的 AI 数字人医疗小助手系统 |
| What have I learned? | 5微服务架构、技术栈选型、4个Agent设计、Live2D via web-view；BaseEntity不含id需自行声明；Spring AI withModel/withFunctions deprecated但可用 |
| What have I done? | Phase 1-2 完成；Phase 3: 01~06 完成 (project-init/common-modules/user-service/doctor-service/knowledge-service/ai-service) |

---
*Update after completing each phase or encountering errors*
| 2026-02-28 00:06 | `mvn clean compile -pl medical-common/medical-common-core` compile failed: missing MyBatis-Plus annotations in `BaseEntity` (`TableField`/`TableLogic`/`FieldFill`) | 1 | Added `com.baomidou:mybatis-plus-annotation:${mybatis-plus.version}` to `medical-common-core/pom.xml`, then recompiled |
| 2026-02-28 00:18 | `mvn clean compile -f medical-ai/pom.xml` failed at `medical-common-security`: Lombok annotations not resolvable (`@Slf4j` / `log`) | 1 | Added `org.projectlombok:lombok` (`provided`) to `medical-common-security`, `medical-common-mybatis`, and `medical-common-redis` module POMs, then recompiled |
| 2026-02-28 00:19 | `mvn clean compile -f medical-ai/pom.xml` failed at `medical-common-mybatis`: `PaginationInnerInterceptor` class not found | 2 | Added `com.baomidou:mybatis-plus-extension` to `medical-common-mybatis/pom.xml`, then recompiled |
| 2026-02-28 00:20 | Added `mybatis-plus-extension` without explicit version caused POM validation failure | 3 | Set `mybatis-plus-extension` version to `${mybatis-plus.version}` in `medical-common-mybatis/pom.xml` |
| 2026-02-28 00:21 | `PaginationInnerInterceptor` still unresolved with `mybatis-plus-extension:3.5.9` | 4 | Confirmed class location via jar inspection; added `com.baomidou:mybatis-plus-jsqlparser:${mybatis-plus.version}` to `medical-common-mybatis/pom.xml` |
| 2026-02-28 01:34 | `mvn clean compile -f medical-ai/pom.xml` failed in `medical-doctor-service`: `ScheduleSlot#getAvailableSlots()` annotated with `@TableField` on method, causing annotation target error and cascading symbol errors | 1 | Removed the invalid method-level `@TableField` usage in `ScheduleSlot.java`, kept available slot calculation in VO/service mapping, then recompiled |
| 2026-02-28 01:40 | Re-run `mvn clean compile -f medical-ai/pom.xml` after doctor-service fixes | 2 | BUILD SUCCESS (all 18 modules compiled) |

| 2026-02-28 02:12 | mvn/mvn.cmd not found when running knowledge-service compile validation (-pl medical-service/medical-knowledge-service) | 1-2 | Checked command availability and Maven wrapper (mvnw) in repo; none found in current environment PATH, so compile verification is pending until Maven is available. |
| 2026-02-28 02:24 | Compile verification for medical-knowledge-service failed because mvn and mvn.cmd are not available in environment PATH when running mvn clean compile -f medical-ai/pom.xml -pl medical-service/medical-knowledge-service -am | 1-2 | Applied dependency fix (	ika-core:2.9.1) in module POM; compile verification is pending until Maven is installed or PATH is configured. |
| 2026-02-28 02:27 | Compile verification after Task 8/9 changes failed because mvn and mvn.cmd are unavailable when running mvn clean compile -f medical-ai/pom.xml -pl medical-service/medical-knowledge-service -am | 1-2 | Applied all requested code changes; compilation remains pending until Maven executable is available in PATH. |
| 2026-02-28 02:35 | Full compile mvn clean compile -f medical-ai/pom.xml failed in medical-doctor-service with missing com.medical.api.doctor.dto classes (DoctorInfoDTO/SlotInfoDTO) before medical-knowledge-service stage | 1 | Kept Task 10-11 changes intact; compile is blocked by unrelated module baseline error and needs doctor-service/api fix first. |
| 2026-02-28 02:36 | Follow-up module compile command mvn clean compile -f medical-ai/pom.xml -pl medical-service/medical-knowledge-service -am could not run because mvn command was unavailable in current shell PATH | 2 | Stopped retries per rule; verification result remains pending until Maven command availability is stable in environment. |
| 2026-02-28 02:50 | Attempted to run `git -C D:\project\数字人\medical-ai status --short` for change verification, but `git` command is unavailable in current PowerShell PATH | 1 | Switched to direct file-content verification (`Get-Content`) for modified POM/class files; no further retries on missing git command. |

- [2026-02-28] Error: mvn command not found when running compile for medical-ai-service (attempt 1). Cause: Maven CLI not installed or not in PATH. Action: fall back to Maven Wrapper (mvnw) if present; otherwise report compile verification not executable in current environment.

- [2026-02-28] Error: git command not found when trying to check working tree status. Cause: Git CLI not installed or not in PATH. Action: used direct file scan (g) to verify created classes instead of git status.
- [2026-02-28] File write issue during Task 10-12: PowerShell Set-Content in this environment does not support utf8NoBOM encoding name (attempt 1). Resolved by writing files with [System.IO.File]::WriteAllText(..., [System.Text.UTF8Encoding]::new(False)) to enforce UTF-8 without BOM.
| 2026-02-28 03:04 | `mvn clean compile -f D:\project\������\medical-ai\pom.xml` failed at `medical-ai-service`: `ChatSession/ChatMessage/ConversationSummary` missing `getId()` target fields | 1 | Added explicit `id` PK fields with `@TableId(type = IdType.AUTO)` in all three entities, then recompiled successfully. |
| 2026-02-28 03:06 | Re-ran full reactor compile after AI entity PK fix | 2 | BUILD SUCCESS (all 18 modules compiled). |
| 2026-02-28 03:08 | `mvn clean compile -f D:\project\������\medical-ai\pom.xml -rf :medical-ai-service` failed: upstream internal artifacts (`medical-common-*`, `medical-*-api`) unresolved | 1 | Installed required upstream modules to local Maven repo via `mvn clean install -DskipTests -f D:\project\������\medical-ai\pom.xml -pl medical-service/medical-ai-service -am`, then reran exact `-rf` compile. |
| 2026-02-28 03:10 | Re-ran exact command `mvn clean compile -f D:\project\������\medical-ai\pom.xml -rf :medical-ai-service` after install | 2 | BUILD SUCCESS (medical-ai-service, medical-appointment-service, medical-knowledge-service). |
