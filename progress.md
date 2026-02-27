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
| Where am I? | Phase 3 进行中，04-doctor-service 已完成，下一步 05-knowledge-service |
| Where am I going? | 05-knowledge → 06-ai → 07-appointment → 08-gateway → 09/10-frontend → 11-deploy |
| What's the goal? | 构建基于 Spring Cloud + Spring AI + RAG + AI Agents + Vue3 + UniApp 的 AI 数字人医疗小助手系统 |
| What have I learned? | 5微服务架构、技术栈选型、4个Agent设计、Live2D via web-view；每个 service 模块需要直接声明 lombok(provided) + spring-boot-starter-web；CCB ask 同步模式需要用统一 daemon (CCB_UNIFIED_ASKD 默认)，超时设置要长；@TableField 注解不能用于 method 上 |
| What have I done? | Phase 1-2 完成；Phase 3: 01-project-init + 02-common-modules + 03-user-service + 04-doctor-service 完成 |

---
*Update after completing each phase or encountering errors*
| 2026-02-28 00:06 | `mvn clean compile -pl medical-common/medical-common-core` compile failed: missing MyBatis-Plus annotations in `BaseEntity` (`TableField`/`TableLogic`/`FieldFill`) | 1 | Added `com.baomidou:mybatis-plus-annotation:${mybatis-plus.version}` to `medical-common-core/pom.xml`, then recompiled |
| 2026-02-28 00:18 | `mvn clean compile -f medical-ai/pom.xml` failed at `medical-common-security`: Lombok annotations not resolvable (`@Slf4j` / `log`) | 1 | Added `org.projectlombok:lombok` (`provided`) to `medical-common-security`, `medical-common-mybatis`, and `medical-common-redis` module POMs, then recompiled |
| 2026-02-28 00:19 | `mvn clean compile -f medical-ai/pom.xml` failed at `medical-common-mybatis`: `PaginationInnerInterceptor` class not found | 2 | Added `com.baomidou:mybatis-plus-extension` to `medical-common-mybatis/pom.xml`, then recompiled |
| 2026-02-28 00:20 | Added `mybatis-plus-extension` without explicit version caused POM validation failure | 3 | Set `mybatis-plus-extension` version to `${mybatis-plus.version}` in `medical-common-mybatis/pom.xml` |
| 2026-02-28 00:21 | `PaginationInnerInterceptor` still unresolved with `mybatis-plus-extension:3.5.9` | 4 | Confirmed class location via jar inspection; added `com.baomidou:mybatis-plus-jsqlparser:${mybatis-plus.version}` to `medical-common-mybatis/pom.xml` |
| 2026-02-28 01:34 | `mvn clean compile -f medical-ai/pom.xml` failed in `medical-doctor-service`: `ScheduleSlot#getAvailableSlots()` annotated with `@TableField` on method, causing annotation target error and cascading symbol errors | 1 | Removed the invalid method-level `@TableField` usage in `ScheduleSlot.java`, kept available slot calculation in VO/service mapping, then recompiled |
| 2026-02-28 01:40 | Re-run `mvn clean compile -f medical-ai/pom.xml` after doctor-service fixes | 2 | BUILD SUCCESS (all 18 modules compiled) |

