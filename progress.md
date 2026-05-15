# 进度日志

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
