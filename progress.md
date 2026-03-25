# Progress Log

> 项目：AI 数字人医疗小助手系统
> 最后更新：2026-03-25

## Historical Sessions Summary

| 日期 | Phase | 关键成果 |
|------|-------|---------|
| 02-27 | 1-2 | 需求确认 + 架构设计 + 12 个实施计划 |
| 02-28 | 3 | 01~08 模块实现 (common/user/doctor/knowledge/ai/appointment/gateway) |
| 03-01 | 3-4 | 09~11 前端+部署, Docker 14/14 UP, 联调 12/12 PASS |
| 03-02 | 5-6 | 交付文档 5 篇 + API 联调审查 (11C+9M 问题发现并修复) |
| 03-03 | 7-10 | Mock 修复 + Admin UI 重构 (医疗级 SaaS) + 109 单元测试 |
| 03-05 | 11 | Python 集成测试 60 用例, 4 根因修复 → 58 PASSED 2 SKIPPED |
| 03-06 | 12 | 用户/医生绑定 + 排班权限 + 模板稳定性 |
| 03-07 | 12 | KB 路由/RAG/Embedding/Markdown/SSE 等 12 轮修复 |
| 03-08 | 13 | 小程序 UI 重构 + Live2D 位姿 + 全量 H5 |
| 03-20 | 14-24 | 导诊修复 + 医生数据 + MP API 对接 + CORS + UI 优化 + TTS 集成 + 功能增强 |
| 03-21 | 25 | SSE+TTS 卡死修复 (本次) |

## Session: 2026-03-21 (Phase 25 — SSE+TTS 卡死修复)

### 问题描述
跟数字人对话时，前端消息成功发到后端，后端生成语音后卡死：文字不返回，语音也不返回。

### 根因

| 层 | 根因 |
|----|------|
| 后端 | `concatWith(Mono.fromCallable(...))` 中 TTS 同步阻塞 SSE 流，complete 事件永不发出 |
| 后端 | DashScope `SpeechSynthesizer.call()` 无超时控制 |
| 前端 | `playAuthenticatedAudio` 在 `play()` 就 resolve，音频掐断 |
| 前端 | SSE 解析 `.trim()` 吞空格 token |
| 前端 | 旧 SSE 请求无取消机制 |

### 修复

**后端 (Codex → cherry-pick + BOM 修复)**
- `ChatServiceImpl.java`: 先发 complete(ttsUrl=null)，再异步追加 tts/tts_error 事件，30s timeout
- `TtsServiceImpl.java`: CompletableFuture 30s 超时包裹 DashScope 调用
- 新增 ChatServiceImplTest (134行) + TtsServiceImplTest (68行)

**前端 (Gemini → cherry-pick)**
- `audio-player.js`: onended 才 resolve
- `main.js`: SSE data 行保留空格 + AbortController + tts/tts_error 事件支持

### 验证
- Maven compile: **SUCCESS**
- live2d-h5 build: **SUCCESS** (5.17s)

### Commits
- `194e94b` agent(fix-frontend-sse-tts-v1): gemini result
- `74b0649` fix(ai): 解耦SSE文字流与TTS合成，修复对话卡死问题

### Errors
| Error | Resolution |
|-------|------------|
| Codex 0 分 (idle terminated + 无 Maven + 越界写规划文件) | 代码正确，手动 cherry-pick 排除越界文件 |
| 4 个 Java 文件 UTF-8 BOM | Python 脚本批量移除 |

## Session: 2026-03-21 (Phase 26 — H5 聊天界面只显示 loading)

### 问题描述
数字人聊天界面不显示文字输出内容，只显示 loading（CSS `:empty` 动画 `···`）。

### 调研 (Gemini 前端 + Codex 后端 并行)
- **后端结论**: SSE 事件序列正确 (`type=token` → `complete` → `tts`)，`publishOn` 位置无误
- **前端结论**: Bug 集中在 `medical-mp/live2d-h5/src/main.js`

### 根因

| # | Bug | 位置 | 影响 |
|---|-----|------|------|
| 1 | `buffer = blocks.pop()` — 流结束后残留 buffer 未处理 | main.js L401 | complete 事件可能被吞 |
| 2 | `currentAiBubble = null` 在 RAF 回调之前执行 | main.js L477 | **全部文字渲染被跳过** |

**核心机制**: `await reader.read()` 解析为 microtask，不让位给 `requestAnimationFrame` 宏任务。当 LLM 回复快或网络批量到达时，整个 while 循环在一帧内跑完，一次 RAF 都没执行。循环结束后 `currentAiBubble = null` 导致 RAF 回调 bail out，bubble.innerHTML 从未被写入，CSS `:empty` 持续显示 `···`。

### 修复 (Boss 微操)
- `main.js`: while 循环退出后、状态重置前:
  1. 刷新残留 buffer（处理最后一个事件）
  2. 同步调用 `updateAiBubble()`（保证最终渲染）

### 验证
- live2d-h5 build: **SUCCESS** (3.28s)

## Session: 2026-03-23 (Planning catchup)

### 操作
- 运行 `planning-with-files` catchup，确认项目根目录规划文件仍存在
- 执行 `git status --short` 与 `git diff --stat`，同步当前工作区状态到规划文件
- 检查 2 个未提交 diff，补充到 `task_plan.md` 与 `findings.md`

### 当前工作区状态
- 已修改：`medical-ai/medical-common/medical-common-core/src/main/java/com/medical/common/core/handler/GlobalExceptionHandler.java`
- 已修改：`medical-mp/live2d-h5/index.html`
- 未跟踪：`hs_err_pid10344.log`, `hs_err_pid13132.log`, `hs_err_pid8272.log`

### 发现
- 后端新增 `AsyncRequestNotUsableException` 处理，目标是让 SSE 客户端断连不再污染错误日志
- 前端把 `marked` CDN 固定到 `4.3.0`，属于依赖稳定性收敛
- 当前尚无对 3 个 JVM 崩溃日志的根因分析记录

## Session: 2026-03-23 (Phase 27 — 系统指标盘点与验证)

### 目标
- 输出核心接口性能、SSE 流式表现、知识检索效果、预约闭环、系统规模、稳定性、部署效率、覆盖范围的实测或仓库证据

### 当前发现
- 仓库层面已确认 3 端、15 个产品模块 / 6 个后端服务模块 / 11 个实施模块、67~70 个接口的证据来源
- `tests/` 中存在登录、医生查询、知识检索、创建会话、SSE 对话、预约挂号、全链路导诊闭环的现成用例/脚本
- `docker compose -f medical-ai/docker/docker-compose.yml ps` 返回空列表，当前没有运行中的 compose 容器可直接统计

### 本次实测
- `docker compose up -d`：39.49s；核心登录接口可用：103.77s
- Compose 实际启动 14 个容器（5 业务服务 + 1 网关 + 2 前端 + 6 基础设施）
- 核心接口时延：登录 89.23/96.76ms，医生列表 66.35/74.64ms，创建会话 25.43/35.62ms，预约创建 77.75/88.82ms（avg/p95）
- SSE：首包 1664.74/1972.67ms，完整回复 6066.46/6969.38ms（avg/p95），5/5 收到 complete
- 导诊到挂号闭环成功率：5/5
- 稳定性压测（登录/医生列表/创建会话/挂号创建取消混合序列）：20/50/100 次均为 0 error、0 timeout
- 知识检索 `/knowledge/kb/search` 当前返回 `5003 / 404 -`，无法给出命中片段，需单独排查外部 embedding/检索依赖

### 结论
- Phase 27 检查项已全部覆盖：系统规模、接口覆盖、核心性能、SSE、预约闭环、稳定性、部署效率均已落盘到 `findings.md`
- 当前唯一未闭环问题是知识检索运行态异常；已作为风险项保留，不纳入“已通过运行态验收”的结论
- `task_plan.md` 当前阶段已更新为 `complete`

## Session: 2026-03-23 (Planning with Files 复核)

### 操作
- 运行本地 `planning-with-files` catchup 脚本，确认项目根目录可继续沿用现有 `task_plan.md`、`findings.md`、`progress.md`
- 执行 `git status --short`、`git diff --stat`、`git diff --name-status`，同步工作区与规划文件状态
- 将当前未同步上下文补记到 `task_plan.md` 与 `findings.md`

### 当前工作区状态
- 已修改：`task_plan.md`、`findings.md`、`progress.md`（本次规划同步）
- 已修改：`medical-ai/medical-common/medical-common-core/src/main/java/com/medical/common/core/handler/GlobalExceptionHandler.java`
- 已修改：`medical-mp/live2d-h5/index.html`
- 已删除：`语音合成.md`

### Errors
| Error | Resolution |
|-------|------------|
| 使用 PowerShell 风格命令在 Bash 工具中运行 catchup 失败 | 改为直接调用仓库内 `.opencode/skills/planning-with-files/scripts/session-catchup.py` |
| 使用 `/mnt/c/...` 路径运行 catchup 失败 | 改为使用相对仓库路径，避免 Git Bash/Windows Python 路径转换问题 |

## Session: 2026-03-23 (Phase 28 — 知识检索 5003/404 排障)

### 目标
- 复现 `/knowledge/kb/search` 返回 `5003 / 404 -` 的问题，定位故障发生在网关、knowledge 服务、向量检索、embedding 还是外部模型依赖哪一层

### 操作
- 按 `systematic-debugging` 先进入根因调查阶段，不直接修改业务代码
- 更新 `task_plan.md` 当前阶段为 Phase 28，并准备委派后端排障任务

### 当前发现
- 通过 task-router 委派 Codex 只读调查 knowledge 链路，watch 面板最终显示该任务以失败态退出，但 stdout 保留了完整根因报告；失败主因是代理空闲终止与结构化输出不合规，不影响代码层证据提取
- 已独立复核关键证据：`medical-knowledge-service` 的 `spring.ai.openai.base-url` 为 `https://dashscope.aliyuncs.com/compatible-mode`，而设计文档和 `medical-ai-service` 使用 `.../compatible-mode/v1`
- 已独立复核异常透传链：`EmbeddingServiceImpl` 把下游异常消息直接包装成 `EMBEDDING_ERROR(5003)`，`GlobalExceptionHandler` 再将 `e.getMessage()` 原样返回
- 已独立复核测试兜底：`tests/test_06_knowledge.py` 对 `5003` 采取 skip/放行策略，导致 embedding 故障未被当作硬失败

### 当前结论
- 高置信度根因是 knowledge-service 的 DashScope OpenAI-compatible `base-url` 少了 `/v1`，使 embedding 请求命中错误根路径并返回 404
- `/knowledge/kb/search` 的 `5003 / 404 -` 不是 Milvus 检索阶段报错，而是 query embedding 在检索前就失败
- 下一步若进入修复，应先只改 `spring.ai.openai.base-url` 与对应 Nacos 配置，再做最小回归验证

### 修复与验证
- 已将 `medical-ai/medical-service/medical-knowledge-service/src/main/resources/application.yml` 中的 DashScope `base-url` 修为 `https://dashscope.aliyuncs.com/compatible-mode/v1`
- 已执行静态比对，确认 knowledge-service 与 ai-service 的 DashScope `base-url` 一致
- 已执行 `mvn test -pl medical-service/medical-knowledge-service -f medical-ai/pom.xml -Dtest=KnowledgeBaseControllerTest`
- 验证结果：`BUILD SUCCESS`，`Tests run: 18, Failures: 0, Errors: 0, Skipped: 0`

### 收尾结论
- Phase 28 已完成，当前仓库内的高置信度配置根因已修复并完成最小自动化验证
- 剩余外部风险仅在运行环境/Nacos 覆盖配置是否仍保留旧值

## Session: 2026-03-23 (知识检索容器重建验证)

### 操作
- 首次直接 `docker compose build knowledge-service` 后检查容器内 `/app/app.jar`，发现仍是旧值 `.../compatible-mode`，定位为镜像构建前未重新打包模块产物
- 随后执行 `mvn clean package -DskipTests -pl medical-service/medical-knowledge-service -am -f medical-ai/pom.xml` 重新生成 `target/*.jar`
- 再执行 `docker compose build --pull=false knowledge-service && docker compose up -d knowledge-service` 重建并重启容器

### 验证
- `docker exec medical-knowledge-service sh -lc "unzip -p /app/app.jar BOOT-INF/classes/application.yml | strings | grep 'base-url:'"` 输出 `https://dashscope.aliyuncs.com/compatible-mode/v1`
- `docker inspect medical-knowledge-service` 显示容器已于本次会话重新创建并处于 `running`
- 启动日志显示 `medical-knowledge-service.yml` 的 Nacos 配置为空，说明当前容器实际使用镜像内配置

### Errors
| Error | Resolution |
|-------|------------|
| 仅重建 Docker 镜像后容器内仍为旧 `base-url` | 发现 Dockerfile 复制的是 `target/*.jar`，因此先重新打包 Maven 模块，再重建镜像 |
| 二次构建时拉取基础镜像元数据出现 TLS handshake timeout | 改用 `docker compose build --pull=false knowledge-service` 复用本地基础镜像完成构建 |

## Session: 2026-03-23 (Phase 29 — Sentinel 限流与熔断降级保护设计)

### 目标
- 为系统识别最需要保护的入口和内部资源，形成一版可实施的 Sentinel 混合方案（gateway 限流 + service 熔断/降级/热点参数）

### 当前发现
- 仓库当前没有 Sentinel 依赖与规则接入，需要从 0 到 1 补齐依赖、资源命名、fallback/blockHandler 与规则初始化
- 已完成只读调研并确定第一批保护对象：登录、AI 对话 SSE、TTS、知识检索、预约创建、号源查询
- 已得到用户确认的设计决策：采用混合方案；规则先本地落地，后续再迁移到 Nacos

### 产物
- 设计文档：`docs/superpowers/specs/2026-03-23-sentinel-protection-design.md`
- 实施计划：`docs/superpowers/plans/2026-03-23-sentinel-protection-implementation.md`

### Errors
| Error | Resolution |
|-------|------------|
| task-router watch 面板显示调试任务失败 | 收集 watcher 结果后改为读取 task-router 最终报告与仓库实证文件，确认是代理输出协议问题，不是调研结论失效 |
| 代理环境缺少 `git` 可执行文件，无法直接查看最近提交 | 改用仓库内静态对照（设计文档 vs 实际配置 vs 同仓库其他服务配置）建立回归证据链 |

## Session: 2026-03-24 (Phase 29 — Sentinel 限流与熔断降级保护实现)

### 实现范围
- `medical-gateway`：Sentinel gateway 依赖、API 分组规则、本地 block 响应
- `medical-ai-service`：`chatStream`、`tts` 资源限流/熔断与降级
- `medical-knowledge-service`：`search`、`embed` 资源限流/熔断与内外部差异化降级
- `medical-appointment-service`：`slotId` 热点参数保护
- `medical-doctor-service`：`doctorId:date` 热点参数保护

### 关键实现
- 网关新增 `SentinelGatewayConfig` 与 `GatewaySentinelBlockHandler`，使用 `gw:*` 资源名对登录、微信登录、AI 对话、知识检索做入口限流
- AI 服务新增 `SentinelRuleConfig`，`ChatServiceImpl` 在 chat 流入口进行 Sentinel entry，`TtsServiceImpl` 在限流/熔断时直接返回 `null`，从而走现有文本-only 降级路径
- 知识服务新增 `SentinelRuleConfig`，controller 对外部 `/kb/search` 返回忙碌提示，对内部 `/kb/inner/search` 返回空数组；`EmbeddingServiceImpl` 在被阻断时抛 `AI_RATE_LIMIT`，其他异常仍映射为 `EMBEDDING_ERROR`
- 预约服务与医生服务分别在 service 层使用带参数的 `SphU.entry(...)` 完成热点资源保护，不依赖 controller 注解做参数提取

### 验证
- `mvn test -pl medical-gateway -f medical-ai/pom.xml -Dtest=SentinelGatewayConfigTest,GatewaySentinelBlockHandlerTest` → PASS
- `mvn test -pl medical-service/medical-ai-service -f medical-ai/pom.xml -Dtest=ChatServiceImplTest,TtsServiceImplTest,ChatControllerTest` → PASS
- `mvn test -pl medical-service/medical-knowledge-service -f medical-ai/pom.xml -Dtest=KnowledgeBaseControllerTest,KnowledgeBaseServiceImplTest,EmbeddingServiceImplTest` → PASS
- `mvn test -pl medical-service/medical-appointment-service -f medical-ai/pom.xml -Dtest=AppointmentControllerTest,AppointmentServiceImplTest` → PASS
- `mvn test -pl medical-service/medical-doctor-service -f medical-ai/pom.xml -Dtest=ScheduleControllerTest,ScheduleServiceSentinelTest` → PASS
- `mvn -q -pl medical-gateway,medical-service/medical-ai-service,medical-service/medical-knowledge-service,medical-service/medical-appointment-service,medical-service/medical-doctor-service -am -DskipTests compile -f medical-ai/pom.xml` → PASS

### Errors
| Error | Resolution |
|-------|------------|
| Gateway Sentinel 适配类初版导入了错误包名 | 通过检查本地 Maven 仓库中的 adapter jar，改用 `gateway.common` 下的 `GatewayApiDefinitionManager`/`GatewayRuleManager`，并补充 `sentinel-spring-cloud-gateway-adapter` 依赖 |
| `medical-ai-service` 现有测试中存在旧方法签名和一个未完成 mock 构造 | 顺手修复测试调用签名，并改用真实 `ChatResponse/Generation` 对象构造测试数据 |
| 仅添加 starter 不足以支持 Spring Cloud Gateway 规则与 block handler | 补充网关专用 adapter 依赖和 `SentinelGatewayBlockExceptionHandler` 相关配置 |

## Session: 2026-03-24 (Planning with Files catchup)

### 操作
- 读取现有 `task_plan.md`、`findings.md`、`progress.md`，确认项目已具备持续规划文件
- 尝试执行用户提供的 PowerShell 版 catchup 命令；因当前工具实际运行在 Bash 中失败

## Session: 2026-03-25 (Planning with Files catchup)

### 操作
- 先读取现有 `task_plan.md`、`findings.md`、`progress.md`，确认延续使用当前规划文件
- 使用仓库内 `.opencode/skills/planning-with-files/scripts/session-catchup.py` 执行 catchup；脚本无输出
- 执行 `git status --short` 与 `git diff --stat`，同步最新工作区状态
- 额外检查 `docs/superpowers/**/*`，确认未跟踪文档范围

### 当前工作区状态
- 已修改：`.gitignore`
- 已修改：`task_plan.md`、`findings.md`、`progress.md`（本次规划同步）
- 已修改：`medical-ai/medical-common/medical-common-core/src/main/java/com/medical/common/core/handler/GlobalExceptionHandler.java`
- 已修改：`medical-mp/live2d-h5/index.html`
- 已删除：`语音合成.md`
- 未跟踪：`docs/superpowers/specs/2026-03-23-sentinel-protection-design.md`
- 未跟踪：`docs/superpowers/plans/2026-03-23-sentinel-protection-implementation.md`

### 发现
- 本轮 catchup 未发现新的 session 文本残留，但 git 工作区仍有未同步业务改动与文档改动
- 2026-03-24 catchup 中已记录的 `.gitignore` 改动本轮仍在，说明尚未被后续提交或决策吸收
- Sentinel 相关设计/实施文档当前仍未纳入 git 跟踪，需要后续决定是否作为正式交付物保留

### Errors
| Error | Resolution |
|-------|------------|
| 使用 PowerShell 风格命令在 Bash 工具中运行 catchup 失败 | 改为直接调用仓库内 `.opencode/skills/planning-with-files/scripts/session-catchup.py` |
| 使用 `C:/Users/...` 与 `/mnt/c/...` 路径运行 catchup 失败 | 改为使用仓库内 repo-local 脚本路径 |

## Session: 2026-03-25 (Phase 30 — Seata 分布式事务设计与实施计划)

### 目标
- 为预约创建/取消流程设计 Seata AT 分布式事务方案，并形成可直接执行的实施计划，供新窗口继续落地

### 操作
- 使用子代理梳理 `medical-ai/medical-api/` 下全部 Feign Client 与跨服务调用点，确认高危跨库写场景集中在 `appointment-service` ↔ `doctor-service`
- 与用户确认基础设施方向：采用 Docker Compose 集成 `seata-server`，复用现有 Nacos + MySQL
- 完成 Seata AT / TCC / SAGA 三种方案对比，确认采用 AT 模式
- 编写设计文档 `docs/superpowers/specs/2026-03-25-seata-distributed-transaction-design.md`
- 发起 spec review；根据 reviewer 反馈修正包名、版本兼容、TC 端口说明、`@Transactional` 保留、TC/undo_log DDL、Seata Server 环境变量、Druid 验证与回滚策略等问题
- 编写实施计划 `docs/superpowers/plans/2026-03-25-seata-distributed-transaction-implementation.md`

### 产物
- 设计文档：`docs/superpowers/specs/2026-03-25-seata-distributed-transaction-design.md`
- 实施计划：`docs/superpowers/plans/2026-03-25-seata-distributed-transaction-implementation.md`

### 当前结论
- 记忆已落盘；新窗口只需先读取 `task_plan.md`、`findings.md`、`progress.md`，再按实施计划执行即可无缝续接
- 进入实施前，第一步应先核实 `mvn dependency:tree` 中 Seata 客户端实际版本，确保与 `seata-server:2.0.0` 兼容

### Errors
| Error | Resolution |
|-------|------------|
| 第一版 spec 将 `@Transactional` 误写成替换为 `@GlobalTransactional` | 在 review 后修正为两者共存 |
| 第一版 spec 使用了过时的 `io.seata.*` 包名 | 修正为 `org.apache.seata.*` 并补充版本兼容说明 |

## Session: 2026-03-25 (Planning with Files catchup, current session)

### 操作
- 先读取现有 `task_plan.md`、`findings.md`、`progress.md` 与 skill 模板，确认可沿用现有规划文件结构
- 首次按用户提供的 PowerShell 形式运行 catchup，因当前 Bash 工具不支持 `(Get-Location)` 语法而失败
- 改为执行仓库内 `.opencode/skills/planning-with-files/scripts/session-catchup.py`；脚本无输出
- 执行 `git diff --stat` 与 `git status --short`，将当前工作区状态同步回规划文件

### 当前工作区状态
- 已修改：`.gitignore`
- 已修改：`medical-ai/medical-common/medical-common-core/src/main/java/com/medical/common/core/handler/GlobalExceptionHandler.java`
- 已修改：`medical-mp/live2d-h5/index.html`
- 已修改：`task_plan.md`、`findings.md`、`progress.md`（本次 catchup 同步）
- 已删除：`语音合成.md`
- 未跟踪：`docs/superpowers/`

### 结论
- 现有规划文件已同步到最新工作区状态，可继续作为后续会话的持久化上下文
- 当前未闭合事项仍是工作区遗留改动与未跟踪设计文档的后续处置，而非新的 session 恢复问题

### Errors
| Error | Resolution |
|-------|------------|
| 在 Bash 工具中直接执行 PowerShell 风格的 catchup 命令失败 | 改为使用仓库内 repo-local `session-catchup.py` 并传入 `"$(pwd)"` |

## Session: 2026-03-25 (Phase 30 — Seata 分布式事务集成运行态验证)

### 范围
- 在 worktree `seata-distributed-transaction` 内完成 Seata Docker 运行态打通，并实际执行 `pytest tests/test_10_seata.py -v`。

### 结果
- `mvn clean package -DskipTests -f medical-ai/pom.xml`：**BUILD SUCCESS**，产出各服务 `target/*.jar` 供 Dockerfile 使用。
- `docker compose -f medical-ai/docker/docker-compose.yml up -d --build`：经过镜像仓库与配置修正后，成功拉起 `seata-server`、`medical-doctor-service`、`medical-appointment-service` 及依赖容器。
- `pytest tests/test_10_seata.py -v`：**3 passed**，总耗时 `363.73s`。

### 发现
- `apache/seata-server:2.1.0` 才是可拉取的 Docker Hub 镜像；`seataio/seata-server:2.1.0` 不存在。
- Seata Server 2.1.0 需要 `SEATA_STORE_DB_*` 环境变量；旧的 `STORE_DB_*` 变量不会让 server 正确绑定 `dbType`。
- Seata 客户端在 `config.type=nacos` 下需要 Nacos 中显式存在 `service.vgroupMapping.medical_tx_group=default`。
- 旧 MySQL volume 不会自动补 `undo_log`，需对运行中的 `medical-mysql` 手工执行 `/docker-entrypoint-initdb.d/undo-log-init.sql`。
- `tests/test_10_seata.py` 已改成单文件可独立运行，会自行注册 patient 并准备 doctor/slot 上下文。

### Errors
| Error | Resolution |
|-------|------------|
| `docker compose` 初次执行时报 `open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified` | 等待 Docker Desktop/daemon 启动后重试 |
| `docker compose up` 拉取 `seataio/seata-server:2.1.0` 失败 (`not found`) | 改为 `apache/seata-server:2.1.0` |
| `seata-server` 启动报 `store.db.dbType should not be blank` | 改用 `SEATA_STORE_DB_*` 系列环境变量 |
| `doctor-service` / `appointment-service` 启动报 `service.vgroupMapping.medical_tx_group configuration item is required` | 通过 Nacos OpenAPI 写入 `SEATA_GROUP/service.vgroupMapping.medical_tx_group=default` |
| `doctor-service` / `appointment-service` 启动报 `undo_log table not exist` | 对运行中的 `medical-mysql` 手工执行 `/docker-entrypoint-initdb.d/undo-log-init.sql` |
| `tests/test_10_seata.py` 单独执行时缺少 `patient_username` / doctor 上下文 | 调整测试为自准备 patient/doctor/slot 前置状态 |
