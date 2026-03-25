# RabbitMQ 副作用异步化设计

> 日期：2026-03-25
> 状态：已确认（Draft v1）
> 范围：仅做副作用异步化，不改预约主链路同步语义

## 1. 背景与目标

当前预约主链路已经通过 `HTTP + Seata AT` 实现了 `appointment-service` 与 `doctor-service` 之间的同步强一致。下一步引入 RabbitMQ 的目标不是替换这条主链路，而是把预约成功/取消后的非核心副作用从同步请求线程中剥离出来，实现：

- 削峰：将突发副作用流量从 Web 请求线程转移到 MQ 消费端
- 异步：减少预约接口响应时间和尾延迟
- 解耦：通知、统计、审计、随访等副作用不再与预约接口强耦合

本次明确 **不改变** 以下语义：

- `/appointment/appointment` 与 `/appointment/{id}/cancel` 继续同步返回业务结果
- 号源扣减/释放与预约记录写入继续由 Seata 保证强一致
- RabbitMQ 不参与预约主事务协调，只承接事务成功后的领域事件

## 2. 设计原则

### 2.1 系统边界

- `appointment-service`：唯一事件生产者（当前阶段）
- RabbitMQ：事件投递与削峰缓冲层
- 各副作用消费者：通知、统计、审计、后续摘要/提醒/随访

### 2.2 一致性原则

- 主交易成功是前提，副作用异步最终一致
- 事件投递失败不能反向影响预约主交易提交
- 每个消费者必须支持幂等，避免重复消费导致脏数据

### 2.3 演进原则

- 第一阶段仅服务于副作用异步化
- 事件命名、交换机与消息体设计要预留未来扩展空间
- 不在第一阶段引入“全系统事件总线”复杂度

## 3. 方案选型

### 方案 A：`appointment-service` 直接发布领域事件（推荐）

由 `appointment-service` 在预约创建/取消成功后向 RabbitMQ 发布领域事件，各消费端独立监听。

优点：

- 改动小，最符合当前项目阶段
- 事件边界清晰，预约域自己对外发事件
- 测试成本低，便于先落地第一批场景

缺点：

- 生产者逻辑集中在预约服务
- 后续事件种类变多后需要再抽象发布层

### 方案 B：独立 message/notification 服务统一接入 MQ

额外新建消息服务，预约服务只调用该服务，由消息服务负责 MQ 投递与下游分发。

优点：

- 平台化边界更清晰
- 未来更容易扩展成统一消息中心

缺点：

- 现在会引入额外微服务与网络跳数
- 对当前“仅做副作用异步化”来说过重

**结论：采用方案 A。**

## 4. 业务范围与分批落地

### 第一批：通知事件

- 预约创建成功通知
- 预约取消成功通知

### 第二批：统计与审计

- 预约创建/取消计数
- 业务行为埋点
- 审计日志记录

### 第三批：延迟容忍型任务

- 会话摘要生成
- 就诊提醒
- 随访任务派发

## 5. 消息模型设计

### 5.1 Exchange 与 Routing Key

- Exchange：`medical.event`
- 类型：`topic`

Routing Key 约定：

- `appointment.created`
- `appointment.cancelled`

后续扩展预留：

- `appointment.reminder.due`
- `conversation.summary.requested`
- `followup.task.created`

### 5.2 队列规划

第一批：

- `medical.notification.queue`
- `medical.notification.dlq`

第二批：

- `medical.metrics.queue`
- `medical.audit.queue`
- 各自对应 DLQ

第三批：

- `medical.summary.queue`
- `medical.reminder.queue`
- `medical.followup.queue`

### 5.3 消息体

统一事件信封：

```json
{
  "eventId": "uuid",
  "eventType": "appointment.created",
  "occurredAt": "2026-03-25T19:30:00Z",
  "producer": "medical-appointment-service",
  "traceId": "optional-trace-id",
  "data": {
    "appointmentId": 123,
    "patientId": 45,
    "doctorId": 67,
    "departmentId": 8,
    "slotId": 999,
    "status": 0
  }
}
```

约束：

- 只放最小必要字段，不在消息体塞完整业务快照
- 需要更多信息的消费者自行查库或调用服务

## 6. 生产者设计

生产者放在 `appointment-service` 内。

### 6.1 发布时机

推荐顺序：

1. Seata 全局事务成功提交
2. 本地事件发布器发送 RabbitMQ 消息

这里有两种实现层级：

- `V1（可快速落地）`：事务提交后发布消息
- `V2（推荐终态）`：Outbox 表 + 定时/后台发布器

### 6.2 推荐实现：Outbox

在 `medical_appointment` 库新增本地事件表，例如 `appointment_event_outbox`：

- 与主事务同库同事务写入
- 预约成功/取消时同时写出 Outbox 记录
- 独立 publisher 扫描未发布事件并投递到 RabbitMQ
- 发布成功后更新 `published` 状态

这样可以避免：

- 主交易提交了但消息没发出去
- Web 请求线程直接依赖 MQ 可用性

### 6.3 为什么不用 RabbitMQ 替代 Seata

因为当前目标不是把主链路做成最终一致。预约创建/取消要求用户即时知道是否成功，且号源状态必须同步确定；Seata 更适合当前主交易，RabbitMQ 只用于副作用异步化。

## 7. 消费者设计

### 7.1 通知消费者

职责：

- 消费 `appointment.created` / `appointment.cancelled`
- 先写本地通知记录或日志
- 后续可扩展成站内信、短信、微信模板消息等

### 7.2 统计消费者

职责：

- 异步更新预约成功/取消统计指标
- 减少主链路里统计逻辑侵入

### 7.3 审计消费者

职责：

- 记录审计事件
- 保存操作主体、资源 ID、事件类型、时间、来源服务

### 7.4 摘要/提醒/随访消费者

职责：

- 面向延迟容忍任务
- 可以按队列独立扩缩容，不挤占主链路资源

## 8. 幂等、重试与失败处理

### 8.1 幂等

每个消费者必须基于 `eventId` 或 `eventType + appointmentId` 做幂等控制。

推荐方式：

- 新建消费去重表
- 或者在目标业务表上建立唯一约束

### 8.2 Ack 策略

- 使用手动 ack
- 业务成功后 ack
- 可重试异常 → nack/requeue 或转入重试队列
- 不可恢复异常 → 入 DLQ

### 8.3 死信队列

每类消费者都应绑定 DLQ，便于：

- 排查毒消息
- 补偿重放
- 答辩中展示“可靠消息处理”能力

## 9. 基础设施设计

### 9.1 Docker Compose

在 `medical-ai/docker/docker-compose.yml` 中新增：

- `rabbitmq:3-management`
- 管理端口 `15672`
- AMQP 端口 `5672`

### 9.2 服务配置

至少 `appointment-service` 需要：

- RabbitMQ 连接配置
- exchange / queue / binding 声明

后续有独立消费者的服务模块时，再为相应服务追加消费者配置。

## 10. 对现有系统的影响

### 不变部分

- Controller API
- 前端交互语义
- 预约主事务同步成功/失败返回
- Seata 主链路

### 变更部分

- 预约服务新增事件发布逻辑
- 新增 RabbitMQ 容器与配置
- 新增若干消费者模块/类
- 新增 Outbox/消费去重/审计记录等表（按实现阶段决定）

## 11. 测试策略

### 单元测试

- 事件构造正确性
- Outbox 状态流转
- 消费者幂等行为

### 集成测试

- 预约成功后 Outbox 记录生成
- publisher 成功投递 RabbitMQ
- 消费者收到消息并落审计/统计/通知结果

### 运行态验证

- Docker Compose 启动 RabbitMQ + 微服务
- 执行预约创建/取消
- 检查队列消费、DLQ、业务副作用结果

### 回归验证

- 现有 Seata 同步链路不能退化
- 现有 `tests/test_07_appointment.py` 与 `tests/test_10_seata.py` 语义保持成立

## 12. 风险与约束

- 第一阶段不要把 RabbitMQ 引入主交易决策流程
- 如果不做 Outbox，消息可靠性会弱于主交易可靠性
- 如果消费者不做幂等，重试会造成重复通知/重复统计
- 如果把过多字段塞进消息体，会增加版本演进成本与服务耦合

## 13. 结论

本次 RabbitMQ 方案定位明确：

- **主链路继续同步强一致**
- **副作用改为异步最终一致**
- **先从预约通知、统计、审计落地，再扩展到摘要/提醒/随访**

该方案既能体现消息队列在“削峰、异步、解耦”上的工程价值，又不会破坏当前已完成的 Seata 同步事务设计，适合作为当前项目的稳妥演进路径。
