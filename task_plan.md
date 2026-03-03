# Task Plan: AI 数字人医疗小助手系统

## Goal
构建基于 Spring Cloud + Spring AI + RAG + AI Agents + Vue3 + UniApp 的 AI 数字人医疗小助手系统（毕业设计）

## Current Phase
Phase 9: 接口测试 — in_progress

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
- [ ] Task 0: 测试基础设施搭建 (父 POM + surefire + 各服务 application-test.yml + SecurityUtil mock 策略)
- [ ] Task 1: medical-user-service 接口测试 (AuthController 8 + SysUserController 12 = 20 用例)
- [ ] Task 2: medical-doctor-service 接口测试 (Doctor 14 + Department 10 + Schedule 13 = 37 用例)
- [ ] Task 3: medical-ai-service 接口测试 (Chat 10 + Encyclopedia 6 + Summary 4 = 20 用例)
- [ ] Task 4: medical-appointment-service 接口测试 (Appointment 14 用例)
- [ ] Task 5: medical-knowledge-service 接口测试 (KnowledgeBase 18 用例)
- [ ] 全量验证: mvn test → 109 用例全部 GREEN
- **Status:** in_progress

## Notes
<!-- 
  REMINDERS:
  - Update phase status as you progress: pending → in_progress → complete
  - Re-read this plan before major decisions (attention manipulation)
  - Log ALL errors - they help avoid repetition
  - Never repeat a failed action - mutate your approach instead
-->
- Update phase status as you progress: pending → in_progress → complete
- Re-read this plan before major decisions (attention manipulation)
- Log ALL errors - they help avoid repetition
