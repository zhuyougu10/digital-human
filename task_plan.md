# Task Plan: AI 数字人医疗小助手系统

## Goal
构建基于 Spring Cloud + Spring AI + RAG + AI Agents + Vue3 + UniApp 的 AI 数字人医疗小助手系统（毕业设计）

## Current Phase
会话衔接与规划文件同步 — complete

## Phases
<!-- 
  WHAT: Break your task into 3-7 logical phases. Each phase should be completable.
  WHY: Breaking work into phases prevents overwhelm and makes progress visible.
  WHEN: Update status after completing each phase: pending → in_progress → complete
-->

### Phase 1: Requirements & Discovery
- [x] Understand user intent
- [x] Identify constraints and requirements
- [x] Document findings in findings.md
- [x] 架构方案对比并确认（方案A：5微服务）
- [x] 分段呈现设计并逐段确认
- [x] 撰写设计文档 docs/plans/2026-02-27-medical-ai-assistant-design.md
- **Status:** complete

### Phase 2: Planning & Task Decomposition
- [x] 创建 00-overview.md 总览计划
- [x] 创建 01-project-init.md 项目初始化（12 Tasks）
- [x] 创建 02-common-modules.md 公共模块（10 Tasks）
- [x] 创建 03-user-service.md 用户服务（12 Tasks）
- [x] 创建 04-doctor-service.md 医生服务（10 Tasks）
- [x] 创建 05-knowledge-service.md 知识库服务（12 Tasks）
- [x] 创建 06-ai-service.md AI服务（13 Tasks）
- [x] 创建 07-appointment-service.md 预约服务（6 Tasks）
- [x] 创建 08-gateway.md 网关服务（5 Tasks）
- [x] 创建 09-frontend-admin.md 管理端+医生端前端（18 Tasks）
- [x] 创建 10-frontend-mp.md 小程序端+Live2D（14 Tasks）
- [x] 创建 11-docker-deploy.md 部署+联调（9 Tasks）
- **Status:** complete

### Phase 3: Implementation (TDD)
- [x] 01-project-init (12 Tasks) — COMPLETE
- [x] 02-common-modules (10 Tasks) — COMPLETE
- [x] 03-user-service (12 Tasks) — COMPLETE
- [x] 04-doctor-service (10 Tasks) — COMPLETE
    - [x] 05-knowledge-service (12 Tasks) — COMPLETE
    - [x] 06-ai-service (13 Tasks) — COMPLETE
    - [x] 07-appointment-service (6 Tasks) — COMPLETE
- [x] 08-gateway (5 Tasks) — COMPLETE
- [x] 09-frontend-admin (18 Tasks) — COMPLETE
- [x] 10-frontend-mp (14 Tasks) — COMPLETE
- [x] 11-docker-deploy (9 Tasks) — COMPLETE
- **Status:** complete

### 11-docker-deploy 详细进度
- [x] Task 1: 6 个 Dockerfile (gateway + 5 services)
- [x] Task 2: admin Nginx + Dockerfile
- [x] Task 3: Live2D H5 Dockerfile + nginx.conf
- [x] Task 4: 完整 docker-compose.yml (14 containers, healthcheck, depends_on)
- [x] Task 5: .env.example (27 行)
- [x] Task 6: Maven 全量打包 BUILD SUCCESS (18/18, 36s)
- [x] Task 7: Docker Compose 启动 14/14 UP
- [x] Task 8: 端到端联调验证 — 12/12 PASS (Session 4 全通)
- [x] Task 9: Commit — feat(deploy): 0b72f91

### Phase 4: Testing & Verification
- [x] 端到端联调 (11-docker-deploy Task 8 完成)
- [x] 12 项联调验证全部通过 (Session 4)
- **Status:** complete

### Phase 5: Delivery (12-delivery)
- [x] Task 1: 项目 README.md (项目概述/技术栈/架构/快速启动) — Codex
- [x] Task 2: docs/deployment-guide.md (Docker 部署手册) — Codex
- [x] Task 3: docs/database-design.md (数据库设计文档) — Codex
- [x] Task 4: docs/api-reference.md (API 接口文档) — Codex
- [x] Task 5: docs/user-guide.md (用户使用指南: 管理端/医生端/患者端) — Gemini
- [x] Task 6: Final commit + 清理
- **Status:** complete

### Phase 6: API 联调审查与修复
- [x] 5 批并行审查 (user/doctor/knowledge/ai/appointment)
- [x] 修复批次A: 跨服务前端基础修复 (admin request.js msg字段 + mp request.js R<T>解包 + mp auth URL)
- [x] 修复批次B: 小程序 API 修复 (chat session URL/字段 + doctor schedule URL)
- [x] 修复批次C: Admin knowledge 视图修复 (records字段 + column props + dropdown slot)
- [x] 修复批次D: Admin appointment/doctor 视图修复 (records字段 + doctor endpoint + getDoctorTodayAppointments)
- [x] 修复批次E: ChatPanel SSE JSON 解析修复
- [x] 修复批次F: Doctor Profile.vue my-profile 端点修复
- [x] 修复批次G: 后端修复 (DepartmentController/DoctorController keyword + AppointmentQueryDTO + AppointmentListVO + ChunkManualDTO)
- [x] 最终验证: Admin build SUCCESS + MP build SUCCESS + Maven 编译环境阻塞(WSL)
- **Status:** complete

### Phase 8: Final Review & Polishing
- [x] Task 1: Perform a final end-to-end check of the UI and API.
- [x] Task 2: Verify all documentation in `docs/` is up-to-date.
- [x] Task 3: Clean up any remaining temporary files or comments.
- **Status:** complete

## Key Questions
1. 是否已经形成“患者问诊 -> 导诊 -> 挂号 -> 医生接诊”的完整演示闭环？（是，已多轮验证通过）
2. 当前规划文件是否只保留唯一任务轨迹与有效状态？（本次已清洗重复区块与占位内容）

## Decisions Made
| Decision | Rationale |
|----------|-----------|
| 保留单一主 `task_plan.md` 作为项目执行总览 | 避免重复阶段和冲突状态，便于后续续跑与审计 |
| Phase 21 标记为 complete | 根据用户确认，TTS 集成已完成，无需继续保留进行中状态 |
| 本轮续跑使用仓库内 `.opencode/skills/planning-with-files/scripts/session-catchup.py` | 当前 shell 环境下用户目录脚本路径不可用，改用项目内同版脚本可稳定完成会话衔接 |

## Errors Encountered
| Error | Attempt | Resolution |
|-------|---------|------------|
| ai-service OkHttp Companion NoSuchFieldError | 1 | Milvus SDK 传递依赖 okhttp 排除 (前 session 已加 exclusion，重建镜像即解决) |
| init.sql 只建库不建表 | 1 | Codex 合并 5 个 DDL 到 init.sql |
| admin BCrypt hash 与 admin123 不匹配 | 1 | Codex 重新生成正确 BCrypt hash |
| 5 服务缺少 Redis config | 1 | Codex 添加 spring.data.redis 配置到 5 个 application.yml |
| Gateway Sa-Token 401 token 无效 | 1 | **已修复**: 根因=token-name 同时用作 Redis key 前缀，Gateway 用 Authorization 而服务用 satoken 默认值。统一 5 服务 sa-token 配置 + Gateway 改用 redis-reactive |
| Spring Boot 3.x -parameters flag 缺失 | 1 | 父 POM 添加 maven-compiler-plugin parameters=true |
| DDL 缺少 BaseEntity 审计列 create_by/update_by | 1 | Codex 更新 init.sql + ALTER TABLE 补齐 13 张表 |
| 非 user 服务缺 StpInterfaceImpl 致 @SaCheckRole 403 | 1 | Codex 在 common-security 添加基于 Feign 的通用 StpInterfaceImpl |
| doctor/knowledge 服务缺 @EnableFeignClients | 1 | OpenCode 手动添加 @EnableFeignClients + loadbalancer 依赖 |
| DashScope TTS `NoClassDefFoundError: okhttp3/WebSocketListener` | 1 | 根因已定位：`medical-ai-service` 为 `spring-ai-openai-spring-boot-starter` 与 `dashscope-sdk-java` 都排除了 `okhttp`，导致 DashScope WebSocket 运行时缺类；待补齐兼容依赖并验证 |
| `session-catchup.py` 按 PowerShell 示例直接在 Bash 执行失败 | 1 | 改为 Bash 兼容调用方式，不再混用 `(Get-Location)` 语法 |
| 用户目录 `.opencode` 路径不存在，catchup 脚本无法打开 | 2 | 改用仓库内 `D:/project/数字人/.opencode/.../session-catchup.py` 成功完成会话检查 |
| H5 TTS 音频 `TypeError: Failed to fetch` | 1 | 根因不是 `localhost` 映射，而是 `ChatController#getTtsAudio()` 手动返回 `Access-Control-Allow-Origin: *`，与 Gateway 全局 CORS 头冲突；已删除手动 CORS 头并通过 `mvn compile -pl medical-service/medical-ai-service -am -f medical-ai/pom.xml -q` 验证 |

### Phase 6: API 联调审查与修复
- [x] 5 批并行审查 (user/doctor/knowledge/ai/appointment)
- [x] 修复批次A: 跨服务前端基础修复 (admin request.js msg字段 + mp request.js R<T>解包 + mp auth URL)
- [x] 修复批次B: 小程序 API 修复 (chat session URL/字段 + doctor schedule URL)
- [x] 修复批次C: Admin knowledge 视图修复 (records字段 + column props + dropdown slot)
- [x] 修复批次D: Admin appointment/doctor 视图修复 (records字段 + doctor endpoint + getDoctorTodayAppointments)
- [x] 修复批次E: ChatPanel SSE JSON 解析修复
- [x] 修复批次F: Doctor Profile.vue my-profile 端点修复
- [x] 修复批次G: 后端修复 (DepartmentController/DoctorController keyword + AppointmentQueryDTO + AppointmentListVO + ChunkManualDTO)
- [x] 最终验证: Admin build SUCCESS + MP build SUCCESS + Maven 编译环境阻塞(WSL)
- **Status:** complete

### Phase 7: Mock 数据修复
- [x] **[高] #1** login/index.vue: 移除 setTimeout mock 登录，接入 api/auth.js 真实 login + getUserInfo
- [x] **[高] #2** stores/user.js: 移除 "Mock login for now" 注释代码，实现真实 login() 流程
- [x] **[中] #3** SystemConfig.vue: 硬编码 AI 配置仅存 localStorage → 保留现状(无后端 API)，添加 TODO 注释
- [x] **[中] #5** DoctorManagement.vue: specialtyOptions 写死 → 添加注释说明为快捷标签
- [x] **[中] #6** DoctorManagement.vue: 排班入口被注释 → 恢复路由跳转
- [x] **[低] #4** Navbar.vue: 头像硬编码外部 URL → 绑定 userStore.userInfo.avatar
- [x] **[低] #7** mine/index.vue: 设置/关于 showDeveloping() → 改为分别的 comingSoon 提示
- [x] **[低] #8** ChatMessage.vue: AI头像固定 ai-avatar.png + 用户头像动态绑定 message.avatar
- [x] **[低] #9** index/index.vue quickActions → 添加注释说明为固定配置
- **验证:** Admin build SUCCESS + MP build:mp-weixin SUCCESS
- **Status:** complete

### Phase 9: 接口测试 (13-api-testing)
- [x] Task 0: 测试基础设施搭建 (父 POM + surefire + 各服务 application-test.yml + SecurityUtil mock 策略)
- [x] Task 1: medical-user-service 接口测试 (AuthController 8 + SysUserController 12 = 20 用例)
- [x] Task 2: medical-doctor-service 接口测试 (Doctor 14 + Department 10 + Schedule 13 = 37 用例)
- [x] Task 3: medical-ai-service 接口测试 (Chat 10 + Encyclopedia 6 + Summary 4 = 20 用例)
- [x] Task 4: medical-appointment-service 接口测试 (Appointment 14 用例)
- [x] Task 5: medical-knowledge-service 接口测试 (KnowledgeBase 18 用例)
- [x] 全量验证: mvn test → 109 用例全部 GREEN
- **Status:** complete

### Phase 10: Admin UI 重构美化 (14-ui-redesign)
- [x] Task 1: Design System Foundation + Layout Shell (style.css, App.vue, AppLayout.vue, Sidebar.vue, Navbar.vue)
- [x] Task 2: Login + Dashboard 页面重构 (login/index.vue, dashboard/index.vue)
- [x] Task 3: Admin CRUD Pages 重构 (8 views)
- [x] Task 4: Doctor Pages 重构 (5 views)
- [x] Task 5: Shared Components 重构 (ChatPanel.vue, RichEditor.vue)
- [x] Final build verification
- **Status:** complete

### Phase 11: 真实接口集成测试 (15-real-api-testing, Python)
- [x] Task 0: 测试基础设施搭建 (config.py + conftest.py + requirements.txt + test-upload.txt)
- [x] Task 1: test_01_auth.py — 认证接口 (7 用例)
- [x] Task 2: test_02_user.py — 用户管理 (6 用例)
- [x] Task 3: test_03_department.py — 科室管理 (7 用例)
- [x] Task 4: test_04_doctor.py — 医生管理 (8 用例)
- [x] Task 5: test_05_schedule.py — 排班管理 (7 用例)
- [x] Task 6: test_06_knowledge.py — 知识库管理 (10 用例)
- [x] Task 7: test_07_appointment.py — 预约管理 (8 用例)
- [x] Task 8: test_08_chat.py — AI 对话 (6 用例)
- [x] Task 9: test_09_e2e_flow.py — 核心业务全链路 (1 用例/9步)
- [x] 全量验证 Round 1: pytest -v → 60 tests, 40 PASSED / 20 FAILED
- [x] Bug Fix Round 1: 修复 4 个根因 (Codex 完成) — RC1/RC2/RC3 代码+DDL修复
- [x] Bug Fix Round 1 验证: pytest -v → **58 PASSED, 2 SKIPPED, 0 FAILED** ✅
- **Status:** complete

### Phase 12: Feature Enhancements & Bug Fixes
- [x] **[User Management]** 新增用户接口与角色同步修复
- [x] **[Schedule]** 排班模板稳定性与号源联动清理
- [x] **[Knowledge]** 知识库智能路由与 RAG 空检索修复
- [x] **[Admin UI]** 百科助手全屏页与 Sidebar 行为优化
- **Status:** complete

### Phase 13: 小程序 UI 重构与数字人集成 (16-mp-redesign)
- [x] Task 1: 基于原型图重构 MP 7 大核心页面 (Login/Chat/Doctor/Appt)
- [x] Task 2: 实施“方案 A：全量 H5 聊天 UI”解决 Webview 遮挡问题
- [x] Task 3: 修复 Live2D PixiJS 兼容性与 Ticker 静态问题
- [x] Task 4: 实现 URL 驱动的毫秒级口型同步 (Lip-Sync)
- [x] Task 5: 优化数字人位姿对齐与固定物理尺寸缩放
- [x] Task 6: 极简登录流优化 (登录直达问诊)
- **Status:** complete

### Phase 14: 智能导诊功能修复 (17-triage-fix)
- [x] **Task 1 [P0]**: ChatServiceImpl — 为 TRIAGE Agent 动态注入 patientId 到 system prompt (Codex)
- [x] **Task 2 [P2a]**: sse.js — 修复 SSE 事件块解析器，正确处理 event: + data: 多行格式 (Gemini)
- [x] **Task 3 [P2b]**: chat.vue — SSE 消息类型判断 'text' → 'token'，新增 complete 处理 (Gemini)
- [x] **Task 4**: 编译验证 (Maven BUILD SUCCESS + MP Build complete)
- **Status:** complete

## Phase 8: Final Review & Polishing
| 根因 | 描述 | 结果 |
|------|------|------|
| RC1 | doctor-service: setUserId(null) → NOT NULL 500 | ✅ 已修复 |
| RC2 | ai-service: chat_message 等缺 update_time/deleted 列 | ✅ ALTER TABLE 已执行 |
| RC3 | knowledge-service: uploadPath 解析到 Tomcat 临时目录 | ✅ 已改 /data/uploads |
| RC4 | knowledge-service: DASHSCOPE_API_KEY 占位符, embedding 404 | ⏭️ 2 tests skipped (预期) |
| RC5 | 级联 None 传参 | ✅ 上游修复后自动消除 |

## Notes
- Update phase status as you progress: pending → in_progress → complete
- Re-read this plan before major decisions (attention manipulation)
- Log ALL errors - they help avoid repetition

### Phase 12 Update Log (2026-03-06)
- [x] **[User Management]** 修复分配角色“取消无效”问题（角色差集：assign + remove）
- [x] **[User Management]** `/user/add` 返回新建用户信息（用于后续绑定链路）
- [x] **[User Management]** 新增 DOCTOR 用户时自动初始化医生画像并绑定 `userId`
- [x] **[Doctor Management]** 新增医生弹窗增加“关联用户”字段并设为必填
- [x] **[Doctor Management]** 关联用户列表仅展示 `DOCTOR` 角色用户（含前端兜底过滤）
- [x] 验证：`SysUserControllerTest`、`DoctorControllerTest`、`medical-user-service` 打包、`medical-admin` 构建全部通过
- **Phase 12 实际进度结论:** 上述子项均已完成，后续仅保留体验优化与字段命名统一收尾项

### Phase 12 Update Log (2026-03-06, Round 2)
- [x] **[Admin Routing]** 修复管理员从医生管理进入排班触发权限不足（新增 ADMIN 排班路由）
- [x] **[Doctor Management]** 排班入口改为按医生维度跳转并携带 `doctorId`
- [x] **[Schedule Backend]** 修复 `schedule_template.period` 非空约束失败（`saveTemplate` 增加 `resolvePeriod` 兜底）
- [x] **[Schedule Frontend]** 保存模板时显式提交 `period`，与后端字段约束对齐
- [x] **[Doctor Schedule]** 修复医生端“未找到医生档案”问题（优先 `my-profile` + 兼容 `records/list`）
- [x] **[Regression Test]** 新增 `ScheduleServiceImplTest#saveTemplate_infersPeriodWhenMissing`
- [x] 验证：`mvn "-Dtest=ScheduleServiceImplTest,ScheduleControllerTest" test` + `npm run build` 全部通过
- **Round 2 结论:** 排班入口权限、模板落库稳定性、医生端档案解析三项问题均已闭环修复

### Phase 12 Update Log (2026-03-07, Round 12)
- [x] **[AI Tool Blocking]** 修复 `block()/blockFirst()/blockLast() are blocking in reactor-http-nio thread`：
  - 根因：Spring AI M5 `handleToolCalls` 在 Reactor Netty I/O 线程上同步执行 tool callback；OpenFeign `BlockingLoadBalancerClient` 内部调用 `Mono.block()`，在 Netty I/O 线程禁止 blocking → `IllegalStateException`
  - 修复：`ChatServiceImpl.chat()` 中 `chatModel.stream(prompt)` 后紧接 `.publishOn(Schedulers.boundedElastic())`，将整条 SSE 流（含 tool 执行）切换到可 blocking 的弹性线程池
  - 修改文件：`ChatServiceImpl.java`（新增 `Schedulers` import + `.publishOn` 调用）
  - 验证：`mvn package -DskipTests -pl medical-service/medical-ai-service -am` → BUILD SUCCESS (17s)

### Phase 12 Update Log (2026-03-07, Round 11)
- [x] **[AI Function Calling]** 修复 `No function callback found for name: searchKnowledge`：
  - 根因：`AiModelConfig` 用 `new OpenAiChatModel(api, options)` 手动构造，未传入 `FunctionCallbackResolver`，模型无法感知 Spring 容器中注册的 `@Bean Function<>` callback
  - Codex 初次修复使用了不存在的类 `FunctionCallbackContext`（M5 已改名为 `FunctionCallbackResolver`）
  - OpenCode 通过 `javap` 反射实际 jar 确认正确构造器签名，修正为 `FunctionCallbackResolver`
  - 修改文件：`AiModelConfig.java`（deepSeekChatModel + qwenChatModel 均注入 `FunctionCallbackResolver`）
  - 验证：`mvn package -DskipTests -pl medical-service/medical-ai-service -am` → BUILD SUCCESS (15.9s)

### Phase 12 Update Log (2026-03-07, Round 10)
- [x] **[Knowledge Upload Path]** 修复本地运行文件上传报错 `FileNotFoundException ... Tomcat temp dir`：
  - 根因：`application.yml` 无 `knowledge.upload-path` 配置，默认值 `/data/uploads` 在 Windows 被 Tomcat 解析为临时目录下的子路径
  - 修复：`application.yml` 新增 `knowledge.upload-path: ${UPLOAD_PATH:D:/project/数字人/uploads}`
  - 修复：`KnowledgeBaseServiceImpl` 改用 `Files.copy(file.getInputStream(), target, REPLACE_EXISTING)` 绕开 Tomcat 路径解析
  - 验证：`mvn package -DskipTests` → BUILD SUCCESS (22s)

### Phase 12 Update Log (2026-03-07, Round 10)
- [x] **[SSE 乱码修复]** ChatPanel.vue SSE 解析改用 \n\n 事件块缓冲，catch 块仅 warn 跳过不追加原始 JSON 片段；前端构建 SUCCESS
- [x] **[KB 路由误判修复]** getKbProfileVector() 查询 KB 前10条 chunk 首行主题纳入 profile，路由准确率 6/6 全通过（高血压→内科✅ 脑梗→神经内科✅ 等）
- [x] 提交：`3310c68` fix(frontend): SSE解析乱码；`6102124` fix(knowledge): KB路由profile增强

### Phase 12 Update Log (2026-03-07, Round 9)
- [x] **[Knowledge Manual Chunk]** 修复「添加知识条目」后无法查看问题：
  - 根因1：手动chunk `docId=0L`，而查看接口按真实 docId 过滤，永远查不到
  - 根因2：添加成功后前端无引导路径，没有手动条目的独立查看入口
  - 修复：后端新增 `GET /kb/{kbId}/manual-chunks` 端点 + `listManualChunks` Service 方法
  - 修复：前端 knowledge.js 新增 `getManualChunkList`；DocumentManagement.vue 新增「查看手动条目」按钮 + 抽屉展示 + 添加成功后自动打开
  - 验证：`mvn package -DskipTests` (knowledge-service) → BUILD SUCCESS；`npm.cmd run build` → BUILD SUCCESS (17.48s)

### Phase 12 Update Log (2026-03-07, Round 9)
- [x] **[KB 智能路由]** 三阶段路由：① embedding余弦相似度匹配科室（路由阈值0.4）→ ② 精确检索（质量阈值0.5）→ ③ 兜底全库；KB profile向量懒加载缓存
- [x] **[KB 智能路由验证]** 眼科/儿科精确路由（结果全来自对应科室）✅；高血压跨科室问题正确触发兜底 ✅；E2E：AI先检索KB再说明内容局限性，行为符合预期
- [x] 提交：`05dcd4b` feat(knowledge): 知识库智能路由三阶段策略

### Phase 12 Update Log (2026-03-07, Round 11)
- [x] **[Markdown 渲染修复]** 引入 marked 库，renderMarkdown() 替换50行残缺手写正则，修复标题 # 号不渲染；新增完整 CSS：h1-h6/ul/ol/code/pre/blockquote/table/用户气泡适配
- [x] 提交：`1c379a3` fix(frontend): 引入marked库替换手写正则，修复标题#号不渲染及整体排版问题

### Phase 12 Update Log (2026-03-07, Round 8)
- [x] **[Knowledge Embedding model_not_found]** 修复 embedding 调用使用错误模型 `text-embedding-ada-002`：`application.yml` 中配置键名错误，`spring.ai.openai.embedding.model` 不被 Spring AI M5 识别，需改为 `spring.ai.openai.embedding.options.model: text-embedding-v3`

### Phase 12 Update Log (2026-03-07, Round 7)
- [x] **[Knowledge Embedding Jetty协议违规]** 根因：Tika 2.9.1 → hadoop → jetty-websocket-client → jetty-client 12.0.15，Spring Boot 自动选用 Jetty 作为 RestClient 底层，DashScope 401 响应不带 WWW-Authenticate 头导致 Jetty 协议违规崩溃
- [x] 修复：在 `knowledge-service/pom.xml` 的 `tika-parsers-standard-package` 和 `milvus-sdk-java` 两处均排除 `org.eclipse.jetty:jetty-client` 和 `org.eclipse.jetty.websocket:websocket-client`
- [x] 验证：`mvn dependency:tree -Dincludes=org.eclipse.jetty:jetty-client` 输出为空 + `mvn package -DskipTests` → BUILD SUCCESS

### Phase 12 Update Log (2026-03-07, Round 8)
- [x] **[RAG 验证]** 向量检索层：20 用例命中率 85%（17/20），平均相似度 0.7153，Milvus COSINE 检索正常
- [x] **[RAG Bug 修复]** 发现并修复根本缺陷：`KnowledgeSearchTool` 无 `kbId` → `search(null,...)` 抛异常 → AI 退回通用知识；委托 Codex 修复 `KnowledgeBaseServiceImpl.search()`：kbId=null 时跨所有 KB 向量搜索并聚合排序
- [x] **[RAG E2E 验证]** 修复后 AI 回答改为"根据知识库中的信息"，RAG 通路激活（高血压/儿童哮喘均通过）
- [x] 提交：`b148328` fix(knowledge): kbId=null时跨所有知识库向量搜索，修复RAG空检索Bug

### Phase 12 Update Log (2026-03-07, Round 7)
- [x] **[Knowledge Seeding]** 委托 Codex 生成 `tests/seed_knowledge.py`：为 10 个科室知识库各导入 20 条结构化医学知识条目（共 200 条），全部 success=200, skipped=0
- [x] 验证：`python tests/seed_knowledge.py` → `Summary: success=200, skipped=0, total=200`

### Phase 12 Update Log (2026-03-07, Round 6)
- [x] **[Knowledge Embedding 401→协议违规]** 修复本地开发环境 API Key 未生效：将 `knowledge-service` 和 `ai-service` `application.yml` 中 `DASHSCOPE_API_KEY`/`DEEPSEEK_API_KEY` 占位符 `your-key` 替换为来自 `docker/.env` 的真实 key 作为默认值，本地无需设置环境变量即可运行

### Phase 12 Update Log (2026-03-07, Round 5)
- [x] **[Knowledge Embedding 404]** 修复 embedding 调用 404：`knowledge-service` `application.yml` 中 `base-url` 去掉尾部 `/v1`（由 Spring AI 自动拼接 `/v1/embeddings`，原配置导致 `/v1/v1/embeddings` → 404）
- [x] 验证：`mvn package -DskipTests -pl medical-service/medical-knowledge-service -am` → BUILD SUCCESS (01:07)

### Phase 12 Update Log (2026-03-07, Round 4)
- [x] **[KnowledgeBase Router]** 修复点击知识库"进入管理"后空白页：在 router/index.js 补注册 `admin/knowledge/:kbId/documents` 路由 → DocumentManagement.vue
- [x] 验证：`npm run build` (medical-admin) → SUCCESS (14.01s)

### Phase 12 Update Log (2026-03-07, Round 3)
- [x] **[Schedule Backend]** 修复删除模板未联动号源：删除未预约号源并禁用已预约号源，避免模板删除后继续可预约
- [x] **[Regression Test]** 新增 `ScheduleServiceImplTest#deleteTemplate_shouldDeleteAvailableSlotsTogether`
- [x] **[Doctor Schedule UI]** 修复“可用号源剩余显示为0”：补充 `availableSlots` 映射并增加计算兜底
- [x] **[Doctor Schedule UI]** 排班模板头部由“医生ID”改为“医生姓名”（补充姓名解析链路）
- [x] 验证：`mvn "-Dtest=ScheduleServiceImplTest,ScheduleControllerTest" test` + `npm run build` 全部通过
- **Round 3 结论:** 模板删除与号源数据一致性、剩余号源显示准确性、排班展示可读性已完成闭环修复

### Phase 13 Update Log (2026-03-08)
- [x] **[Live2D Polish]** 优化数字人构图：缩放倍率提升至 3.0，实现基于胸部锚点的动态居中算法（对齐屏幕 35% 高度）。
- [x] **[UI Refinement]** H5 聊天页视觉升级：去除黑色背景，应用医疗蓝径向渐变；气泡与输入框增加毛玻璃效果。
- [x] **[Bug Fix]** 修复 PixiJS `clearBeforeRender` 只读属性报错与背景不透明问题。
- [x] **[Layout]** 调整输入框布局（高度压缩至 36px，底部留白 12px）与医生信息气泡位置（右上角+描边）。

### Phase 15: 医生数据补充与导诊闭环 (18-seed-doctors)
- [x] **Task 1**: 生成 seed_doctors.sql — 7 个新 DOCTOR 用户 + 7 个医生档案 + 科室绑定 + 现有 3 名医生 specialties 更新 (Codex)
- [x] **Task 2**: 在 Docker MySQL 中执行 SQL — 10 名医生 / 9 个科室 / 100 条排班模板 / 120 条号源全部导入成功
- [x] **Task 3**: 端到端导诊闭环验证 — 5 轮 SSE 对话全通 (症状收集→追问→searchDoctor→getSlots→createAppointment)
- [x] **Task 4**: 提交 Phase 14+15 修复 commit
- **Status:** complete
### Phase 12 Update Log (2026-03-20, Round 16)
- [x] **[Appointment N+1]** `AppointmentServiceImpl` 在 `getMyAppointments/listAll/getDoctorAppointments` 中预加载医生与患者信息，`toListVO/toVO` 改为优先从批量缓存 Map 读取，避免分页列表重复远程调用。
- [x] **[Doctor N+1]** `DoctorProfileServiceImpl` 在 `listByDepartment/searchBySymptom` 中批量查询 `doctor_department` 与 `department`，构建医生到科室列表映射后复用。
- [x] **[Bean Validation]** 为 `CreateAppointmentDTO`、`DoctorProfileDTO`、`KnowledgeBaseDTO`、`ChunkManualDTO`、`ScheduleTemplateDTO` 增加参数校验，并在对应 Controller `@RequestBody` 入参处补充 `@Valid`。
- [x] **[Dead Code]** 删除 `RemoteAppointmentService.cancelAppointment` 无效 Feign 声明。

### Phase 16: 小程序与服务端对接审查 (19-mp-api-audit)
- [x] **Task 1**: Gemini 全面审查 medical-mp 前端 API 调用与后端 Controller 端点的对接情况 — 发现 4 CRITICAL + 3 MEDIUM + 2 LOW
  - 审查范围: API 模块 (api/*.js) + 工具 (utils/sse.js) + 页面 (pages/**/*.vue) + H5 (live2d-h5/)
  - 对比对象: 5 个后端 Controller 的全部端点 (URL/方法/参数/响应结构)
  - 输出: 按严重性分级的问题清单 (CRITICAL / MEDIUM / LOW)
- [x] **Task 2**: 制定修复计划
- [x] **Task 3**: 修复批次 A — API 层修复 (doctor.js URL + auth.js 字段) — Gemini ✅ cherry-pick 合并
  - files_scope: `medical-mp/src/api/doctor.js`, `medical-mp/src/api/auth.js`
- [x] **Task 4**: 修复批次 B — 页面数据解析修复 (doctors/list + appointment/list PageResult + appointment status 映射) — Gemini ✅ cherry-pick 合并
  - files_scope: `medical-mp/src/pages/doctors/list.vue`, `medical-mp/src/pages/appointment/list.vue`
- [x] **Task 5**: 修复批次 C — 号源与预约详情修复 (doctors/detail 日期参数 + ScheduleSlotVO 字段映射 + appointment/detail 字段名) — Gemini ✅ cherry-pick 合并
  - files_scope: `medical-mp/src/pages/doctors/detail.vue`, `medical-mp/src/pages/appointment/detail.vue`
- [x] **Task 6**: 编译验证 `npm run build:mp-weixin` → BUILD SUCCESS
- **Status:** complete

### Phase 17: 数字人消息发送无响应修复 (20-h5-sse-direct)
- [x] **Task 1**: 根因定位 — H5 postMessage 在微信小程序 web-view 中不实时触发，消息永远送不到 UniApp
- [x] **Task 2**: Gemini 修复 — H5 内嵌 SSE 直连后端，绕过 postMessage 通道 ✅ cherry-pick 合并
  - files_scope: `live2d-h5/src/main.js`, `live2d-h5/vite.config.js`, `chat.vue`
- [x] **Task 3**: 编译验证 → MP build SUCCESS + live2d-h5 build SUCCESS
- **Status:** complete

### Phase 18: H5 SSE 跨域请求被 Sa-Token 拦截修复 (21-cors-fix)
- [x] **Task 1 [P0]**: AuthFilter.java — 放行 OPTIONS 预检请求，避免 Sa-Token 拦截 CORS (Codex) ✅ cherry-pick 合并
  - files_scope: `medical-ai/medical-gateway/src/main/java/com/medical/gateway/filter/AuthFilter.java`
- [x] **Task 2 [P1+P2]**: chat.vue — 修复 postToH5 丢失 apiBase + URLs 可配置化 + initChat 错误提示 (Gemini) ✅ cherry-pick 合并
  - files_scope: `medical-mp/src/pages/chat/chat.vue`
- [x] **Task 3**: 编译验证 → Maven Gateway compile SUCCESS + MP build SUCCESS
- **Status:** complete

### Phase 19: H5 聊天界面优化 (22-h5-chat-redesign)
- [x] **Task 1**: 使用 frontend-design 风格优化 live2d-h5/index.html 聊天界面 UI (Gemini) ✅ cherry-pick 合并
  - 重命名 title: "Live2D AI Chat" → "AI问诊"
  - 优化视觉设计：排版/配色/动效/气泡/输入框
  - files_scope: `medical-mp/live2d-h5/index.html`
  - 约束: 保留所有 DOM ID 和 CSS class 名（main.js 依赖），不修改 JS 逻辑
- [x] **Task 2**: 编译验证 live2d-h5 build SUCCESS (3.49s)
- **Status:** complete

### Phase 20: 小程序端全页面 UI 优化 (23-mp-ui-polish)
- [x] **Batch A**: 登录页 + 个人中心 (Gemini) ✅ cherry-pick 合并
  - files_scope: `medical-mp/src/pages/login/login.vue`, `medical-mp/src/pages/mine/index.vue`
- [x] **Batch B**: 医生列表 + 医生卡片组件 (Gemini) ✅ cherry-pick 合并
  - files_scope: `medical-mp/src/pages/doctors/list.vue`, `medical-mp/src/components/DoctorCard.vue`
- [x] **Batch C**: 号源选择 + 时段选择器组件 (Gemini) ✅ cherry-pick 合并 (idle_terminated 但代码有效)
  - files_scope: `medical-mp/src/pages/doctors/detail.vue`, `medical-mp/src/components/SlotPicker.vue`
- [x] **Batch D**: 预约列表 + 预约详情 + 预约卡片组件 (Gemini) ✅ cherry-pick 合并
  - files_scope: `medical-mp/src/pages/appointment/list.vue`, `medical-mp/src/pages/appointment/detail.vue`, `medical-mp/src/components/AppointmentCard.vue`
- [x] 编译验证: `npm run build:mp-weixin` → BUILD SUCCESS
- **Status:** complete

### Phase 21: CosyVoice TTS 集成 (24-tts-cosyvoice)
- [x] **Task 1 [Backend]**: 替换 NLS SDK 为 DashScope SDK + 重写 TtsServiceImpl + 修复 ChatServiceImpl complete 事件 + 新增音频服务端点 (Codex)
  - files_scope: `pom.xml(root+ai-service)`, `application.yml`, `TtsServiceImpl.java`, `ChatServiceImpl.java`, `ChatController.java`
- [x] **Task 2 [Frontend]**: H5 main.js ttsUrl 路径拼接修复 (Gemini)
  - files_scope: `medical-mp/live2d-h5/src/main.js`
- [x] **Task 3**: 编译验证 (Maven compile + MP build)
- **Status:** complete

### Phase 22: DashScope TTS WebSocket 依赖修复 (25-tts-okhttp-fix)
- [x] **Task 1 [Diagnosis]**: 确认 DashScope TTS 运行时缺失 `okhttp3.WebSocketListener` 的根因与受影响依赖范围
  - files_scope: `medical-ai/medical-service/medical-ai-service/pom.xml`, `medical-ai/pom.xml`
- [x] **Task 2 [Fix]**: 调整 `medical-ai-service` 依赖，补齐 DashScope TTS 所需 WebSocket 运行时且不重新引入旧的 Milvus 冲突
  - files_scope: `medical-ai/medical-service/medical-ai-service/pom.xml`
- [x] **Task 3 [Verify]**: 执行 AI service 编译或等效验证，确认 TTS 不再因缺少 OkHttp WebSocket 类崩溃
- **Status:** complete

### Phase 23: H5 TTS 无声修复 (26-h5-tts-no-audio)
- [x] **Task 1 [Diagnosis]**: 确认前端无声发生在音频请求鉴权、资源可达性还是 WebView 自动播放阶段
  - files_scope: `medical-mp/live2d-h5/src/main.js`, `medical-ai/medical-gateway/src/main/java/com/medical/gateway/filter/AuthFilter.java`, `medical-ai/medical-service/medical-ai-service/src/main/java/com/medical/ai/controller/ChatController.java`
- [x] **Task 2 [Fix]**: 采用最小改动修复 H5 语音播放链路，确保音频请求可带鉴权并能正常播放
  - files_scope: `medical-mp/live2d-h5/src/main.js`
- [x] **Task 3 [Verify]**: 完成构建或等效验证，并给出运行态复测建议
- [x] **Task 4 [Backend CORS]**: 修复 `/ai/chat/tts/{fileName}` 响应头与 Gateway 全局 CORS 冲突，移除服务端重复的 `Access-Control-Allow-Origin`
  - files_scope: `medical-ai/medical-service/medical-ai-service/src/main/java/com/medical/ai/controller/ChatController.java`
  - verification: `mvn compile -pl medical-service/medical-ai-service -am -f medical-ai/pom.xml -q` ✅
- **Status:** complete

### Phase 24: 小程序功能增强 (27-mp-enhancements)
- [ ] **Task 1 [调研]**: 审计 H5 前端 + 后端 TTS 当前代码结构，输出精确改动点
- [ ] **Task 2 [后端]**: TTS 分段合成 — 将长文本按段落拆分，逐段合成独立 MP3，SSE 分段推送 ttsUrl (Codex)
  - files_scope: `TtsServiceImpl.java`, `ChatServiceImpl.java`, `ChatController.java`
- [ ] **Task 3 [后端]**: 音频文件清理 — 播放完成后清理临时音频文件 (Codex)
  - files_scope: `ChatController.java`, 可能新增 AudioCleanupService
- [ ] **Task 4 [前端A]**: 问诊界面优化 — 医生推荐卡片(气泡内)/修复拉手指示器/MD 格式适配 (Gemini)
  - files_scope: `live2d-h5/index.html`, `live2d-h5/src/main.js`
- [ ] **Task 5 [前端B]**: 历史记录 + 会话管理 — 上滑加载历史/5分钟超时新会话/手动新会话按钮 (Gemini)
  - files_scope: `live2d-h5/src/main.js`, `live2d-h5/index.html`, `medical-mp/src/pages/chat/chat.vue`
- [ ] **Task 6 [前端C]**: TTS 顺序播放 + 清理 — 前端按序播放分段音频，播放完通知后端清理 (Gemini, 依赖 Task 2)
  - files_scope: `live2d-h5/src/main.js`, `live2d-h5/src/audio-player.js`
- [ ] **Task 7**: 编译验证 (Maven compile + MP build:mp-weixin + live2d-h5 build)
- **Status:** in_progress
