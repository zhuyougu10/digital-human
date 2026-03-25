# Sentinel Protection Design

## Goal

为 AI 数字人医疗小助手系统引入第一批 Sentinel 限流、熔断与降级保护，优先覆盖登录、AI 对话 SSE、TTS、知识检索、预约创建与号源查询等高风险链路，在高峰或外部依赖故障时优先保护核心业务可用性。

## Scope

- `medical-gateway`：统一入口限流
- `medical-ai-service`：SSE 主链路、TTS、内部 AI 重依赖
- `medical-knowledge-service`：知识检索入口、embedding 调用
- `medical-appointment-service`：预约创建热点保护
- `medical-doctor-service`：号源查询热点保护

## Non-Goals

- 第一版不接入 Sentinel Dashboard
- 第一版不做 Nacos 动态规则推送
- 第一版不覆盖所有 API，只保护最关键高风险链路
- 第一版不改前端交互协议，只在后端返回现有风格的业务降级响应

## Architecture

采用混合方案：

1. 网关层做统一入口限流，优先挡住突发流量、恶意刷接口与过量 SSE 建连。
2. 业务服务内部对重资源做熔断、降级与热点参数保护，确保外部依赖抖动时系统能快速失败并保留核心主链路。
3. 第一版规则先用本地代码初始化，待规则阈值跑稳后再迁移到 Nacos 动态数据源。

## Resource Naming

### Gateway Resources

- `gw:auth:login`
- `gw:auth:wxLogin`
- `gw:ai:chatSend`
- `gw:knowledge:search`
- `gw:appointment:create`

### Service Resources

- `svc:ai:chatStream`
- `svc:ai:tts`
- `svc:knowledge:search`
- `svc:knowledge:innerSearch`
- `svc:knowledge:embed`
- `svc:appointment:create`
- `svc:doctor:scheduleSlots`

## First Batch Rules

### Gateway

1. `gw:auth:login`
   - Type: QPS flow control
   - Initial threshold: 10~20 QPS / instance
   - Fallback: `R.fail("登录请求过于频繁，请稍后再试")`

2. `gw:auth:wxLogin`
   - Type: QPS flow control
   - Initial threshold: 5~10 QPS / instance
   - Fallback: `R.fail("微信登录服务繁忙，请稍后重试")`

3. `gw:ai:chatSend`
   - Type: QPS flow control
   - Initial threshold: 3~5 QPS / instance
   - Fallback: JSON busy response before SSE stream is established

4. `gw:knowledge:search`
   - Type: QPS flow control
   - Initial threshold: 5~10 QPS / instance
   - Fallback: `R.fail("知识检索服务繁忙，请稍后重试")`

### Services

1. `svc:ai:chatStream`
   - Type: thread-count + slow-call degrade
   - Initial threshold: 10~20 concurrent, slow call 8~10s
   - Fallback: return business busy result / SSE error event without starting tool/TTS expansion

2. `svc:ai:tts`
   - Type: slow-call degrade + exception-ratio degrade
   - Initial threshold: slow call 5~8s, exception ratio 30%~50%
   - Fallback: skip audio generation, keep text response only

3. `svc:knowledge:search` / `svc:knowledge:innerSearch`
   - Type: slow-call degrade + exception-ratio degrade
   - Fallback:
     - external API: busy/fail response
     - internal tool search: empty result list

4. `svc:knowledge:embed`
   - Type: slow-call degrade + exception-ratio degrade
   - Initial threshold: slow call 3~5s, exception ratio 20%~30%
   - Fallback: external request fast-fail, internal request empty result or explicit unavailable marker

5. `svc:appointment:create`
   - Type: QPS flow control + param flow rule on `slotId`
   - Initial threshold: 5~10 QPS overall, 1~2 req/s per hot slot
   - Fallback: `R.fail("当前号源繁忙，请刷新后重试")`

6. `svc:doctor:scheduleSlots`
   - Type: param flow rule on `doctorId` + `date`
   - Fallback: empty slot list or busy result

## Fallback Strategy

- AI 对话主链路：优先保文本，舍弃 TTS 和非必要工具扩展
- 知识检索：优先保主对话与主页面稳定，检索失败时返回空结果或业务失败，不拖慢主链路
- 预约：快速失败，不长时间占用线程等待跨服务链路恢复
- 登录：统一返回频率限制提示，不暴露内部依赖状态

## Implementation Plan Summary

1. 引入 Sentinel 依赖与基础配置
2. 在网关接入入口资源与统一 block 响应
3. 在 `ai-service` 接入 chat/TTS 资源与降级逻辑
4. 在 `knowledge-service` 接入 search/embed 资源与降级逻辑
5. 在 `appointment-service` 和 `doctor-service` 接入热点参数保护
6. 加入本地规则初始化与最小测试验证
7. 第二阶段再迁移规则到 Nacos

## Risks

- SSE 接口若直接使用传统 JSON fallback，需要兼容前端现有异常处理逻辑
- 预约与号源热点限流阈值需要基于真实流量调优，首发值只能保守设置
- 若后续接入 Nacos 动态规则，需明确配置优先级，避免本地规则与远端规则冲突
