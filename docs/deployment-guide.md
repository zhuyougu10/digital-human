# 部署手册（Deployment Guide）

## 1. 环境要求

### 基础软件
- JDK 17+
- Maven 3.8+
- Node.js 18+
- pnpm / npm（前端开发使用）
- Docker 24+
- Docker Compose v2+

### 机器建议配置
- CPU: 4 核及以上
- 内存: 8GB 及以上（Milvus + Nacos + 多服务并行运行）
- 磁盘: 20GB 可用空间

## 2. 一键 Docker 部署（推荐）

### Step 1: 获取代码
```bash
git clone <your-repo-url>
cd 数字人/medical-ai/docker
```

### Step 2: 配置环境变量
```bash
cp .env.example .env
```
按需填写 AI Key、微信小程序配置等敏感信息。

### Step 3: 启动服务
```bash
docker compose up -d --build
```

### Step 4: 检查容器状态
```bash
docker compose ps
```
预期：14 个容器均为 `Up`。

### Step 5: 核心入口验证
- 管理端/医生端：`http://localhost`
- 网关：`http://localhost:8080`
- Nacos：`http://localhost:8848/nacos`
- Live2D H5：`http://localhost:8090`

## 3. 环境变量说明

说明：当前仓库 `medical-ai/docker/.env.example` 实际包含 16 个变量（与早期计划中的 27 个变量版本不同）。以下按当前文件逐项说明。

| 变量名 | 示例值 | 作用 | 是否必填 |
|---|---|---|---|
| `WX_APPID` | `your-appid` | 微信小程序 AppID | 患者端微信登录必填 |
| `WX_SECRET` | `your-secret` | 微信小程序密钥 | 患者端微信登录必填 |
| `DEEPSEEK_API_KEY` | `your-deepseek-key` | DeepSeek 大模型调用密钥 | AI 对话必填 |
| `DASHSCOPE_API_KEY` | `your-dashscope-key` | 通义千问/Embedding 调用密钥 | RAG/向量化建议必填 |
| `ALIYUN_AK_ID` | `your-ak-id` | 阿里云访问密钥 ID | TTS 语音必填 |
| `ALIYUN_AK_SECRET` | `your-ak-secret` | 阿里云访问密钥 Secret | TTS 语音必填 |
| `ALIYUN_TTS_APPKEY` | `your-tts-appkey` | 阿里云语音合成 AppKey | TTS 语音必填 |
| `MYSQL_USER` | `root` | MySQL 用户名 | 否 |
| `MYSQL_PASSWORD` | `root123` | MySQL 密码 | 否 |
| `NACOS_ADDR` | `nacos:8848` | Nacos 服务地址 | 否 |
| `MYSQL_HOST` | `mysql` | MySQL 主机名 | 否 |
| `REDIS_HOST` | `redis` | Redis 主机名 | 否 |
| `REDIS_PORT` | `6379` | Redis 端口 | 否 |
| `REDIS_PASSWORD` | `` | Redis 密码 | 有密码时必填 |
| `MILVUS_HOST` | `milvus` | Milvus 主机名 | 否 |
| `MILVUS_PORT` | `19530` | Milvus gRPC 端口 | 否 |

补充：`docker-compose.yml` 中 MySQL 健康检查还引用 `MYSQL_ROOT_PASSWORD`，未单独配置时会回退为 `MYSQL_PASSWORD`/默认值。

## 4. 本地开发环境搭建

### 4.1 后端构建
```bash
cd /mnt/d/project/数字人/medical-ai
mvn clean compile
```

### 4.2 启动基础设施
```bash
cd docker
docker compose up -d mysql redis nacos milvus-etcd milvus-minio milvus
```

### 4.3 启动后端服务（示例）
分别在各模块执行：
```bash
mvn spring-boot:run
```
推荐顺序：
1. `medical-user-service`
2. `medical-doctor-service`
3. `medical-appointment-service`
4. `medical-knowledge-service`
5. `medical-ai-service`
6. `medical-gateway`

### 4.4 启动前端
```bash
cd /mnt/d/project/数字人/medical-admin
npm install
npm run dev
```

```bash
cd /mnt/d/project/数字人/medical-mp
npm install
npm run dev:h5
```

## 5. 服务端口总览（14 容器）

| 容器名 | 对外端口 | 作用 |
|---|---|---|
| `medical-mysql` | `3306` | 关系型数据存储 |
| `medical-redis` | `6379` | 缓存/会话共享 |
| `medical-nacos` | `8848`, `9848` | 注册中心/配置中心 |
| `medical-milvus-etcd` | 无 | Milvus 元数据依赖 |
| `medical-milvus-minio` | `9001` | Milvus 对象存储 Console |
| `medical-milvus` | `19530`, `9091` | 向量数据库服务 |
| `medical-gateway` | `8080` | API 统一入口 |
| `medical-user-service` | 无（容器内 8081） | 用户/认证服务 |
| `medical-doctor-service` | 无（容器内 8082） | 医生/科室/排班服务 |
| `medical-ai-service` | 无（容器内 8083） | AI 会话/摘要/TTS 服务 |
| `medical-appointment-service` | 无（容器内 8084） | 预约挂号服务 |
| `medical-knowledge-service` | 无（容器内 8085） | 知识库/RAG 服务 |
| `medical-admin-web` | `80` | 管理端/医生端前端 |
| `medical-live2d-h5` | `8090` | Live2D H5 资源服务 |

## 6. 健康检查与联通验证

### 6.1 容器与健康状态
```bash
cd /mnt/d/project/数字人/medical-ai/docker
docker compose ps
```

### 6.2 Nacos 注册检查
```bash
curl "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=medical-user-service"
```

### 6.3 API 基础连通
```bash
curl -X POST http://localhost:8080/api/user/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### 6.4 SSE 接口联通（示例）
```bash
curl -N -X POST http://localhost:8080/api/ai/chat/send \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"sessionId":1,"message":"你好"}'
```

## 7. 常见问题排障（9+）

| 问题 | 现象 | 根因 | 解决方案 |
|---|---|---|---|
| ai-service 启动报 `NoSuchFieldError: Companion` | 容器 Exited(1) | Milvus SDK 传递 `okhttp` 与 Kotlin 运行时冲突 | 排除 Milvus SDK 的 okhttp 传递依赖，重建镜像 |
| `init.sql` 只建库不建表 | 服务启动后表缺失 | 初始化脚本不完整 | 合并 5 个服务 DDL 到 `medical-ai/docker/mysql/init.sql` |
| `admin123` 无法登录 | 登录总是密码错误 | DDL 中 BCrypt 哈希不匹配 | 替换为正确哈希并重新初始化 |
| 服务连接 Redis 失败 | 日志指向 `localhost:6379` | `application.yml` 未配置 Redis 环境变量 | 为 5 个服务补充 `spring.data.redis.*` 配置 |
| Gateway 返回 401 token 无效 | 登录成功但访问业务接口 401 | Gateway/服务 token-name 不一致导致 Redis key 前缀不一致 | 统一 Sa-Token 配置（`Authorization` + `Bearer`） |
| Spring Boot 参数名丢失 | Controller 参数绑定异常 | 编译未开启 `-parameters` | 父 POM 配置 `maven-compiler-plugin` + `parameters=true` |
| DDL 缺少审计字段 | MyBatis 插入/更新异常 | 表结构与 `BaseEntity` 不一致 | 补齐 `create_by/update_by` 等字段 |
| 非 user 服务 `@SaCheckRole` 全部 403 | 已登录但鉴权失败 | 缺通用角色查询实现 | 在 common-security 增加基于 Feign 的 `StpInterfaceImpl` |
| doctor/knowledge 服务 Feign 不生效 | 启动后依赖注入失败 | 缺 `@EnableFeignClients` 与相关依赖 | 补充注解与 loadbalancer 依赖 |
| Milvus 检索无结果 | 检索返回空 | 集合名/向量写入不一致 | 检查 `collection_name`、`milvus_id` 和 embedding 维度一致性 |

## 8. 回滚与重置（开发环境）

清空容器和数据卷并重建：
```bash
cd /mnt/d/project/数字人/medical-ai/docker
docker compose down -v
docker compose up -d --build
```

注意：`-v` 会删除 MySQL/Redis/Milvus 数据。
