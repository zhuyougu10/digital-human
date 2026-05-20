# 进度日志

## 2026-05-20 09:50:00 [DONE] 导诊 Agent 确定性重构并完成真实流程复测
- 重构：废弃原 `TriageAgent` 的提示词和工具编排，导诊会话在 `ChatServiceImpl` 中直接进入 `TriageAppointmentFlowService` 确定性状态机，不再让大模型决定预约步骤。
- 新流程：第一轮必须先收集预约日期和上午/下午；拿到时间后按症状搜索医生并过滤该时段可用号源；用户通过“选1”或医生姓名选人；最后必须回复“确认预约”才创建预约。
- 修复：真实复测发现“我想预约2026年5月26日上午，咳嗽”这类句子会把日期和流程词混入医生搜索，导致查不到医生；已改为抽取明确症状关键词并按候选词兜底搜索。
- 验证：通过 `TriageAppointmentFlowServiceTest`、`TriageAgentTest`、`ChatServiceImplTest#chat_shouldUseDeterministicTriageFlowInsteadOfChatModel`，并完成 `medical-ai-service` 模块打包。
- 真实复测：已热更新并重启 `medical-ai-service` 容器；使用指定患者 token 创建 `sessionId=256`，按“先问时间 -> 找医生 -> 选孙七 doctorId=5 -> 确认预约”创建 `appointmentId=84`、`slotId=480`、`2026-05-26 morning`。
- 医生端确认：登录 `doctor_sunqi/123456` 后，医生预约列表可见 `appointmentId=84`；结束会话后 `summary/session/256` 与 `summary/appointment/84` 均返回 `summaryId=51` 且 `appointmentId=84`。

## 2026-05-20 00:45:00 [DONE] 使用指定患者 token 复测并修复预约成功文案误判
- 复测 token：指定患者 token 对应 `userId=4`，真实网关路径为 `/api/ai/chat/**`、`/api/appointment/appointment/**`。
- 复现：`sessionId=250/251/252` 中 AI 工具实际创建预约 `appointmentId=79/80/81`，患者端和医生端均可见，摘要也能绑定预约，但最终 assistant 回复被守卫改写为“尚未成功创建预约”。
- 根因：真实模型成功文案可能只包含“预约编号：xx”，不包含 `doctorId`，原守卫只能按 `doctorId + slotId` 反查，且首次补的 appointmentId 正则未覆盖真实中文变体。
- 修复：`ChatServiceImpl` 在无 tool 落库消息时，先从成功文案中解析/候选提取预约编号，按预约快照校验 `patientId` 和 `sessionId`，验证通过则保留成功文案；保留原 `patientId + slotId` 兜底创建/绑定逻辑。
- 验证：新增 `chat_shouldAcceptVerifiedAppointmentIdWhenAssistantReplyOmitsDoctorId`、`chat_shouldVerifyAppointmentIdFromNumericCandidatesWhenLabelVaries`，并通过目标单测。
- 真实复测：热更新并重启 `medical-ai-service` 后，使用同一 token 预约李四医生 `doctorId=2`、`slotId=463`、`2026-05-25 afternoon`，生成 `sessionId=253`、`appointmentId=82`；患者预约可见、医生预约列表可见、`summary/session/253` 与 `summary/appointment/82` 均返回 `appointmentId=82`，最终 AI 回复保留“预约已成功创建/预约编号：82”。
- 备注：`docker compose build --no-cache ai-service` 本次超过 15 分钟卡住，已停止卡住的构建进程；为完成复测，采用本地 Maven 打包后复制 jar 到运行容器并重启的方式热更新。

## 2026-05-19 23:45:00 [DONE] 导诊预约链路修复并完成真实流程复测
- 修复：AI 创建预约时传递 `sessionId`，预约服务保存 `appointment.session_id`；会话结束时根据 `sessionId` 反查预约并生成带 `appointmentId` 的导诊摘要。
- 修复：当 Spring AI 工具调用已真实创建预约但未持久化 `role=tool/toolName=createAppointment` 消息时，`ChatServiceImpl` 会按患者和号源反查既有预约并绑定会话，不再把真实成功误判为“模型假成功”。
- 验证：新增/更新 `ChatServiceImplTest` 与 `AppointmentServiceImplTest`，受影响单测全部通过；未使用旧 `tests/` Python 集成测试。
- Docker：已无缓存重建并重启 `appointment-service`、`ai-service`，当前容器使用新镜像。
- 真实复测：患者 `patient_test` 通过数字人导诊预约李四医生 `doctorId=2`，生成 `sessionId=247`、`appointmentId=78`、`slotId=459`；患者预约记录可见，医生端预约列表可见，医生端按预约查询导诊摘要返回 `summaryId=44` 且 `appointmentId=78`。

## 2026-05-19 23:10:26 [DONE_WITH_CONCERNS] 换医生复测确认问题不依赖赵六医生
- 执行：按用户要求更换医生复测，选择 `李四 doctorId=2`、医生端账号 `lisi/123456`，症状改为咳嗽三天、发热、嗓子痛；目标号源为 `2026-05-21 morning 08:00-12:00`、`slotId=458`。
- 结果：有效复测会话 `sessionId=246`；AI 推荐内科李四医生并查询到目标号源；实际创建预约 `appointmentId=77`，患者端预约列表可见，医生端李四预约列表可见。
- 复现错误：AI 日志显示 `AppointmentTool` 调用 `createAppointment(patientId=5, doctorId=2, slotId=458)`，模型原始回复包含“预约成功”，但 `guardAppointmentSuccessReply` 继续因缺少 `role=tool/toolName=createAppointment` 落库消息而误判，最终 assistant 消息被改写为“尚未成功创建预约”。
- 复现错误：预约 `77` 的 `appointment.session_id` 为 `NULL`，摘要 `summaryId=43` 的 `conversation_summary.appointment_id` 为 `NULL`；医生端按预约查询摘要仍返回 `null`。
- 结论：两类问题不依赖赵六医生或神经内科场景，在李四/内科/咳嗽发热场景下同样稳定复现。

## 2026-05-19 23:04:21 [DONE_WITH_CONCERNS] 第三次复测确认问题稳定复现
- 执行：继续使用真实网关和 Docker 后端复测，未使用旧集成测试；为避开已有预约，选择新号源 `2026-05-21 morning 08:00-12:00`、`slotId=446`。
- 结果：有效复测会话 `sessionId=245`，患者中文消息正常落库；AI 完成导诊、推荐神经内科赵六医生并查询到目标号源；实际创建预约 `appointmentId=76`，患者端预约列表可见，医生端 `2026-05-21` 预约列表可见。
- 复现错误：最终 assistant 消息仍被改写为“尚未成功创建预约”；AI 日志显示 `AppointmentTool` 已调用 `createAppointment(patientId=5, doctorId=4, slotId=446)`，但 `chat_message` 仍没有 `role=tool/toolName=createAppointment` 落库消息，`guardAppointmentSuccessReply` 继续误判。
- 复现错误：预约 `76` 的 `appointment.session_id` 为 `NULL`，摘要 `summaryId=42` 的 `conversation_summary.appointment_id` 为 `NULL`；医生端按预约查询摘要仍返回 `null`。

## 2026-05-19 23:00:00 [DONE_WITH_CONCERNS] 二次复测继续定位预约链路错误
- 执行：再次基于真实网关手工复测，未使用旧集成测试；为避开上午号源 `slotId=252` 的去重影响，改测赵六医生 `2026-05-20 afternoon 14:00-18:00` 的 `slotId=253`。
- 结果：有效复测会话 `sessionId=244` 中文消息落库正常；AI 推荐赵六医生并查询到上午/下午号源；最终实际创建预约 `appointmentId=75`，患者端预约列表和医生端预约列表均可见。
- 新确认错误 1：AI 服务日志显示 `AppointmentTool` 已调用 `createAppointment(patientId=5, doctorId=4, slotId=253)`，数据库也新增预约 `75`，但最终 assistant 消息仍被改写为“尚未成功创建预约”。根因是 `guardAppointmentSuccessReply` 只检查 `chat_message` 中 `role=tool/toolName=createAppointment` 的落库消息；当前 Spring AI 工具调用结果没有落库为 tool 消息，守卫误判为“模型假成功”。
- 新确认错误 2：预约 `75` 的 `appointment.session_id` 仍为 `NULL`，摘要 `summaryId=41` 的 `conversation_summary.appointment_id` 仍为 `NULL`，医生端按预约查询摘要仍返回 `null`。
- 排除项：中间一次 `sessionId=243` 出现摘要“未提及/乱码”，根因是临时测试脚本直接在 PowerShell here-string 写中文导致请求体入库为 `????`，不是产品链路缺陷；有效复测已使用 Unicode 转义规避并确认中文正常入库。

## 2026-05-19 22:50:00 [DONE_WITH_CONCERNS] 后端患者导诊预约到医生端联调完成
- 执行：按用户要求未使用 `tests/` 旧集成测试；基于真实网关 `http://localhost:8080/api` 手工脚本完成患者登录、数字人导诊对话、AI 创建预约、患者预约列表/详情、医生登录、医生预约列表、医生按预约查看导诊摘要的全流程验证。
- 结果：患者 `patient_test` 登录成功；数字人导诊会话 `sessionId=242` 生成；AI 工具创建预约 `appointmentId=74`，预约医生为赵六 `doctorId=4`，号源 `slotId=252`，时间为 `2026-05-20 morning 08:00-12:00`；患者端预约记录和医生端预约列表均可见该预约。
- 关注点：导诊摘要按会话已生成 `summaryId=39`，但 `conversation_summary.appointment_id` 为 `NULL`，预约详情 `appointment.session_id` 也为 `NULL`；医生端按预约 `GET /api/ai/summary/appointment/74` 返回 `null`，因此医生端患者摘要页无法展示本次导诊摘要。
- 根因链路：AI 预约工具 `AppointmentTool` 通过 Feign `RemoteAppointmentService.createAppointment(patientId, doctorId, slotId)` 调用 `/appointment/inner/create`，未传 `sessionId`；`ChatServiceImpl.endSession` 结束 TRIAGE 会话时调用 `summaryService.generateSummary(sessionId, null)`，也未把摘要绑定到预约。

## 2026-05-19 23:50:00 [NEEDS_CONTEXT] 后端流程测试前 Docker 无缓存重编译失败
- 现象：按用户要求执行 Docker 无缓存重编译，`gateway`、`admin-web`、`live2d-h5` 已构建成功，但 `user-service` 在镜像内 Maven 构建阶段失败，进而取消了并行中的 `ai-service`、`doctor-service`、`appointment-service`、`knowledge-service` 构建。
- 栈路：`docker compose build --no-cache user-service` -> `medical-user-service/Dockerfile` -> `mvn -q -DskipTests -pl medical-service/medical-user-service -am package` -> 解析 `medical-common-security` 依赖。
- 根因假设：失败点是下载 `org.skyscreamer:jsonassert:pom:1.5.3` 时 Maven Central TLS 握手被远端断开，属于镜像构建阶段网络/仓库访问问题；同时 `user-service`、`ai-service`、`knowledge-service` Dockerfile 未像 `doctor-service`/`appointment-service`/`gateway` 一样显式写入 Maven settings，构建可重复性偏弱。
- 单点验证：先单独重试失败服务构建，若仍失败，再考虑对缺少 Maven settings 的 Dockerfile 做最小一致性修复后重建。

## 2026-05-16 11:02:00 [NEEDS_CONTEXT] 号源查询返回空排查假设
- 现象：小程序/AI 查询赵六医生 2026-05-18（周一）提示没有可预约号源，但数据库存在赵六医生周一启用排班模板。
- 栈路：AI `DoctorSearchTool.getAvailableSlots` -> Feign `RemoteScheduleService.getAvailableSlots` -> doctor-service `/schedule/inner/slots` -> `ScheduleServiceImpl.getAvailableSlots`。
- 数据验证：`doctor_profile` 中赵六 doctorId=4；`schedule_template` 中 doctorId=4、day_of_week=1 有 morning/afternoon 启用模板；`schedule_slot` 中 doctorId=4、schedule_date=2026-05-18 没有每日号源行；直连 `http://localhost:8082/schedule/slots?doctorId=4&date=2026-05-18` 返回空数组。
- 假设：当前服务只查每日 `schedule_slot`，当未来日期尚未由定时任务/手工生成每日行时，没有按周模板即时物化，导致“有排班模板但查不到号源”。

## 2026-05-16 11:08:00 [DONE] 预约号源查询修复
- 修复：`ScheduleServiceImpl.getAvailableSlots` 在当天没有任何 `schedule_slot` 行时，会读取该医生当天星期的启用 `schedule_template`，即时生成每日号源并返回，同时缓存结果；若当天已有号源行但均不可约，则仍返回空，避免重复生成。
- 测试：新增 `getAvailableSlots_shouldMaterializeSlotsFromActiveTemplateWhenRowsAreMissing`，先红后绿；同时修复 `ScheduleServiceSentinelTest` 清理 Sentinel 规则、`DoctorProfileServiceImplTest` 科室关系桩数据，保证 doctor-service 全量测试可稳定通过。
- 验证：`mvn test -pl medical-service/medical-doctor-service -f medical-ai/pom.xml` 通过；`mvn test -pl medical-service/medical-ai-service -am -f medical-ai/pom.xml "-Dtest=DoctorSearchToolTest" "-Dsurefire.failIfNoSpecifiedTests=false"` 通过。

## 2026-05-16 10:41:29 [DONE] 小程序发送按钮解锁时机调整
- 根因：`medical-mp/src/pages/chat/chat.vue` 原先只在 SSE `onComplete` 中释放 `isSending`，而该连接会继续承载 TTS 事件，导致按钮要等 TTS 返回/流结束后才能再次发送。
- 修复：收到文本完成事件 `payload.type === 'complete'` 后立即释放发送锁；SSE `onComplete` 保留为兜底；同时增加 `activeSendTurnId` 轮次校验，避免上一轮 SSE 收尾影响下一轮发送状态。
- 验证：`npm run type-check --prefix medical-mp` 通过。

## 2026-05-12
- 已创建本次 UniApp 重构的计划与发现记录文件。
- 已启动后台探查任务，等待项目结构与视觉问题总结。
- 发现 planning-with-files 的 session-catchup 脚本路径在当前环境下未直接可用，暂记为环境问题。

## 2026-05-12 - 数字人表情动作调研
- 已并行调研前端 Live2D/TTS/聊天编排、后端 AI 流式与工具调用链路、以及 Live2D/Cubism 的外部实现模式。
- 已确认当前系统可用于驱动表情动作的关键信号：`agentType`、tool 名称、SSE `complete/tts`、消息 metadata、summary 的 severity/assessment。
- 当前阶段仅输出方案，不做代码修改。

## 2026-05-12 - 数字人实现与验收
- 已通过子代理完成数字人前后端联动实现：后端生成 `avatarCue` metadata，前端消费 bucket 驱动 Live2D 表情/动作。
- 已补齐前端稳定性修复：去除 `complete` / `tts` 双触发；播放结束和新会话时显式回到中性状态。
- `medical-mp` 验证结果：`npm run type-check` 通过，`npm run build:mp-weixin` 通过。
- `medical-ai-service` 验证结果：使用 `mvn test -pl medical-service/medical-ai-service -am -f medical-ai/pom.xml "-Dtest=ChatServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false"` 后测试通过。

## 2026-05-12 - Docker 与管理端联调
- 已用 Docker 启动后端编排。
- 已定位 `doctor-service` / `appointment-service` 退出根因是 Seata 配置源错误，并通过 `SEATA_CONFIG_TYPE=file` 修复，两者现已正常启动。
- 已定位 `medical-admin-web` 的 `/api/api/...` 问题根因是旧 bundle 中 `baseURL` 与接口前缀重复叠加。
- 当前源码侧已修正 `medical-admin/.env.development`，同时对正在运行的 `medical-admin-web` 容器做过热修补以恢复登录与 SSE 路径。

## 2026-05-12 - 小程序 TTS 播放修复
- 已确认后端 `TtsServiceImpl` 仍固定生成 `.mp3`，`ChatController` 也以 `audio/mpeg` 返回，问题不在 TTS 文件产出链路。
- 已定位前端根因：`medical-mp/src/components/TtsPlayer.vue` 直接使用 `uni.downloadFile()` 返回的 `tempFilePath` 作为播放源，微信开发者工具环境下该临时路径被落成 `http://tmp/*.txt`，导致 `InnerAudioContext` 解码失败。
- 已修复为微信小程序显式下载到 `wx.env.USER_DATA_PATH` 下的 `.mp3` 文件名，并补充播放竞态保护，避免旧下载结果覆盖新片段播放。

## 2026-05-13 - 本地微服务启动修复
- 已定位除网关外服务统一启动失败的根因：`medical-common-security` 中 `InternalApiAuthProperties` 对 `security.internal-api.secret` 做了 `@NotBlank` 强校验，而各业务服务主 `application.yml` 未提供该值。
- 已为 5 个业务服务补充本地开发默认配置 `${SECURITY_INTERNAL_API_SECRET:local-dev-internal-secret}`，保留环境变量覆盖能力，避免本地非 Docker 启动在属性绑定阶段失败。
- 已同步更新 README 的环境变量与本地启动说明，强调多服务本地联调时该密钥必须保持一致。

## 2026-05-15 - Seata 本地启动路径补齐
- 已确认 `doctor-service` / `appointment-service` 的 Seata 启动失败来自本地缺少 Seata 配置中心数据，默认 Nacos 语义保持不变。
- 已为这两个服务补充 `application-local.yml`，仅将 Seata 配置源切换为 file，保留其作为事务参与者的角色。
- 已更新 README 的本地启动步骤，补充 `seata-server` 前置条件与 `local` profile 启动方式。
