# AI 数字人医疗小助手系统

## 项目简介
AI 数字人医疗小助手系统是一个面向导诊、医疗问答与在线预约场景的毕业设计项目，采用微服务架构，提供患者端、医生端、管理端三端协同能力。

## 功能特性

### 患者端（UniApp 小程序 + Live2D）
- AI 数字人导诊：多轮问诊、症状收集、科室推荐
- 医疗问答：基于 RAG 的常见疾病与健康知识问答
- 智能预约：医生检索、号源查询、在线挂号、取消预约
- 对话体验：SSE 流式回复 + TTS 语音播报 + Live2D 口型联动

### 医生端（Vue3 Web）
- 医生画像维护：职称、简介、擅长领域等信息管理
- 排班管理：模板维护、号源生成与查看
- 预约管理：查看预约患者列表与详情
- 患者对话摘要：查看 AI 自动生成的问诊摘要
- 百科助手：医学知识快速检索与问答

### 管理端（Vue3 Web）
- 用户管理：用户列表、启停、角色分配
- 科室管理：科室信息维护
- 知识库管理：知识库、文档上传、分块与检索
- 预约与会话管理：运营视角查看业务数据
- 系统配置与数据看板

## 技术栈
- 后端：Spring Boot 3.3.6、Spring Cloud 2023.0.4、Spring Cloud Alibaba、Spring AI 1.0.0-M5、Sa-Token、MyBatis-Plus、OpenFeign
- 数据与中间件：MySQL 8、Redis 7、Nacos 2.3.2、Milvus 2.4.5（含 etcd + minio）
- 前端：Vue3、Element Plus、Vite
- 小程序：UniApp、Live2D、pixi.js、SSE
- 部署：Docker Compose

## 系统架构图

```text
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

## 项目结构

```text
.
├── medical-ai/                      # 后端微服务工程
│   ├── medical-common/              # 公共模块（core/security/mybatis/redis）
│   ├── medical-api/                 # 服务间调用 DTO/Feign 契约
│   ├── medical-gateway/             # 网关服务
│   ├── medical-service/             # 5 个业务微服务
│   └── docker/                      # Docker Compose 与基础设施初始化
├── medical-admin/                   # 管理端 + 医生端前端
├── medical-mp/                      # 患者端小程序 + live2d-h5
├── docs/                            # 项目文档
├── findings.md                      # 研发过程关键发现
├── progress.md                      # 过程记录
└── task_plan.md                     # 任务计划
```

## 快速启动（Docker Compose）

### 前置条件
- Docker 24+
- Docker Compose v2+

### 启动步骤
1. 进入部署目录：
   ```bash
   cd medical-ai/docker
   ```
2. 复制环境变量模板并按需填写：
   ```bash
   cp .env.example .env
   ```
3. 一键启动所有容器：
   ```bash
   docker compose up -d --build
   ```
4. 查看状态：
   ```bash
   docker compose ps
   ```

### 关键访问地址
- 管理端/医生端：`http://localhost`
- 网关 API：`http://localhost:8080`
- Nacos 控制台：`http://localhost:8848/nacos`
- MinIO Console（Milvus 依赖）：`http://localhost:9001`
- Live2D H5：`http://localhost:8090`

## 核心业务流
1. 患者进入小程序发起导诊会话
2. 导诊 Agent 多轮收集症状并判断建议科室
3. 系统查询可选医生与可预约号源
4. 患者确认医生与时段后创建预约
5. 会话结束后异步生成对话摘要
6. 医生端查看预约信息与患者摘要，完成接诊准备

## AI Agent 设计
| Agent | 用途 | Function Calling 工具 |
|---|---|---|
| 导诊 Agent | 症状收集、科室推荐、挂号引导 | `searchDoctorBySymptom`、`getAvailableSlots`、`createAppointment` |
| 医疗问答 Agent | 面向患者的健康科普问答 | `searchKnowledge`、`getRelatedArticles` |
| 对话摘要 Agent | 会话结束后生成结构化摘要 | 无（Prompt 驱动） |
| 医生百科 Agent | 医生侧专业知识辅助 | `searchKnowledge`、`searchDrugInfo`、`searchGuideline` |

## 项目截图
<!-- 截图占位 -->

- 患者端首页截图：<!-- 截图占位 -->
- 导诊对话页面截图：<!-- 截图占位 -->
- 医生端排班页面截图：<!-- 截图占位 -->
- 管理端知识库页面截图：<!-- 截图占位 -->
