# AI 数字人医疗小助手 - 实施计划总览

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 按模块逐步构建完整的 AI 数字人医疗小助手系统

**设计文档:** `docs/plans/2026-02-27-medical-ai-assistant-design.md`

---

## 计划文件清单与执行顺序

| 序号 | 文件 | 模块 | 预估任务数 | 依赖 | 状态 |
|------|------|------|-----------|------|------|
| 01 | `01-project-init.md` | 项目初始化 + Maven 多模块 + Docker 基础设施 | ~15 | 无 | [ ] |
| 02 | `02-common-modules.md` | 公共模块 (core/security/mybatis/redis) | ~20 | 01 | [ ] |
| 03 | `03-user-service.md` | 用户服务 (注册/登录/角色/微信) | ~25 | 02 | [ ] |
| 04 | `04-doctor-service.md` | 医生服务 (画像/科室/排班) | ~20 | 02 | [ ] |
| 05 | `05-knowledge-service.md` | 知识库服务 (文档解析/Embedding/向量检索) | ~20 | 02 | [ ] |
| 06 | `06-ai-service.md` | AI 服务 (对话/4个Agent/RAG/TTS) | ~30 | 03,04,05 | [ ] |
| 07 | `07-appointment-service.md` | 预约服务 (号源/预约/排班) | ~20 | 03,04 | [ ] |
| 08 | `08-gateway.md` | Spring Cloud Gateway + 路由 + 鉴权 | ~10 | 02 | [ ] |
| 09 | `09-frontend-admin.md` | 管理端+医生端 (Vue3+ElementPlus) | ~35 | 03-08 | [ ] |
| 10 | `10-frontend-mp.md` | 小程序端 (UniApp+Live2D+TTS) | ~25 | 06,07 | [ ] |
| 11 | `11-docker-deploy.md` | Docker Compose 全量部署 + 联调 | ~10 | 01-10 | [ ] |

**总计：约 230 个微任务**

## 执行依赖图

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

## 建议执行策略

1. **串行核心路径**：01 → 02 → 03 → 06 → 10（患者端核心链路优先可演示）
2. **并行辅助模块**：04、05、07、08 可在 02 完成后并行开发
3. **前端最后**：09、10 在对应后端服务就绪后开发
4. **每完成一个模块**：运行全量测试 + 提交 + 更新此文件状态
