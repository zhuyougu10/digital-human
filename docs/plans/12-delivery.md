# Phase 5: Delivery — Documentation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为毕业设计项目生成完整的项目文档，包括 README、部署手册、数据库设计、API 参考和用户指南。

**Architecture:** 基于已完成的 Phase 1-4 产出（设计文档 + 代码 + 12/12 联调通过），生成 5 个文档 + 最终提交。

**Tech Stack:** Markdown 文档

---

## Task 1: Project README.md

**委派:** Codex (后端专家)

**Files:**
- Create: `README.md` (项目根目录)

**要求:**
- 中文撰写
- 包含以下章节:
  1. **项目简介** — AI 数字人医疗小助手系统，一句话描述 + 核心功能
  2. **功能特性** — 分 患者端/医生端/管理端 列出核心功能
  3. **技术栈** — 后端(Spring Boot 3.3.6 / Spring Cloud 2023.0.4 / Spring AI 1.0.0-M5 / Sa-Token / MyBatis-Plus / Milvus / MySQL / Redis / Nacos)、前端(Vue3 + ElementPlus + Vite)、小程序(UniApp + Live2D + pixi.js)、部署(Docker Compose)
  4. **系统架构图** — 复用 findings.md 中的 ASCII 架构图
  5. **项目结构** — 精简的目录树 (后端模块/前端/小程序)
  6. **快速启动** — 前置条件 + Docker Compose 一键部署步骤 (参照 .env.example)
  7. **核心业务流** — 患者导诊→预约 流程描述
  8. **AI Agent 设计** — 4 个 Agent 的用途和 Function Calling 工具绑定
  9. **项目截图** — 预留 `<!-- 截图占位 -->` 标记

**参考文件:**
- `findings.md` (架构、决策、Agent 设计)
- `docs/plans/2026-02-27-medical-ai-assistant-design.md` (完整设计文档)
- `medical-ai/docker/.env.example` (环境变量)
- `medical-ai/docker/docker-compose.yml` (容器编排)

**验证:** README.md 生成且包含上述所有章节

---

## Task 2: Deployment Guide (docs/deployment-guide.md)

**委派:** Codex

**Files:**
- Create: `docs/deployment-guide.md`

**要求:**
- 中文撰写
- 包含以下章节:
  1. **环境要求** — JDK 17+, Maven 3.8+, Node.js 18+, Docker 24+, Docker Compose v2
  2. **一键 Docker 部署 (推荐)** — 详细步骤: clone → 配置 .env → docker compose up -d → 验证
  3. **环境变量说明** — 逐项解释 .env.example 中的 27 个变量
  4. **本地开发环境搭建** — Maven 编译 → 启动基础设施(MySQL/Redis/Nacos/Milvus) → 逐服务启动 → 前端 dev server
  5. **服务端口总览** — 表格: 14 个容器的名称/端口/用途
  6. **健康检查** — Nacos 注册验证、API 联通测试命令
  7. **常见问题排障** — 整理 Phase 3 中遇到的 9+ 个 error 及其 resolution

**参考文件:**
- `medical-ai/docker/docker-compose.yml`
- `medical-ai/docker/.env.example`
- `task_plan.md` → Errors Encountered 表
- `findings.md` → 11-docker-deploy 联调阻塞分析

**验证:** deployment-guide.md 生成且步骤完整

---

## Task 3: Database Design (docs/database-design.md)

**委派:** Codex

**Files:**
- Create: `docs/database-design.md`

**要求:**
- 中文撰写
- 包含以下章节:
  1. **数据库概览** — 5 个独立数据库 (medical_user / medical_doctor / medical_ai / medical_appointment / medical_knowledge)
  2. **ER 关系描述** — 每个库的表间关系文字描述 + 跨服务引用关系
  3. **表结构详情** — 每张表的字段名、类型、约束、说明 (共约 13 张核心表)
  4. **初始数据** — admin 用户、10 个科室等初始化数据说明
  5. **向量存储** — Milvus collection 结构 (dimension=1536, COSINE metric)

**参考文件:**
- `medical-ai/docker/mysql/init.sql` (所有 DDL + 初始数据)
- `findings.md` → Core Database Tables 表格

**验证:** database-design.md 生成且覆盖全部 13 张表

---

## Task 4: API Reference (docs/api-reference.md)

**委派:** Codex

**Files:**
- Create: `docs/api-reference.md`

**要求:**
- 中文撰写
- 包含以下章节:
  1. **认证机制** — Sa-Token Bearer Token 认证流程 (登录获取 token → Header 携带 → Gateway 校验)
  2. **Gateway 路由总览** — 6 条路由 (含 SSE 专用路由) 表格
  3. **用户服务 API** — /api/user/** (登录/注册/微信登录/用户 CRUD/角色管理)
  4. **医生服务 API** — /api/doctor/** (科室/医生画像/排班/号源)
  5. **AI 服务 API** — /api/ai/** (SSE 对话/会话管理/摘要/百科)
  6. **预约服务 API** — /api/appointment/** (创建/取消/查询/统计)
  7. **知识库服务 API** — /api/knowledge/** (知识库 CRUD/文档上传/向量检索)
  8. **通用响应格式** — R 对象结构 {code, msg, data}、ErrorCode 枚举
  9. **SSE 流式接口** — /api/ai/chat/send 的请求/响应格式说明

**参考文件:**
- 各 Controller 源文件 (medical-ai/medical-service/medical-*-service/src/.../controller/)
- `medical-ai/medical-gateway/src/main/resources/application.yml` (路由配置)
- `medical-ai/medical-common/medical-common-core/src/.../domain/R.java`
- `medical-ai/medical-common/medical-common-core/src/.../exception/ErrorCode.java`

**验证:** api-reference.md 生成且覆盖 5 个服务的全部 Controller 端点

---

## Task 5: User Guide (docs/user-guide.md)

**委派:** Gemini (前端专家)

**Files:**
- Create: `docs/user-guide.md`

**要求:**
- 中文撰写
- 包含以下章节:
  1. **管理端使用指南**:
     - 登录 (admin/admin123)
     - 数据看板 (Dashboard 统计概览)
     - 用户管理 (列表/启用禁用/角色分配)
     - 科室管理 (CRUD/启用禁用)
     - 医生管理 (列表/查看详情)
     - 知识库管理 (创建知识库/上传文档/查看分块)
     - 会话管理 (查看患者对话记录)
     - 预约管理 (查看/取消预约)
     - 系统配置
  2. **医生端使用指南**:
     - 个人画像维护 (Profile 编辑)
     - 排班管理 (模板创建/号源生成)
     - 预约查看 (今日/全部预约)
     - 患者对话摘要 (AI 生成的摘要查看)
     - 百科助手 (ChatPanel 智能问答)
  3. **患者端 (小程序) 使用指南**:
     - 首页导航
     - 智能导诊对话 (数字人 + SSE 流式 + TTS 语音)
     - 医生列表 → 详情 → 选号源 → 预约
     - 预约查看 (列表/详情/取消)
     - 个人中心

**参考文件:**
- `medical-admin/src/views/` (所有 .vue 页面文件)
- `medical-admin/src/router/index.js` (路由定义)
- `medical-mp/src/pages/` (所有小程序页面)
- `medical-mp/src/pages.json` (页面配置)
- `findings.md` → 09-frontend-admin 审计结果 + 10-frontend-mp 完成状态

**验证:** user-guide.md 生成且覆盖三端所有功能页面

---

## Task 6: Final Commit + Cleanup

**委派:** OpenCode (自身)

**Files:**
- Update: `medical-admin/README.md` (替换默认 Vue 模板内容，简短指向根 README)
- Update: `task_plan.md` / `progress.md` (Phase 5 标记完成)

**步骤:**
1. 审查 5 个文档质量
2. 清理模板 README
3. git add + commit: `docs: add project documentation (README, deployment, database, API, user guide)`
4. 更新 progress.md

**验证:** git log 显示提交成功
