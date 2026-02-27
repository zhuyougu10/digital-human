# 11 - Docker Compose 全量部署 + 联调

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将所有服务容器化，Docker Compose 一键部署整个系统，完成端到端联调验证。

**Architecture:** 基础设施容器（MySQL/Redis/Nacos/Milvus）+ 业务服务容器（5个微服务+Gateway）+ 前端容器（Nginx）+ Live2D H5 容器

**前置依赖:** 01-10 全部完成

---

## Task 1: 为每个服务创建 Dockerfile

**模板（所有 Spring Boot 服务通用）：**

```dockerfile
# medical-service/medical-user-service/Dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=docker"]
```

每个服务各一份，端口对应修改。

---

## Task 2: Nginx 配置 + 前端 Dockerfile

**Files:**
- Create: `medical-admin/Dockerfile`
- Create: `medical-admin/nginx.conf`

```nginx
server {
    listen 80;
    server_name localhost;

    location / {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://gateway:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        
        # SSE 支持
        proxy_http_version 1.1;
        proxy_set_header Connection '';
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 120s;
    }
}
```

```dockerfile
FROM node:18-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

---

## Task 3: Live2D H5 Dockerfile

```dockerfile
FROM node:18-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8090
```

---

## Task 4: 完整 docker-compose.yml

**Files:**
- Update: `medical-ai/docker/docker-compose.yml`

在 Task 10 (01-project-init) 基础上追加所有业务服务：

```yaml
  # === 业务服务 ===
  gateway:
    build: ../medical-gateway
    container_name: medical-gateway
    ports:
      - "8080:8080"
    environment:
      NACOS_ADDR: nacos:8848
      REDIS_HOST: redis
    depends_on:
      - nacos
      - redis

  user-service:
    build: ../medical-service/medical-user-service
    container_name: medical-user-service
    environment:
      NACOS_ADDR: nacos:8848
      MYSQL_HOST: mysql
      REDIS_HOST: redis
      WX_APPID: ${WX_APPID}
      WX_SECRET: ${WX_SECRET}
    depends_on:
      - mysql
      - redis
      - nacos

  doctor-service:
    build: ../medical-service/medical-doctor-service
    container_name: medical-doctor-service
    environment:
      NACOS_ADDR: nacos:8848
      MYSQL_HOST: mysql
    depends_on:
      - mysql
      - nacos

  ai-service:
    build: ../medical-service/medical-ai-service
    container_name: medical-ai-service
    environment:
      NACOS_ADDR: nacos:8848
      MYSQL_HOST: mysql
      REDIS_HOST: redis
      DEEPSEEK_API_KEY: ${DEEPSEEK_API_KEY}
      DASHSCOPE_API_KEY: ${DASHSCOPE_API_KEY}
      MILVUS_HOST: milvus
      ALIYUN_AK_ID: ${ALIYUN_AK_ID}
      ALIYUN_AK_SECRET: ${ALIYUN_AK_SECRET}
      ALIYUN_TTS_APPKEY: ${ALIYUN_TTS_APPKEY}
    depends_on:
      - mysql
      - redis
      - nacos
      - milvus

  appointment-service:
    build: ../medical-service/medical-appointment-service
    container_name: medical-appointment-service
    environment:
      NACOS_ADDR: nacos:8848
      MYSQL_HOST: mysql
      REDIS_HOST: redis
    depends_on:
      - mysql
      - redis
      - nacos

  knowledge-service:
    build: ../medical-service/medical-knowledge-service
    container_name: medical-knowledge-service
    environment:
      NACOS_ADDR: nacos:8848
      MYSQL_HOST: mysql
      MILVUS_HOST: milvus
      DASHSCOPE_API_KEY: ${DASHSCOPE_API_KEY}
    depends_on:
      - mysql
      - nacos
      - milvus

  # === 前端 ===
  admin-web:
    build: ../../medical-admin
    container_name: medical-admin-web
    ports:
      - "80:80"
    depends_on:
      - gateway

  live2d-h5:
    build: ../../medical-mp/live2d-h5
    container_name: medical-live2d-h5
    ports:
      - "8090:8090"
```

---

## Task 5: 创建 .env 模板

**Files:**
- Create: `medical-ai/docker/.env.example`

```env
# 微信小程序
WX_APPID=your-appid
WX_SECRET=your-secret

# DeepSeek
DEEPSEEK_API_KEY=your-deepseek-key

# 通义千问 / DashScope
DASHSCOPE_API_KEY=your-dashscope-key

# 阿里云 TTS
ALIYUN_AK_ID=your-ak-id
ALIYUN_AK_SECRET=your-ak-secret
ALIYUN_TTS_APPKEY=your-tts-appkey
```

---

## Task 6: Maven 全量打包

```bash
mvn clean package -DskipTests -f medical-ai/pom.xml
```

Expected: 所有服务的 target/ 下生成 fat jar

---

## Task 7: Docker Compose 启动

```bash
cd medical-ai/docker
cp .env.example .env
# 编辑 .env 填入真实密钥

docker compose up -d --build
```

---

## Task 8: 端到端联调验证清单

| 测试项 | 验证方式 | 预期结果 |
|--------|---------|---------|
| Nacos 注册 | 访问 http://localhost:8848/nacos | 看到 5 个服务实例 |
| 管理端登录 | 访问 http://localhost, 用 admin/admin123 登录 | 登录成功，跳转看板 |
| 用户管理 | 管理端 → 用户管理 | 看到 admin 用户 |
| 科室管理 | 管理端 → 科室管理 | 看到 10 个初始科室 |
| 知识库创建 | 管理端 → 知识库 → 新建 → 上传文档 | 文档解析成功，分块数 >0 |
| 医生创建 | 管理端 → 医生管理 → 创建测试医生 | 创建成功，可编辑画像 |
| 排班配置 | 管理端 → 医生管理 → 排班 → 配置模板 → 生成号源 | 号源生成成功 |
| 小程序登录 | 微信开发者工具打开小程序 | 自动登录成功 |
| AI 问诊 | 小程序 → 开始问诊 → 描述症状 | AI 多轮对话 + 流式回复 |
| 导诊推荐 | 继续对话至推荐环节 | 展示医生推荐卡片 |
| 预约挂号 | 选择医生 → 选时间 → 确认 | 预约成功卡片展示 |
| TTS 播放 | AI 回复后 | 语音播放 + Live2D 口型动画 |
| 对话摘要 | 医生端 → 预约患者 → 查看摘要 | 显示结构化摘要 |
| 医生百科 | 医生端 → 百科助手 → 提问 | SSE 流式回复 |

---

## Task 9: Commit

```bash
git add .
git commit -m "feat(deploy): add Dockerfiles, complete docker-compose, e2e verification"
```

---

## 检查清单

- [ ] 所有服务 Dockerfile 创建
- [ ] Nginx 反向代理配置（含 SSE 支持）
- [ ] 完整 docker-compose.yml（基础设施 + 业务 + 前端）
- [ ] .env 模板
- [ ] Maven 全量打包成功
- [ ] Docker Compose 一键启动所有容器
- [ ] Nacos 控制台看到所有服务注册
- [ ] 端到端联调 12 项全部通过
