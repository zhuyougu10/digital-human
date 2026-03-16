# AI 数字人医疗小助手系统

<p align="center">
  <strong>面向导诊、医疗问答与在线预约场景的智能医疗助手系统</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.3.6-brightgreen" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Spring%20Cloud-2023.0.4-blue" alt="Spring Cloud">
  <img src="https://img.shields.io/badge/Spring%20AI-1.0.0--M5-orange" alt="Spring AI">
  <img src="https://img.shields.io/badge/Vue-3.5-brightgreen" alt="Vue">
  <img src="https://img.shields.io/badge/Java-17-blue" alt="Java">
  <img src="https://img.shields.io/badge/License-MIT-yellow" alt="License">
</p>

---

## 目录

- [项目简介](#项目简介)
- [核心功能](#核心功能)
- [技术架构](#技术架构)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [API 文档](#api-文档)
- [开发指南](#开发指南)
- [测试](#测试)
- [部署指南](#部署指南)
- [项目截图](#项目截图)
- [贡献指南](#贡献指南)
- [许可证](#许可证)
- [致谢](#致谢)

---

## 项目简介

AI 数字人医疗小助手系统是一个面向导诊、医疗问答与在线预约场景的毕业设计项目。系统采用 Spring Cloud 微服务架构，集成 Spring AI 与 RAG 技术，提供患者端、医生端、管理端三端协同能力。

### 核心亮点

- **AI 数字人导诊**：基于 Spring AI + Function Calling 的多轮问诊 Agent，支持症状收集、科室推荐、智能挂号
- **RAG 知识增强**：Milvus 向量数据库 + 智能路由，实现精准医疗知识检索
- **流式对话体验**：SSE 流式回复 + TTS 语音播报 + Live2D 口型联动
- **微服务架构**：5 个独立业务服务 + Gateway 统一网关，符合云原生设计原则

---

## 核心功能

### 患者端（UniApp 小程序 + Live2D）

| 功能模块 | 描述 |
|---------|------|
| AI 数字人导诊 | 多轮问诊、症状收集、科室推荐、智能挂号引导 |
| 医疗问答 | 基于 RAG 的常见疾病与健康知识问答 |
| 智能预约 | 医生检索、号源查询、在线挂号、取消预约 |
| 对话体验 | SSE 流式回复 + TTS 语音播报 + Live2D 口型联动 |

### 医生端（Vue3 Web）

| 功能模块 | 描述 |
|---------|------|
| 医生画像 | 职称、简介、擅长领域等信息管理 |
| 排班管理 | 模板维护、号源生成与查看 |
| 预约管理 | 查看预约患者列表与详情 |
| 患者摘要 | 查看 AI 自动生成的问诊摘要 |
| 百科助手 | 医学知识快速检索与问答 |

### 管理端（Vue3 Web）

| 功能模块 | 描述 |
|---------|------|
| 用户管理 | 用户列表、启停、角色分配 |
| 科室管理 | 科室信息维护 |
| 医生管理 | 医生档案维护与排班配置 |
| 知识库管理 | 知识库创建、文档上传、分块与检索 |
| 预约管理 | 运营视角查看业务数据 |
| 数据看板 | 系统配置与数据统计 |

---

## 技术架构

### 技术栈总览

| 层级 | 技术选型 |
|-----|---------|
| **后端框架** | Spring Boot 3.3.6、Spring Cloud 2023.0.4、Spring Cloud Alibaba 2023.0.3.2 |
| **AI 框架** | Spring AI 1.0.0-M5、DeepSeek API、通义千问 API |
| **安全认证** | Sa-Token 1.39.0（JWT + Redis 会话） |
| **数据访问** | MyBatis-Plus 3.5.9、Druid 连接池 |
| **数据库** | MySQL 8.0（5 个独立数据库） |
| **缓存** | Redis 7 |
| **向量数据库** | Milvus 2.4.5（含 etcd + minio） |
| **注册中心** | Nacos 2.3.2 |
| **前端框架** | Vue 3.5、Element Plus 2.13、Vite 7 |
| **小程序** | UniApp、TypeScript、Live2D、PixiJS v6 |
| **部署** | Docker Compose |

### 系统架构图

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

### AI Agent 设计

| Agent | 用途 | Function Calling 工具 |
|-------|------|----------------------|
| 导诊 Agent | 症状收集、科室推荐、挂号引导 | `searchDoctorBySymptom`、`getAvailableSlots`、`createAppointment` |
| 医疗问答 Agent | 面向患者的健康科普问答 | `searchKnowledge`、`getRelatedArticles` |
| 对话摘要 Agent | 会话结束后生成结构化摘要 | 无（Prompt 驱动） |
| 医生百科 Agent | 医生侧专业知识辅助 | `searchKnowledge`、`searchDrugInfo`、`searchGuideline` |

### 数据库设计

系统采用微服务数据隔离原则，每个服务独立数据库：

| 服务 | 数据库 | 核心表 |
|------|--------|--------|
| user-service | `medical_user` | `sys_user`, `sys_role`, `sys_user_role`, `wx_user_binding` |
| doctor-service | `medical_doctor` | `doctor_profile`, `department`, `doctor_department`, `schedule_template`, `schedule_slot` |
| ai-service | `medical_ai` | `chat_session`, `chat_message`, `conversation_summary` |
| appointment-service | `medical_appointment` | `appointment`, `appointment_slot` |
| knowledge-service | `medical_knowledge` | `knowledge_base`, `knowledge_document`, `knowledge_chunk` |

---

## 项目结构

```text
.
├── medical-ai/                      # 后端微服务工程
│   ├── medical-common/              # 公共模块
│   │   ├── medical-common-core/     # 通用工具、异常、响应体
│   │   ├── medical-common-security/ # Sa-Token 鉴权配置
│   │   ├── medical-common-mybatis/  # MyBatis-Plus 配置
│   │   └── medical-common-redis/    # Redis 缓存配置
│   ├── medical-api/                 # 服务间调用 DTO/Feign 契约
│   │   ├── medical-user-api/
│   │   ├── medical-doctor-api/
│   │   ├── medical-appointment-api/
│   │   └── medical-knowledge-api/
│   ├── medical-gateway/             # 网关服务 (:8080)
│   ├── medical-service/             # 业务微服务
│   │   ├── medical-user-service/    # 用户服务 (:8081)
│   │   ├── medical-doctor-service/  # 医生服务 (:8082)
│   │   ├── medical-ai-service/      # AI 服务 (:8083)
│   │   ├── medical-appointment-service/ # 预约服务 (:8084)
│   │   └── medical-knowledge-service/   # 知识库服务 (:8085)
│   └── docker/                      # Docker Compose 配置
│       ├── docker-compose.yml
│       ├── mysql/init.sql
│       └── .env.example
├── medical-admin/                   # 管理端 + 医生端前端
│   ├── src/
│   │   ├── api/                     # API 接口封装
│   │   ├── components/              # 公共组件
│   │   ├── router/                  # 路由配置
│   │   ├── stores/                  # Pinia 状态管理
│   │   └── views/                   # 页面组件
│   └── package.json
├── medical-mp/                      # 患者端小程序
│   ├── src/
│   │   ├── api/                     # API 接口
│   │   ├── components/              # 公共组件
│   │   ├── pages/                   # 页面
│   │   ├── stores/                  # 状态管理
│   │   └── utils/                   # 工具函数
│   ├── live2d-h5/                   # Live2D H5 项目
│   └── package.json
├── tests/                           # Python 集成测试
│   ├── config.py
│   ├── conftest.py
│   ├── test_01_auth.py
│   ├── test_02_user.py
│   └── ...
├── docs/                            # 项目文档
│   ├── api-reference.md             # API 接口文档
│   ├── database-design.md           # 数据库设计
│   ├── deployment-guide.md          # 部署手册
│   ├── user-guide.md                # 用户指南
│   └── plans/                       # 详细设计文档
├── AGENTS.md                        # AI 编码助手规则
├── findings.md                      # 研发过程关键发现
├── progress.md                      # 过程记录
└── task_plan.md                     # 任务计划
```

---

## 快速开始

### 环境要求

| 软件 | 版本要求 |
|-----|---------|
| Docker | 24+ |
| Docker Compose | v2+ |
| JDK | 17+（本地开发） |
| Maven | 3.8+（本地开发） |
| Node.js | 18+（本地开发） |

### 一键部署（推荐）

```bash
# 1. 克隆项目
git clone <your-repo-url>
cd 数字人/medical-ai/docker

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env 文件，填写必要的 API Key

# 3. 启动所有服务
docker compose up -d --build

# 4. 查看容器状态
docker compose ps
```

预期结果：14 个容器均为 `Up` 状态。

### 访问地址

| 服务 | 地址 | 说明 |
|-----|------|------|
| 管理端/医生端 | http://localhost | Vue3 Web 应用 |
| 网关 API | http://localhost:8080 | RESTful API 入口 |
| Nacos 控制台 | http://localhost:8848/nacos | 服务注册与配置管理 |
| MinIO Console | http://localhost:9001 | Milvus 依赖存储 |
| Live2D H5 | http://localhost:8090 | 数字人展示页面 |

### 默认账号

| 角色 | 用户名 | 密码 |
|-----|--------|------|
| 管理员 | admin | admin123 |
| 医生 | doctor | doctor123 |
| 患者 | patient | patient123 |

---

## 配置说明

### 环境变量

创建 `.env` 文件并配置以下变量：

```bash
# AI 服务配置（必填）
DEEPSEEK_API_KEY=your-deepseek-api-key
DASHSCOPE_API_KEY=your-dashscope-api-key

# TTS 语音服务（可选）
ALIYUN_AK_ID=your-aliyun-ak-id
ALIYUN_AK_SECRET=your-aliyun-ak-secret
ALIYUN_TTS_APPKEY=your-tts-appkey

# 微信小程序（患者端必填）
WX_APPID=your-wechat-appid
WX_SECRET=your-wechat-secret

# 基础设施（可选，使用默认值）
MYSQL_PASSWORD=root123
REDIS_HOST=redis
MILVUS_HOST=milvus
NACOS_ADDR=nacos:8848
```

### Gateway 路由配置

| 路由 ID | Path 规则 | 目标服务 | 说明 |
|---------|----------|---------|------|
| `ai-service-sse` | `/api/ai/chat/send` | ai-service | SSE 专用路由（超时 120s） |
| `user-service` | `/api/user/**` | user-service | 用户与认证 |
| `doctor-service` | `/api/doctor/**` | doctor-service | 医生/科室/排班 |
| `ai-service` | `/api/ai/**` | ai-service | AI 对话/摘要/百科 |
| `appointment-service` | `/api/appointment/**` | appointment-service | 预约挂号 |
| `knowledge-service` | `/api/knowledge/**` | knowledge-service | 知识库/RAG |

---

## API 文档

### 认证机制

系统使用 Sa-Token 进行认证，采用 Bearer Token 方式：

```bash
# 登录获取 Token
curl -X POST http://localhost:8080/api/user/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# 响应示例
{
  "code": 200,
  "msg": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "userInfo": { "id": 1, "username": "admin", "roles": ["ADMIN"] }
  }
}

# 携带 Token 访问受保护接口
curl http://localhost:8080/api/user/user/list \
  -H "Authorization: Bearer <token>"
```

### 核心 API 端点

详细 API 文档请参阅 [API Reference](docs/api-reference.md)。

#### 用户服务

| 方法 | 路径 | 说明 |
|-----|------|------|
| POST | `/api/user/auth/login` | 用户登录 |
| POST | `/api/user/auth/register` | 用户注册 |
| GET | `/api/user/user/list` | 用户列表（管理员） |
| PUT | `/api/user/user/{id}/toggle-status` | 启停用户 |

#### 医生服务

| 方法 | 路径 | 说明 |
|-----|------|------|
| GET | `/api/doctor/doctor/list` | 医生列表 |
| GET | `/api/doctor/doctor/my-profile` | 当前医生档案 |
| GET | `/api/doctor/schedule/slots` | 可用号源 |

#### AI 服务

| 方法 | 路径 | 说明 |
|-----|------|------|
| POST | `/api/ai/chat/session` | 创建会话 |
| POST | `/api/ai/chat/send` | SSE 流式对话 |
| GET | `/api/ai/chat/sessions` | 会话列表 |

#### 预约服务

| 方法 | 路径 | 说明 |
|-----|------|------|
| POST | `/api/appointment/appointment` | 创建预约 |
| GET | `/api/appointment/appointment/my` | 我的预约 |
| PUT | `/api/appointment/appointment/{id}/cancel` | 取消预约 |

#### 知识库服务

| 方法 | 路径 | 说明 |
|-----|------|------|
| GET | `/api/knowledge/kb/list` | 知识库列表 |
| POST | `/api/knowledge/kb` | 创建知识库 |
| POST | `/api/knowledge/kb/{id}/upload` | 上传文档 |
| POST | `/api/knowledge/kb/search` | 向量检索 |

---

## 开发指南

### 本地开发环境

#### 后端开发

```bash
# 1. 启动基础设施
cd medical-ai/docker
docker compose up -d mysql redis nacos milvus-etcd milvus-minio milvus

# 2. 编译项目
cd medical-ai
mvn clean compile

# 3. 启动单个服务（示例）
cd medical-service/medical-user-service
mvn spring-boot:run
```

#### 前端开发

```bash
# 管理端
cd medical-admin
npm install
npm run dev    # 开发服务器 :5173

# 小程序端
cd medical-mp
npm install
npm run dev:mp-weixin
```

### 代码规范

#### Java 代码规范

- 包结构：`com.medical.<service>.<layer>`（controller → service → mapper → domain）
- 命名约定：实体无后缀，DTO 使用 `*DTO`，VO 使用 `*VO`
- 响应包装：统一返回 `R<T>`，使用 `R.ok()` / `R.fail()`
- 异常处理：抛出 `BusinessException`，由 `GlobalExceptionHandler` 统一处理

```java
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;

    @PostMapping("/session")
    public R<ChatSessionVO> createSession(@RequestBody @Validated CreateSessionDTO dto) {
        Long userId = SecurityUtil.getUserId();
        return R.ok(chatService.createSession(userId, dto));
    }
}
```

#### Vue 代码规范

- 使用 `<script setup>` 语法
- API 调用统一通过 `src/api/` 模块
- 状态管理使用 Pinia Composition API

```javascript
import { getUserList, toggleUserStatus } from '@/api/user'

const loadData = async () => {
  const res = await getUserList(queryParams)
  tableData.value = res.data.records
}
```

---

## 测试

### 单元测试

```bash
# 运行所有单元测试
cd medical-ai
mvn test

# 运行单个服务测试
mvn test -pl medical-service/medical-user-service

# 运行单个测试类
mvn test -pl medical-service/medical-user-service -Dtest=AuthControllerTest
```

### 集成测试（Python）

```bash
# 安装依赖
pip install -r tests/requirements.txt

# 运行所有集成测试
pytest tests/ -v

# 生成 HTML 报告
pytest tests/ --html=report.html --self-contained-html

# 运行单个测试文件
pytest tests/test_01_auth.py -v
```

### 测试覆盖

- **单元测试**：109 个测试用例，覆盖全部 55 个 REST 端点
- **集成测试**：60 个端到端测试用例，覆盖核心业务流程

---

## 部署指南

详细部署说明请参阅 [部署手册](docs/deployment-guide.md)。

### Docker Compose 部署

```bash
# 生产环境部署
cd medical-ai/docker
docker compose -f docker-compose.yml up -d --build

# 查看日志
docker compose logs -f ai-service

# 停止服务
docker compose down
```

### 容器清单

| 容器名称 | 镜像 | 端口 | 说明 |
|---------|------|------|------|
| medical-mysql | mysql:8.0 | 3306 | MySQL 数据库 |
| medical-redis | redis:7-alpine | 6379 | Redis 缓存 |
| medical-nacos | nacos/nacos-server:v2.3.2 | 8848 | 注册/配置中心 |
| medical-milvus | milvusdb/milvus:v2.4.5 | 19530 | 向量数据库 |
| medical-gateway | 自建 | 8080 | API 网关 |
| medical-user-service | 自建 | 8081 | 用户服务 |
| medical-doctor-service | 自建 | 8082 | 医生服务 |
| medical-ai-service | 自建 | 8083 | AI 服务 |
| medical-appointment-service | 自建 | 8084 | 预约服务 |
| medical-knowledge-service | 自建 | 8085 | 知识库服务 |
| medical-admin-web | 自建 | 80 | 管理端前端 |
| medical-live2d-h5 | 自建 | 8090 | Live2D H5 |

---

## 项目截图

### 患者端

| 首页 | 导诊对话 | 医生列表 |
|:---:|:---:|:---:|
| *待补充* | *待补充* | *待补充* |

### 医生端

| 个人档案 | 排班管理 | 百科助手 |
|:---:|:---:|:---:|
| *待补充* | *待补充* | *待补充* |

### 管理端

| 用户管理 | 知识库管理 | 数据看板 |
|:---:|:---:|:---:|
| *待补充* | *待补充* | *待补充* |

---

## 贡献指南

欢迎提交 Issue 和 Pull Request！

### 提交规范

```
feat: 新功能
fix: 修复 Bug
docs: 文档更新
style: 代码格式调整
refactor: 重构
test: 测试相关
chore: 构建/工具链相关
```

### 开发流程

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'feat: add amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 创建 Pull Request

---

## 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件。

---

## 致谢

- [Spring AI](https://spring.io/projects/spring-ai) - AI 应用开发框架
- [DeepSeek](https://www.deepseek.com/) - 大语言模型服务
- [Milvus](https://milvus.io/) - 向量数据库
- [Element Plus](https://element-plus.org/) - Vue3 UI 组件库
- [UniApp](https://uniapp.dcloud.io/) - 跨平台应用开发框架
- [Live2D](https://www.live2d.com/) - 虚拟角色技术

---

<p align="center">
  Made with ❤️ for Graduation Design
</p>
