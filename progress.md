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
| Where am I? | Phase 3 进行中，02-common-modules 已完成，下一步 03-user-service |
| Where am I going? | 03-user → 06-ai → 10-frontend-mp (核心路径) |
| What's the goal? | 构建基于 Spring Cloud + Spring AI + RAG + AI Agents + Vue3 + UniApp 的 AI 数字人医疗小助手系统 |
| What have I learned? | 5微服务架构、技术栈选型、4个Agent设计、Live2D via web-view 方案 → 见 findings.md |
| What have I done? | Phase 1 需求确认 + 架构设计文档；Phase 2 拆分 11 模块 ~230 微任务计划 → 见上方日志 |

---
*Update after completing each phase or encountering errors*
| 2026-02-28 00:06 | `mvn clean compile -pl medical-common/medical-common-core` compile failed: missing MyBatis-Plus annotations in `BaseEntity` (`TableField`/`TableLogic`/`FieldFill`) | 1 | Added `com.baomidou:mybatis-plus-annotation:${mybatis-plus.version}` to `medical-common-core/pom.xml`, then recompiled |
| 2026-02-28 00:18 | `mvn clean compile -f medical-ai/pom.xml` failed at `medical-common-security`: Lombok annotations not resolvable (`@Slf4j` / `log`) | 1 | Added `org.projectlombok:lombok` (`provided`) to `medical-common-security`, `medical-common-mybatis`, and `medical-common-redis` module POMs, then recompiled |
| 2026-02-28 00:19 | `mvn clean compile -f medical-ai/pom.xml` failed at `medical-common-mybatis`: `PaginationInnerInterceptor` class not found | 2 | Added `com.baomidou:mybatis-plus-extension` to `medical-common-mybatis/pom.xml`, then recompiled |
| 2026-02-28 00:20 | Added `mybatis-plus-extension` without explicit version caused POM validation failure | 3 | Set `mybatis-plus-extension` version to `${mybatis-plus.version}` in `medical-common-mybatis/pom.xml` |
| 2026-02-28 00:21 | `PaginationInnerInterceptor` still unresolved with `mybatis-plus-extension:3.5.9` | 4 | Confirmed class location via jar inspection; added `com.baomidou:mybatis-plus-jsqlparser:${mybatis-plus.version}` to `medical-common-mybatis/pom.xml` |
