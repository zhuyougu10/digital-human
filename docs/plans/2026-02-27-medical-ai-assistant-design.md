# AI 数字人医疗小助手系统 - 架构设计文档

> 日期：2026-02-27
> 状态：已确认
> 项目类型：毕业设计

## 1. 项目概述

基于 Spring Cloud + Spring AI + RAG + AI Agents + Vue3 + UniApp 的 AI 数字人医疗小助手系统，面向医疗咨询与导诊场景。

### 1.1 三端定位

| 端 | 技术 | 核心功能 |
|----|------|----------|
| 患者端 | UniApp 小程序 + Live2D | 数字人医生对话、病情咨询、导诊、一键挂号预约 |
| 医生端 | Vue3 网页 | 画像维护、查看预约患者对话摘要、百科助手 |
| 管理端 | Vue3 网页 | 用户管理、知识库管理、科室管理、系统配置 |

## 2. 技术栈

| 层次 | 技术 | 版本 |
|------|------|------|
| JDK | OpenJDK | 17 |
| 后端框架 | Spring Boot | 3.3.x |
| 微服务 | Spring Cloud | 2023.0.x |
| 微服务增强 | Spring Cloud Alibaba | 2023.0.x |
| AI 框架 | Spring AI | 1.0.0+ |
| LLM | DeepSeek API (主力) + 通义千问 API (备选/Embedding) | - |
| 注册/配置中心 | Nacos | 2.3.x |
| 网关 | Spring Cloud Gateway | - |
| 服务间调用 | OpenFeign + LoadBalancer | - |
| ORM | MyBatis-Plus | 3.5.x |
| 业务数据库 | MySQL | 8.x |
| 缓存 | Redis | 7.x |
| 向量数据库 | Milvus Lite / ChromaDB | - |
| TTS 语音合成 | 阿里云智能语音 | - |
| 患者端 | UniApp (Vue3) + pixi-live2d-display | - |
| 网页端 | Vue3 + Vite + ElementPlus + Pinia | 3.4+ |
| 构建工具 | Node.js | 18+ |
| 部署 | Docker Compose | - |

## 3. 总体架构

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
    └────┬─────┘└────┬─────┘└────┬─────┘└────┬─────┘└────┬─────┘
         │           │           │           │           │
         ▼           ▼           ▼           ▼           ▼
    ┌─────────────────────┐ ┌─────────┐ ┌──────────────────┐
    │       MySQL         │ │  Redis  │ │  Milvus / Chroma │
    └─────────────────────┘ └─────────┘ └──────────────────┘
                    ┌─────────────────────┐
                    │    Nacos (注册+配置) │
                    └─────────────────────┘
```

## 4. 微服务详细设计

### 4.1 user-service（用户服务）

**职责**：用户注册登录、角色权限管理

| 功能 | 说明 |
|------|------|
| 微信小程序登录 | wx.login → code → 后端换 openid → JWT |
| 网页端登录 | 账号密码 → JWT（医生/管理员） |
| 角色体系 | PATIENT（患者）、DOCTOR（医生）、ADMIN（管理员） |
| 用户 CRUD | 管理端用户列表、禁用/启用、角色分配 |

**核心表**：`sys_user`, `sys_role`, `sys_user_role`, `wx_user_binding`

### 4.2 doctor-service（医生服务）

**职责**：医生画像、科室管理、排班配置

| 功能 | 说明 |
|------|------|
| 医生画像 | 姓名、头像、职称、擅长、简介、主治方向 |
| 科室管理 | 科室 CRUD、科室-医生关联 |
| 排班管理 | 医生周排班模板、号源数量配置 |

**核心表**：`doctor_profile`, `department`, `doctor_department`, `schedule_template`, `schedule_slot`

### 4.3 ai-service（AI 服务 -- 系统核心）

**职责**：对话管理、4 个 AI Agent、RAG 检索、TTS 调用

| Agent | System Prompt 要点 | 绑定工具 (Function Calling) |
|-------|-------------------|---------------------------|
| 导诊 Agent | 医疗分诊专家，通过多轮问答收集症状 | `searchDoctorBySymptom`, `getAvailableSlots`, `createAppointment` |
| 医疗问答 Agent | 医学科普助手，基于 RAG 检索回答 | `searchKnowledge`, `getRelatedArticles` |
| 对话摘要 Agent | 后台异步触发，对患者就诊前对话生成结构化摘要 | 无工具，纯 Prompt 驱动 |
| 医生百科 Agent | 面向医生的专业查询助手 | `searchKnowledge`, `searchDrugInfo`, `searchGuideline` |

**对话流程（SSE 流式）**：
```
用户消息 → Gateway → ai-service
  → 判断意图(导诊/问答/闲聊)
  → 选择对应 Agent
  → Agent 执行(可能调用外部工具/RAG)
  → SSE 流式返回文字
  → 同时异步调用 TTS 生成音频URL
  → 前端接收文字流 + 音频URL → 驱动 Live2D 口型
```

**核心表**：`chat_session`, `chat_message`, `conversation_summary`

### 4.4 appointment-service（预约服务）

**职责**：预约挂号全流程

| 功能 | 说明 |
|------|------|
| 号源查询 | 按科室/医生/日期查询可用号源 |
| 创建预约 | 锁定号源 → 生成预约单 |
| 预约管理 | 患者查看我的预约、取消预约 |
| 医生接诊 | 医生查看今日预约列表 + 对话摘要 |

**核心表**：`appointment`, `appointment_slot`

### 4.5 knowledge-service（知识库服务）

**职责**：医疗知识库管理、文档解析、向量化

| 功能 | 说明 |
|------|------|
| 知识库 CRUD | 创建/管理多个知识库 |
| 文档上传 | 支持 PDF/Word/TXT，后台异步解析+分块+Embedding |
| 向量检索 | 接收 ai-service 的语义查询，返回相关文档片段 |
| 知识条目管理 | 手动添加/编辑/删除知识条目 |

**核心表**：`knowledge_base`, `knowledge_document`, `knowledge_chunk`
**向量存储**：Milvus/Chroma collection per knowledge_base

## 5. 核心业务流程

### 5.1 导诊到挂号完整流程

```
患者描述症状
  → 导诊 Agent 多轮问诊收集信息
  → Agent 调用 searchDoctorBySymptom 匹配科室+医生
  → 前端展示医生推荐卡片
  → 患者选择医生
  → Agent 调用 getAvailableSlots 查询号源
  → 前端展示可选时间
  → 患者选择时间
  → Agent 调用 createAppointment 创建预约
  → 预约成功，异步触发对话摘要 Agent
```

### 5.2 对话摘要 → 医生接诊

```
预约成功后异步触发
  → 对话摘要 Agent 读取完整对话记录
  → 生成结构化摘要（主诉/伴随症状/持续时间/严重程度/既往史/AI初步判断）
  → 存入 conversation_summary 表
  → 医生端"预约患者"页面展示摘要
```

## 6. 前端架构

### 6.1 管理端 + 医生端（Vue3 + ElementPlus）

```
medical-admin/
├── src/views/
│   ├── admin/              # 管理端
│   │   ├── UserManage       # 用户管理
│   │   ├── KnowledgeBase    # 知识库管理
│   │   ├── DepartmentManage # 科室管理
│   │   ├── DoctorManage     # 医生管理
│   │   ├── AppointmentManage# 预约管理
│   │   ├── ChatManage       # 对话管理
│   │   ├── SystemConfig     # 系统配置
│   │   └── Dashboard        # 数据看板
│   └── doctor/             # 医生端
│       ├── Profile          # 画像维护
│       ├── Schedule         # 排班查看
│       ├── Appointments     # 预约患者列表
│       ├── PatientSummary   # 就诊前对话总结
│       └── Encyclopedia     # 百科助手
```

### 6.2 患者小程序（UniApp + Live2D）

```
medical-mp/
├── pages/
│   ├── index/              # 首页（数字人入口）
│   ├── chat/               # 对话页（Live2D + 聊天）
│   ├── doctors/            # 医生列表
│   ├── appointment/        # 预约详情
│   └── mine/               # 个人中心
└── components/
    ├── Live2DPlayer/       # Live2D 渲染 (web-view 内嵌 H5)
    ├── ChatPanel/          # 聊天面板
    └── TtsPlayer/          # TTS 音频播放器
```

### 6.3 Live2D 数字人方案

使用 `<web-view>` 内嵌 H5 页面实现：
- H5 页面加载 pixi.js + pixi-live2d-display
- H5 与小程序通过 postMessage 双向通信
- 阿里云 TTS 返回音频 URL → H5 播放音频 + 解析音量 → 驱动 Live2D mouth 参数

## 7. 管理端完整功能清单

| 模块 | 功能 | 说明 |
|------|------|------|
| 用户管理 | 用户列表、角色分配、禁用/启用 | 管理患者/医生/管理员 |
| 知识库管理 | 知识库CRUD、文档上传、分块预览、重建索引 | 管理 RAG 知识源 |
| 科室管理 | 科室CRUD、科室排序 | 维护医院科室结构 |
| 医生管理 | 医生CRUD、关联科室、审核画像 | 管理员侧医生管理 |
| 预约管理 | 预约列表、预约统计、异常处理 | 全局查看/处理预约 |
| 对话管理 | 对话记录查看、对话统计 | 审计和质量监控 |
| 系统配置 | Agent 提示词配置、模型参数、TTS 配置 | 运维级别配置 |
| 数据看板 | 今日预约数、活跃用户、AI对话量、知识库命中率 | 首页 Dashboard |

## 8. 后端项目结构

```
medical-ai/
├── pom.xml                              # 父 POM
├── medical-common/
│   ├── medical-common-core/             # 通用工具、异常、响应体
│   ├── medical-common-security/         # JWT + Spring Security
│   ├── medical-common-mybatis/          # MyBatis-Plus 配置
│   └── medical-common-redis/            # Redis 配置
├── medical-gateway/                     # Spring Cloud Gateway
├── medical-service/
│   ├── medical-user-service/
│   ├── medical-doctor-service/
│   ├── medical-ai-service/
│   ├── medical-appointment-service/
│   └── medical-knowledge-service/
├── medical-api/                         # Feign 接口定义
│   ├── medical-user-api/
│   ├── medical-doctor-api/
│   ├── medical-appointment-api/
│   └── medical-knowledge-api/
└── docker/
    ├── docker-compose.yml
    ├── mysql/init.sql
    ├── nacos/
    └── nginx/
```

## 9. Docker Compose 部署

| 服务 | 端口 | 说明 |
|------|------|------|
| mysql | 3306 | MySQL 8.x |
| redis | 6379 | Redis 7.x |
| nacos | 8848 | 注册+配置中心 |
| milvus | 19530 | 向量数据库 |
| gateway | 8080 | 对外唯一入口 |
| user-service | 8081 | |
| doctor-service | 8082 | |
| ai-service | 8083 | |
| appointment-service | 8084 | |
| knowledge-service | 8085 | |
| admin-web | 80 | Nginx 托管前端 |
| live2d-h5 | 8090 | Live2D H5 页面 |
