# Task Plan: AI 数字人医疗小助手系统

## Goal
构建基于 Spring Cloud + Spring AI + RAG + AI Agents + Vue3 + UniApp 的 AI 数字人医疗小助手系统（毕业设计）

## Current Phase
Phase 12: Feature Enhancements & Bug Fixes — complete

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

## Phase 8: Final Review & Polishing
- [x] Final E2E Check
- [x] Documentation Audit
- [x] Final Cleanup


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

## Key Questions
<!-- 
  WHAT: Important questions you need to answer during the task.
  WHY: These guide your research and decision-making. Answer them as you go.
  EXAMPLE: 
    1. Should tasks persist between sessions? (Yes - need file storage)
    2. What format for storing tasks? (JSON file)
-->
1. [Question to answer]
2. [Question to answer]

## Decisions Made
<!-- 
  WHAT: Technical and design decisions you've made, with the reasoning behind them.
  WHY: You'll forget why you made choices. This table helps you remember and justify decisions.
  WHEN: Update whenever you make a significant choice (technology, approach, structure).
  EXAMPLE:
    | Use JSON for storage | Simple, human-readable, built-in Python support |
-->
| Decision | Rationale |
|----------|-----------|
|          |           |

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
- [x] **[User Management]** Add "Create User" functionality (Backend API + Frontend Dialog)
- **Status:** complete

### Phase 11 Bug Fix Round 1 — 最终结果
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
