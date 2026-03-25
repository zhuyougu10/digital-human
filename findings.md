# Findings & Decisions

> 项目：AI 数字人医疗小助手系统（毕业设计）
> 最后更新：2026-03-25

## Requirements

- **三端架构**：患者端 (UniApp+Live2D+TTS) / 医生端 (Vue3) / 管理端 (Vue3)
- **核心业务流**：患者描述症状 → 导诊 Agent 多轮问诊 → 匹配科室+医生 → 查号源 → 创建预约 → 异步摘要 → 医生接诊
- **4 个 AI Agent**：导诊 / 医疗问答 / 对话摘要 / 医生百科
- **技术栈**：Spring Cloud 微服务 + Spring AI + RAG + SSE 流式 + Live2D + Docker Compose

## Architecture

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
             │   Spring Cloud Gateway     │ Sa-Token + CORS + 路由
             └─────────────┬─────────────┘
                           │ OpenFeign / LoadBalancer
      ┌──────────┬─────────┼──────────┬───────────┐
      ▼          ▼         ▼          ▼           ▼
 user:8081  doctor:8082  ai:8083  appoint:8084  knowledge:8085
      │          │         │          │           │
      ▼          ▼         ▼          ▼           ▼
 MySQL:3306    Redis:6379   Milvus:19530    Nacos:8848
```

## Technical Decisions

| Decision | Rationale |
|----------|-----------|
| 5 微服务 (user/doctor/ai/appointment/knowledge) | 平衡毕设规模与微服务实践 |
| Spring Boot 3.3.6 + Cloud 2023.0.4 + AI 1.0.0-M5 | 版本兼容 |
| DeepSeek (主力) + 通义千问 (Embedding) | 性价比 + 中文能力 |
| Sa-Token 替代 Spring Security | 轻量 + Redis 会话共享 |
| MyBatis-Plus 3.5.9 + Druid | CRUD 简化 + 连接池监控 |
| Milvus 向量数据库 | Spring AI 集成 + 分布式 |
| 全量 H5 聊天 UI (方案 A) | 解决 web-view 遮挡 |
| PixiJS v6 | pixi-live2d-display@0.4.0 兼容性 |
| H5 直连后端 SSE | 绕过 postMessage 不实时 |
| SSE complete 与 TTS 解耦 | 防止 TTS 阻塞文字流 |

## Database Tables

| 服务 | 核心表 |
|------|--------|
| user | sys_user, sys_role, sys_user_role, wx_user_binding |
| doctor | doctor_profile, department, doctor_department, schedule_template, schedule_slot |
| ai | chat_session, chat_message, conversation_summary |
| appointment | appointment |
| knowledge | knowledge_base, knowledge_document, knowledge_chunk + Milvus collection |

## AI Agent Design

| Agent | 用途 | 工具 (Function Calling) |
|-------|------|------------------------|
| 导诊 | 多轮问答收集症状 | searchDoctorBySymptom, getAvailableSlots, createAppointment |
| 医疗问答 | 科普 RAG | searchKnowledge, getRelatedArticles |
| 对话摘要 | 后台异步 | 无工具，纯 Prompt |
| 医生百科 | 专业查询 | searchKnowledge, searchDrugInfo, searchGuideline |

## Gateway Routes

| Path | Service | Port |
|------|---------|------|
| /api/user/** | medical-user-service | 8081 |
| /api/doctor/** | medical-doctor-service | 8082 |
| /api/ai/** | medical-ai-service | 8083 |
| /api/appointment/** | medical-appointment-service | 8084 |
| /api/knowledge/** | medical-knowledge-service | 8085 |

## Architectural Constraints (易踩坑)

1. Sa-Token `token-name` 同时作 HTTP header 名和 Redis key 前缀，Gateway 与服务必须一致
2. Milvus SDK 必须排除 `okhttp` 传递依赖 (Kotlin classpath 冲突)
3. Tika + Milvus 必须排除 `jetty-client` (覆盖 Spring HTTP 客户端)
4. Spring AI M5 需手动注入 `FunctionCallbackResolver` 到 `OpenAiChatModel`
5. 每服务独立数据库，跨服务调用走 Feign (medical-api/)
6. H2 test profile: `@ActiveProfiles("test")` + `TestAiApplication` (排除 DataSource/Redis)
7. `chatModel.stream(prompt)` 后必须 `.publishOn(Schedulers.boundedElastic())` 防止 tool call 阻塞 Netty IO
8. DashScope TTS 需要 OkHttp WebSocket，不能排除 dashscope-sdk 的 okhttp 传递依赖

## Resources

- 架构设计文档：`docs/plans/2026-02-27-medical-ai-assistant-design.md`
- 实施计划 (12 个)：`docs/plans/00-overview.md` ~ `docs/plans/11-docker-deploy.md`
- 交付文档：`README.md`, `docs/deployment-guide.md`, `docs/database-design.md`, `docs/api-reference.md`, `docs/user-guide.md`

## Workspace Catchup (2026-03-23)

- `medical-ai/medical-common/medical-common-core/src/main/java/com/medical/common/core/handler/GlobalExceptionHandler.java`: 新增 `AsyncRequestNotUsableException` 静默处理，避免 SSE 客户端主动断开时进入通用异常日志
- `medical-mp/live2d-h5/index.html`: `marked` CDN 从浮动最新版改为固定 `4.3.0`，降低上游版本漂移导致的前端渲染风险
- 工作区存在 3 个 `hs_err_pid*.log` JVM 崩溃日志，尚未形成根因结论
- `git diff --stat` 复核：除规划文件 `task_plan.md`、`findings.md`、`progress.md` 外，当前仍有 1 个删除项 `语音合成.md`，需在后续交付前确认该文档是否应从仓库移除

## Workspace Catchup (2026-03-24)

- 使用仓库内 `.opencode/skills/planning-with-files/scripts/session-catchup.py` 重新执行 catchup；脚本本次无额外输出，未暴露新的未同步上下文提示
- `git diff --stat` 显示除规划文件外，当前工作区仍存在 3 类业务改动：`.gitignore` 新增忽略项、`medical-ai/medical-common/medical-common-core/src/main/java/com/medical/common/core/handler/GlobalExceptionHandler.java` 的 SSE 断连异常静默处理、`medical-mp/live2d-h5/index.html` 的 `marked` CDN 固定版本
- 删除项 `语音合成.md` 仍在 diff 中，说明该删除尚未被新一轮交付决策吸收，需要后续明确保留删除还是恢复文档
- Bash 环境不能直接执行 PowerShell 形式的 catchup 命令，也不能可靠使用用户目录中的 Windows 路径；在当前仓库下直接调用 repo-local 脚本最稳妥

## Workspace Catchup (2026-03-25)

- 再次使用仓库内 `.opencode/skills/planning-with-files/scripts/session-catchup.py` 执行 catchup；脚本仍无输出，可视为当前无额外待恢复的 session 文本上下文
- `git status --short` / `git diff --stat` 复核后确认，除规划文件外，当前工作区仍包含 `.gitignore`、`medical-ai/medical-common/medical-common-core/src/main/java/com/medical/common/core/handler/GlobalExceptionHandler.java`、`medical-mp/live2d-h5/index.html` 三处已修改项，以及删除项 `语音合成.md`
- 新补充发现：`docs/superpowers/` 目录当前为未跟踪状态，包含 `docs/superpowers/specs/2026-03-23-sentinel-protection-design.md` 与 `docs/superpowers/plans/2026-03-23-sentinel-protection-implementation.md` 两份 Sentinel 相关文档
- 在当前 Bash 工具环境中，直接使用用户提供的 PowerShell 风格命令或 `C:/Users/...`、`/mnt/c/...` 用户目录脚本路径都会失败；最稳定路径仍是仓库内 repo-local 脚本

## Workspace Catchup (2026-03-25, current session)

- 本次会话再次执行 repo-local `session-catchup.py`，脚本依旧无输出，说明没有新的 session 文本残留待恢复
- `git diff --stat` 显示当前业务/文档改动格局未变化：`.gitignore`、`GlobalExceptionHandler.java`、`medical-mp/live2d-h5/index.html`、规划文件，以及删除项 `语音合成.md`
- `git status --short` 继续显示未跟踪目录 `docs/superpowers/`，说明 Sentinel/Seata 设计文档仍未被纳入版本控制决策

## Metrics Evidence Draft (2026-03-23)

- 已实现 3 端：患者端、医生端、管理端；见 `README.md`
- 核心模块可按 15 个产品功能模块 / 6 个后端服务模块 / 11 个实施模块三种口径表述；见 `README.md` 与 `docs/plans/00-overview.md`
- 核心接口可按 67 个文档接口或 70 个 controller 实际映射表述；见 `docs/api-reference.md` 与各服务 controller
- 登录、医生查询、知识检索、创建会话、SSE 对话、预约挂号、导诊闭环均有现成 pytest 或验证脚本覆盖，主证据位于 `tests/test_01_auth.py`、`tests/test_04_doctor.py`、`tests/test_06_knowledge.py`、`tests/test_07_appointment.py`、`tests/test_08_chat.py`、`tests/test_09_e2e_flow.py`
- 当前 `docker compose ps` 返回空服务列表，说明尚无已启动容器可直接读取运行态指标，后续需启动环境或使用现有运行实例测量

## Runtime Metrics (2026-03-23)

- Docker Compose 实测启动：`docker compose up -d` 返回耗时 `39.49s`；到 `/api/user/auth/login` 成功可用耗时 `103.77s`
- 实际运行容器/服务数：Compose `14` 个服务全部启动；其中业务服务 `5` 个（user/doctor/ai/appointment/knowledge），网关 `1` 个，前端容器 `2` 个，基础设施 `6` 个
- 网关路由数：`6` 条（其中 `/api/ai/chat/send` 单独一条 + user/doctor/ai/appointment/knowledge 5 条）
- 核心接口时延：登录 `89.23ms avg / 96.76ms p95`，医生列表 `66.35ms / 74.64ms`，创建会话 `25.43ms / 35.62ms`，预约创建 `77.75ms / 88.82ms`
- SSE 时延：首包 `1664.74ms avg / 1972.67ms p95`，完整回复 `6066.46ms avg / 6969.38ms p95`，5/5 均收到 `complete`
- 预约闭环：导诊→会话→SSE→查医生→查号源→挂号→详情→取消，实测 `5/5` 成功
- 稳定性：对登录/医生列表/创建会话/挂号创建取消组成的核心非流式请求序列做 `20/50/100` 次顺序压测，错误数 `0/0/0`，超时数 `0/0/0`
- 知识检索当前不可用：`/knowledge/kb/search` 在 `topK=3`、口腔科知识库 `kbId=11` 查询下返回 `code=5003, msg="404 - "`，单次耗时约 `1014.72ms`，命中数 `0`，无相关片段返回；10 次采样平均 `391.55ms`、P95 `833.4ms`

## Metrics Conclusion (2026-03-23)

- **系统规模**：3 端应用、5 个核心业务微服务 + 1 网关、14 个 Compose 容器、6 条网关路由，可支撑毕设答辩中的系统复杂度说明
- **性能表现**：非流式核心接口保持在 `25ms~90ms avg` 区间，SSE 首包约 `1.66s avg`、完整回复约 `6.07s avg`，符合大模型流式问答场景预期
- **业务可用性**：导诊→挂号闭环 `5/5` 成功，核心非流式序列压测 `20/50/100` 次均 `0 error / 0 timeout`
- **当前风险点**：知识检索链路仍存在 `5003 / 404 -` 故障，说明外部 embedding / 检索依赖尚未稳定，不应宣称 RAG 检索已通过运行态验收

## Knowledge Retrieval 5003/404 Debug (2026-03-23)

- `/knowledge/kb/search` 的 controller 直接调用 `knowledgeBaseService.search(...)`，搜索链路会先执行 query embedding；见 `medical-ai/medical-service/medical-knowledge-service/src/main/java/com/medical/knowledge/controller/KnowledgeBaseController.java` 与 `medical-ai/medical-service/medical-knowledge-service/src/main/java/com/medical/knowledge/service/impl/KnowledgeBaseServiceImpl.java`
- `EmbeddingServiceImpl` 在 `embeddingModel.embedForResponse(...)` 异常时直接抛出 `BusinessException(ErrorCode.EMBEDDING_ERROR, e.getMessage())`，随后被 `GlobalExceptionHandler` 原样返回，所以前端会看到裸 `404 - `；见 `medical-ai/medical-service/medical-knowledge-service/src/main/java/com/medical/knowledge/service/impl/EmbeddingServiceImpl.java:21`、`medical-ai/medical-common/medical-common-core/src/main/java/com/medical/common/core/handler/GlobalExceptionHandler.java:26`
- 高置信度根因：knowledge-service 的 `spring.ai.openai.base-url` 配成了 `https://dashscope.aliyuncs.com/compatible-mode`，缺少 `/v1`；见 `medical-ai/medical-service/medical-knowledge-service/src/main/resources/application.yml:32`
- 同仓库对照证据：设计文档与 `medical-ai-service` 中对应 DashScope 兼容地址都使用 `https://dashscope.aliyuncs.com/compatible-mode/v1`；见 `docs/plans/05-knowledge-service.md:160`、`medical-ai/medical-service/medical-ai-service/src/main/resources/application.yml:45`
- 测试掩盖现象：`tests/test_06_knowledge.py` 将 `5003` 视为可接受结果，因此 `/knowledge/kb/search` 并不会因 embedding 故障而在集成测试中硬失败；见 `tests/test_06_knowledge.py:75`、`tests/test_06_knowledge.py:97`

## Knowledge Retrieval Fix Verification (2026-03-23)

- 已实施最小修复：`medical-ai/medical-service/medical-knowledge-service/src/main/resources/application.yml:32` 改为 `https://dashscope.aliyuncs.com/compatible-mode/v1`
- 静态验证：knowledge-service 与 `medical-ai/medical-service/medical-ai-service/src/main/resources/application.yml:45` 的 DashScope `base-url` 已一致，均为 `.../compatible-mode/v1`
- 自动化验证：执行 `mvn test -pl medical-service/medical-knowledge-service -f medical-ai/pom.xml -Dtest=KnowledgeBaseControllerTest`，结果 `BUILD SUCCESS`，`Tests run: 18, Failures: 0, Errors: 0, Skipped: 0`
- 容器验证：重新 `mvn clean package -DskipTests -pl medical-service/medical-knowledge-service -am` 后重建 `knowledge-service` 容器，容器内 `/app/app.jar` 解压得到的 `BOOT-INF/classes/application.yml` 已包含 `base-url: https://dashscope.aliyuncs.com/compatible-mode/v1`
- 运行态补充证据：`medical-knowledge-service` 最新启动日志提示 `medical-knowledge-service.yml` 的 Nacos 配置为空，因此当前容器实际使用的是镜像内打包的 `application.yml`
- 剩余风险：若运行环境通过 Nacos 覆盖了 `spring.ai.openai.base-url` 且仍为旧值，线上仍可能复现；部署侧需同步检查实际生效配置

## Sentinel Protection Design (2026-03-23)

- 仓库当前未接入 Sentinel 依赖或规则；`medical-ai` 下各服务和网关配置中未发现现成的 Sentinel starter、FlowRule、DegradeRule 或 blockHandler 接入痕迹
- 第一批最需要保护的链路按优先级为：`/api/ai/chat/send` SSE 主链路、`TtsService.synthesize`、`/api/knowledge/kb/search` 与 `/api/knowledge/kb/inner/search`、`/api/appointment/appointment` 预约创建、`/api/user/auth/login` 登录、`/api/doctor/schedule/slots` 号源查询
- 已确认采用混合方案：网关负责统一入口限流，服务内部负责熔断/降级/热点参数保护
- 第一版规则存储策略为“先本地落地、后迁移 Nacos”：先在代码/配置中初始化默认规则，待跑通后再改为 Nacos 动态数据源
- 第一批最小规则集目标：网关保护 `login/chatSend/knowledgeSearch`，服务保护 `chatStream/tts/kbSearch/embedding/appointmentCreate/scheduleSlots`

## Sentinel Implementation Findings (2026-03-24)

- `medical-gateway` 已接入 `spring-cloud-starter-alibaba-sentinel` 与 `sentinel-spring-cloud-gateway-adapter`，并新增 `SentinelGatewayConfig`、`GatewaySentinelBlockHandler`，以 API 分组资源名 `gw:auth:login`、`gw:auth:wxLogin`、`gw:ai:chatSend`、`gw:knowledge:search` 加载本地限流规则
- `medical-ai-service` 已增加 `SentinelRuleConfig`，对 `svc:ai:chatStream` 和 `svc:ai:tts` 加载线程数/RT/异常比例规则；`ChatServiceImpl` 在 chat 主链路入口做 Sentinel entry，并在 `TtsServiceImpl` 中对 TTS 限流/熔断时直接降级为仅文本返回
- `medical-knowledge-service` 已增加 `SentinelRuleConfig`，对 `svc:knowledge:search` 和 `svc:knowledge:embed` 加载流控与熔断规则；外部 `/kb/search` 被降级时返回 `知识检索暂不可用，请稍后重试`，内部 `/kb/inner/search` 返回空数组，避免拖垮 AI 对话主链路
- `medical-appointment-service` 使用 `SphU.entry(resource, EntryType.IN, ..., slotId)` 对 `svc:appointment:create` 做热点参数保护，热点号源超限时返回 `当前号源繁忙，请刷新后重试`
- `medical-doctor-service` 使用组合参数 `doctorId:date` 对 `svc:doctor:scheduleSlots` 做热点保护，号源查询超限时返回 `号源查询繁忙，请稍后重试`
- 额外修正了若干既有测试签名不匹配问题（如 `ChatService` / `SummaryService` / `AppointmentService` 新旧方法参数不一致），使当前目标模块测试可重新编译和执行

## Sentinel Verification (2026-03-24)

- Gateway 测试：`mvn test -pl medical-gateway -f medical-ai/pom.xml -Dtest=SentinelGatewayConfigTest,GatewaySentinelBlockHandlerTest` → `BUILD SUCCESS`
- AI 服务测试：`mvn test -pl medical-service/medical-ai-service -f medical-ai/pom.xml -Dtest=ChatServiceImplTest,TtsServiceImplTest,ChatControllerTest` → `BUILD SUCCESS`，共 `11` 条测试通过
- 知识服务测试：`mvn test -pl medical-service/medical-knowledge-service -f medical-ai/pom.xml -Dtest=KnowledgeBaseControllerTest,KnowledgeBaseServiceImplTest,EmbeddingServiceImplTest` → `BUILD SUCCESS`，共 `23` 条测试通过
- 预约服务测试：`mvn test -pl medical-service/medical-appointment-service -f medical-ai/pom.xml -Dtest=AppointmentControllerTest,AppointmentServiceImplTest` → `BUILD SUCCESS`，共 `16` 条测试通过
- 医生服务测试：`mvn test -pl medical-service/medical-doctor-service -f medical-ai/pom.xml -Dtest=ScheduleControllerTest,ScheduleServiceSentinelTest` → `BUILD SUCCESS`，共 `15` 条测试通过
- 跨模块编译验证：`mvn -q -pl medical-gateway,medical-service/medical-ai-service,medical-service/medical-knowledge-service,medical-service/medical-appointment-service,medical-service/medical-doctor-service -am -DskipTests compile -f medical-ai/pom.xml` → 成功，无输出即 0 退出

## Seata Design Decisions (2026-03-25)

- 当前仓库中真正需要分布式事务保护的高危场景集中在 `AppointmentServiceImpl.createAppointment()` 与 `AppointmentServiceImpl.cancelAppointment()`：两者都同时涉及 `medical-appointment` 与 `medical-doctor` 两个独立数据库的写操作
- 方案选型结论：采用 **Seata AT 模式**，由 `appointment-service` 作为 TM + RM，`doctor-service` 作为 RM，`ai-service` 不直接参与分布式事务，只通过 Feign 调用 `appointment-service`
- 基础设施方案：在 `medical-ai/docker/docker-compose.yml` 中新增 `seata-server` 容器，复用现有 `nacos:8848` 作为注册/配置中心，复用现有 `mysql:3306` 新建 `seata` 数据库存储 TC 元数据
- 业务代码最小改动原则：保留 `@Transactional`，仅在 `AppointmentServiceImpl` 的创建/取消方法上新增 `@GlobalTransactional`；`doctor-service` 的 `ScheduleServiceImpl` 保持零业务代码改动
- 兼容性注意事项：实施前必须用 `mvn dependency:tree ... -Dincludes=*:seata*` 核实 Spring Cloud Alibaba 2023.0.3.2 实际解析的 Seata 客户端版本，并确保与 `seata-server` 主版本一致
- Druid + Seata 风险点：集成后要验证数据源实际被 `SeataDataSourceProxy` 包裹，否则 `undo_log` 不会落盘，AT 回滚将失效
- 测试策略：`application-test.yml` 中禁用 seata，保持 H2 单元测试隔离；集成测试另行通过 `tests/test_10_seata.py` 在 Docker 环境验证正向提交与回滚行为

## Seata Runtime Notes (2026-03-25)

- `spring-cloud-starter-alibaba-seata:2023.0.3.2` 实际解析 `seata-spring-boot-starter:2.1.0`，本次按 2.1.0 客户端线完成编译、单测与 Docker 运行态验证。
- `docker-compose.yml` 中 `seata-server` 运行态可用镜像为 `apache/seata-server:2.1.0`；`seataio/seata-server:2.1.0` 在 Docker Hub 上不可拉取。
- Seata Server 2.1.0 在 Compose 环境下需要使用 `SEATA_STORE_MODE`、`SEATA_STORE_DB_DATASOURCE`、`SEATA_STORE_DB_DB_TYPE`、`SEATA_STORE_DB_*` 这一组 Spring 环境变量，才能正确进入 DB 存储模式。
- 在 `config.type=nacos` 下，客户端启动除本地配置外，还要求配置中心存在 `SEATA_GROUP/service.vgroupMapping.medical_tx_group=default`；缺失时 `doctor-service` 与 `appointment-service` 会在 `GlobalTransactionScanner` 初始化阶段失败。
- `medical-ai/docker/mysql/init/*.sql` 通过 `/docker-entrypoint-initdb.d` 仅在 MySQL 首次初始化或新 volume 时自动执行；已有 volume 需要手工执行 `undo-log-init.sql` 或重建数据卷，否则 Seata AT 数据源代理会报 `undo_log table not exist`。
- `tests/test_10_seata.py` 已调整为可单文件独立运行，会自行准备 patient/doctor/slot 上下文；当前覆盖的是预约/号源一致性冒烟验证，不是故障注入式回滚证明。
- 完整运行态验证结果：在 Docker Compose + Seata Server + Nacos + MySQL 环境下，`pytest tests/test_10_seata.py -v` 实测 `3 passed`。

## RabbitMQ Async Side Effects Runtime Notes (2026-03-25)

- `AppointmentEventPublisherImpl` 必须使用 outbox 行里的 `routingKey`（`appointment.created` / `appointment.cancelled`）投递消息；若错误使用 `eventType`（`APPOINTMENT_CREATED` / `APPOINTMENT_CANCELLED`），将无法命中 `appointment.#` 的 Topic Binding，消费者侧永远观察不到通知/审计副作用。
- RabbitMQ 副作用 4 张表都继承了 `BaseEntity` 语义，运行态 SQL 不仅需要业务字段，还必须包含 `create_time/create_by/update_time/update_by/deleted`；否则 MyBatis-Plus 默认查询会直接报 `Unknown column 'create_by'`，定时 outbox 发布任务无法工作。
- `medical-ai/docker/mysql/init/rabbitmq-outbox-init.sql` 只会在 MySQL 新 volume 初始化时自动执行；已有 `mysql-data` volume 需要手工在运行中的 `medical-mysql` 上执行该 SQL，或先 drop 旧表再重建，才能让 RabbitMQ 副作用链路真正可用。
- `medical-ai/docker/docker-compose.yml` 中 `appointment-service` 运行 RabbitMQ 版实现时必须显式依赖 `rabbitmq` healthy，并注入 `RABBITMQ_HOST/RABBITMQ_PORT/RABBITMQ_USER/RABBITMQ_PASSWORD`；否则容器内默认连 `localhost:5672`，消息发布器与消费者都不会连到 Compose 内的 broker。
- 本轮环境中，Seata 所需的 Nacos 配置 `SEATA_GROUP/service.vgroupMapping.medical_tx_group=default` 已经存在，因此 RabbitMQ 运行态验收无需再额外补写 Nacos。
- `tests/test_11_rabbitmq_side_effects.py` 已调整为单文件独立运行：测试会自行注册独立 patient、准备 doctor/slot 上下文，并通过 `docker exec medical-mysql mysql ...` 轮询 outbox / notification / audit 表来验证 create 与 cancel 两条副作用链路。
- Outbox 发布语义现已明确为 **claim -> publish -> broker confirm/return -> finalize**：先用 CAS 把 `publish_status` 从 `0=pending` 原子切到 `2=publishing`，只有拿到 publisher confirm `ack=true` 且没有 return 时才切到 `1=published`；任何 nack / return / timeout / send exception 都会把行恢复成 `0=pending` 并累加 `retry_count`。
- `publish_status=2` 同时承担崩溃恢复语义：如果实例在 claim 后宕机，其他实例会在 `medical.mq.publish-claim-timeout-seconds` 超时后重新 claim 并重发，因此该链路是 **at-least-once publish**，下游消费者必须保持幂等。
- Notification / Audit 消费者现已把副作用表的唯一键冲突视为“副作用已落库”的成功回放：若第一次消费在副作用 insert 成功后、consume log 成功前崩溃，重放不会再因 duplicate key 进入 DLQ，而是补写 success consume log 并 ack。
- 运营恢复语义：outbox 行长期停在 `publish_status=0` 代表 broker confirm 未闭环，可继续由定时任务自动重试；长期停在 `publish_status=2` 代表 claim 实例可能卡死/宕机，等待 claim timeout 后会被其他实例接管；消费者 DLQ 仍仅保留真正的不可恢复错误（如 JSON 反序列化失败或副作用/consume log 持续异常）。
