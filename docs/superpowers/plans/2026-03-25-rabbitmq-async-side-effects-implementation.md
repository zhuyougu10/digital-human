# RabbitMQ 副作用异步化 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为预约域引入 RabbitMQ 副作用异步化能力，在不改变 `HTTP + Seata` 主交易语义的前提下，实现预约通知、统计、审计等副作用的削峰、异步与解耦。

**Architecture:** 保持 `appointment-service` 继续同步完成预约创建/取消主链路，在主事务内写入 Outbox 事件，再由后台 publisher 投递到 RabbitMQ。通知、统计、审计通过独立消费者订阅 `appointment.created` / `appointment.cancelled`，并要求手动 ack、DLQ 与幂等消费。

**Tech Stack:** Spring Boot 3.3.6, Spring AMQP, RabbitMQ 3 Management, MyBatis-Plus, MySQL 8.0, Docker Compose, pytest

---

## File Structure

| 文件 | 职责 | 操作 |
|------|------|------|
| `medical-ai/pom.xml` | 父 POM 统一管理 AMQP 依赖版本 | Modify |
| `medical-ai/docker/docker-compose.yml` | 新增 RabbitMQ 基础设施 | Modify |
| `medical-ai/docker/mysql/init/rabbitmq-outbox-init.sql` | 初始化 Outbox / 消费幂等 / 通知表 | Create |
| `medical-ai/medical-service/medical-appointment-service/pom.xml` | 引入 Spring AMQP | Modify |
| `medical-ai/medical-service/medical-appointment-service/src/main/resources/application.yml` | RabbitMQ 连接与 MQ 开关配置 | Modify |
| `medical-ai/medical-service/medical-appointment-service/src/test/resources/application-test.yml` | 测试环境关闭 MQ 真实连接 | Modify |
| `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/config/RabbitMqConfig.java` | Exchange / Queue / Binding 声明 | Create |
| `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/domain/entity/AppointmentEventOutbox.java` | Outbox 实体 | Create |
| `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/domain/entity/AppointmentNotificationRecord.java` | 通知落库实体（第一批） | Create |
| `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/domain/entity/AppointmentAuditRecord.java` | 审计落库实体（第二批） | Create |
| `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/domain/entity/AppointmentEventConsumeLog.java` | 幂等消费记录实体 | Create |
| `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/domain/vo/AppointmentDomainEvent.java` | MQ 统一事件信封 | Create |
| `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/mapper/AppointmentEventOutboxMapper.java` | Outbox mapper | Create |
| `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/mapper/AppointmentNotificationRecordMapper.java` | 通知 mapper | Create |
| `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/mapper/AppointmentAuditRecordMapper.java` | 审计 mapper | Create |
| `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/mapper/AppointmentEventConsumeLogMapper.java` | 幂等日志 mapper | Create |
| `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/service/AppointmentEventOutboxService.java` | Outbox 服务接口 | Create |
| `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/service/impl/AppointmentEventOutboxServiceImpl.java` | Outbox 记录落盘 | Create |
| `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/service/AppointmentEventPublisher.java` | 扫描 Outbox 并发布消息 | Create |
| `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/service/impl/AppointmentEventPublisherImpl.java` | RabbitTemplate 发布实现 | Create |
| `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/job/AppointmentOutboxPublishJob.java` | 定时发布未投递事件 | Create |
| `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/messaging/AppointmentNotificationConsumer.java` | 第一批通知消费者 | Create |
| `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/messaging/AppointmentMetricsConsumer.java` | 第二批统计消费者 | Create |
| `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/messaging/AppointmentAuditConsumer.java` | 第二批审计消费者 | Create |
| `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/service/impl/AppointmentServiceImpl.java` | 主事务成功时写 Outbox | Modify |
| `medical-ai/medical-service/medical-appointment-service/src/test/java/com/medical/appointment/service/impl/AppointmentServiceImplTest.java` | 主事务 + Outbox 单测 | Modify |
| `medical-ai/medical-service/medical-appointment-service/src/test/java/com/medical/appointment/service/impl/AppointmentEventPublisherImplTest.java` | 发布器单测 | Create |
| `medical-ai/medical-service/medical-appointment-service/src/test/java/com/medical/appointment/messaging/AppointmentNotificationConsumerTest.java` | 通知消费者幂等单测 | Create |
| `medical-ai/medical-service/medical-appointment-service/src/test/java/com/medical/appointment/messaging/AppointmentMetricsConsumerTest.java` | 统计消费者单测 | Create |
| `medical-ai/medical-service/medical-appointment-service/src/test/java/com/medical/appointment/messaging/AppointmentAuditConsumerTest.java` | 审计消费者单测 | Create |
| `tests/test_11_rabbitmq_side_effects.py` | Docker 运行态 MQ 集成测试 | Create |
| `task_plan.md` | 增加 Phase 32 | Modify |
| `findings.md` | 补充 RabbitMQ 实施发现 | Modify |
| `progress.md` | 记录实现与验证结果 | Modify |

---

## Chunk 1: 基础设施与依赖接入

### Task 1: 引入 RabbitMQ 与 SQL 初始化脚本

**Files:**
- Modify: `medical-ai/pom.xml`
- Modify: `medical-ai/docker/docker-compose.yml`
- Create: `medical-ai/docker/mysql/init/rabbitmq-outbox-init.sql`

- [ ] **Step 1: 阅读父 POM 和 docker compose 现状**

Read:
- `medical-ai/pom.xml`
- `medical-ai/docker/docker-compose.yml`

Expected: 确认当前没有 Spring AMQP / RabbitMQ 容器定义。

- [ ] **Step 2: 父 POM 增加 AMQP 版本管理**

在 `medical-ai/pom.xml` 中确认 Spring Boot BOM 已管理 `spring-boot-starter-amqp`，优先直接复用 BOM，不额外硬编码版本；仅在注释或依赖管理说明中标明 RabbitMQ 由 Spring Boot BOM 管理。

- [ ] **Step 3: 在 Docker Compose 中新增 RabbitMQ**

在 `medical-ai/docker/docker-compose.yml` 中新增：

```yaml
  rabbitmq:
    image: rabbitmq:3-management
    container_name: medical-rabbitmq
    ports:
      - "5672:5672"
      - "15672:15672"
    environment:
      RABBITMQ_DEFAULT_USER: ${RABBITMQ_USER:-guest}
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD:-guest}
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "ping"]
      interval: 10s
      timeout: 5s
      retries: 10
    volumes:
      - rabbitmq-data:/var/lib/rabbitmq
```

并在 `volumes:` 段增加：

```yaml
  rabbitmq-data:
```

- [ ] **Step 4: 创建 SQL 初始化脚本**

在 `medical-ai/docker/mysql/init/rabbitmq-outbox-init.sql` 中创建如下结构（可按仓库命名风格微调，但字段语义必须一致）：

```sql
USE `medical_appointment`;

CREATE TABLE IF NOT EXISTS `appointment_event_outbox` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `event_id` VARCHAR(64) NOT NULL,
  `event_type` VARCHAR(64) NOT NULL,
  `aggregate_id` BIGINT NOT NULL,
  `payload` JSON NOT NULL,
  `publish_status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=pending,1=published,2=failed',
  `retry_count` INT NOT NULL DEFAULT 0,
  `last_error` VARCHAR(500) DEFAULT NULL,
  `published_at` DATETIME DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_id` (`event_id`),
  KEY `idx_status_create_time` (`publish_status`, `create_time`)
);

CREATE TABLE IF NOT EXISTS `appointment_event_consume_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `event_id` VARCHAR(64) NOT NULL,
  `consumer_name` VARCHAR(64) NOT NULL,
  `consume_status` TINYINT NOT NULL DEFAULT 1,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_consumer` (`event_id`, `consumer_name`)
);

CREATE TABLE IF NOT EXISTS `appointment_notification_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `event_id` VARCHAR(64) NOT NULL,
  `appointment_id` BIGINT NOT NULL,
  `patient_id` BIGINT NOT NULL,
  `notification_type` VARCHAR(32) NOT NULL,
  `content` VARCHAR(500) NOT NULL,
  `status` TINYINT NOT NULL DEFAULT 0,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_notification` (`event_id`, `notification_type`)
);

CREATE TABLE IF NOT EXISTS `appointment_audit_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `event_id` VARCHAR(64) NOT NULL,
  `event_type` VARCHAR(64) NOT NULL,
  `appointment_id` BIGINT NOT NULL,
  `operator_id` BIGINT DEFAULT NULL,
  `details` VARCHAR(1000) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_audit` (`event_id`)
);
```

- [ ] **Step 5: 验证 YAML 与 SQL 文件存在**

Run:

```bash
python -c "import yaml; yaml.safe_load(open('medical-ai/docker/docker-compose.yml', encoding='utf-8'))"
ls "medical-ai/docker/mysql/init/rabbitmq-outbox-init.sql"
```

Expected: YAML 解析通过，SQL 文件存在。

---

### Task 2: appointment-service 接入 Spring AMQP 配置

**Files:**
- Modify: `medical-ai/medical-service/medical-appointment-service/pom.xml`
- Modify: `medical-ai/medical-service/medical-appointment-service/src/main/resources/application.yml`
- Modify: `medical-ai/medical-service/medical-appointment-service/src/test/resources/application-test.yml`

- [ ] **Step 1: 为 appointment-service 增加依赖**

在 `medical-ai/medical-service/medical-appointment-service/pom.xml` 中添加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

- [ ] **Step 2: 在生产配置追加 RabbitMQ 配置**

在 `medical-ai/medical-service/medical-appointment-service/src/main/resources/application.yml` 末尾追加：

```yaml
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USER:guest}
    password: ${RABBITMQ_PASSWORD:guest}
    listener:
      simple:
        acknowledge-mode: manual
        prefetch: 10

medical:
  mq:
    enabled: true
    exchange: medical.event
    notification-queue: medical.notification.queue
    notification-dlq: medical.notification.dlq
    metrics-queue: medical.metrics.queue
    metrics-dlq: medical.metrics.dlq
    audit-queue: medical.audit.queue
    audit-dlq: medical.audit.dlq
```

注意：如果文件已存在 `spring:` 根节点，则合并到现有节点，不能创建重复根键。

- [ ] **Step 3: 测试配置禁用真实 MQ 连接**

在 `medical-ai/medical-service/medical-appointment-service/src/test/resources/application-test.yml` 追加：

```yaml
medical:
  mq:
    enabled: false

spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration
```

若已有 `spring.autoconfigure.exclude`，则合并而不是覆盖。

- [ ] **Step 4: 编译验证**

Run:

```bash
mvn -q compile -pl medical-service/medical-appointment-service -am -DskipTests -f medical-ai/pom.xml
```

Expected: BUILD SUCCESS。

---

## Chunk 2: 事件模型、Outbox 与发布器

### Task 3: 建立事件实体与 Mapper

**Files:**
- Create: `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/domain/entity/AppointmentEventOutbox.java`
- Create: `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/domain/entity/AppointmentNotificationRecord.java`
- Create: `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/domain/entity/AppointmentAuditRecord.java`
- Create: `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/domain/entity/AppointmentEventConsumeLog.java`
- Create: `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/domain/vo/AppointmentDomainEvent.java`
- Create: `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/mapper/AppointmentEventOutboxMapper.java`
- Create: `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/mapper/AppointmentNotificationRecordMapper.java`
- Create: `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/mapper/AppointmentAuditRecordMapper.java`
- Create: `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/mapper/AppointmentEventConsumeLogMapper.java`

- [ ] **Step 1: 参考现有实体风格创建 4 个表实体**

要求：

- 继承 `BaseEntity`（若字段模型明显不适合，可保留独立字段，但要遵循现有实体风格）
- 使用 `@TableName`
- 使用 `@TableId`
- 命名采用项目约定

- [ ] **Step 2: 创建统一事件信封 VO**

`AppointmentDomainEvent.java` 需至少包含：

```java
private String eventId;
private String eventType;
private LocalDateTime occurredAt;
private String producer;
private String traceId;
private EventData data;
```

内部 `EventData` 至少包含：

```java
private Long appointmentId;
private Long patientId;
private Long doctorId;
private Long departmentId;
private Long slotId;
private Integer status;
```

- [ ] **Step 3: 创建 4 个 Mapper**

每个 Mapper 均：

```java
public interface XxxMapper extends BaseMapper<Xxx> {
}
```

- [ ] **Step 4: 编译验证**

Run:

```bash
mvn -q compile -pl medical-service/medical-appointment-service -am -DskipTests -f medical-ai/pom.xml
```

Expected: BUILD SUCCESS。

---

### Task 4: 建立 RabbitMQ 配置与 Outbox 服务

**Files:**
- Create: `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/config/RabbitMqConfig.java`
- Create: `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/service/AppointmentEventOutboxService.java`
- Create: `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/service/impl/AppointmentEventOutboxServiceImpl.java`

- [ ] **Step 1: 创建 MQ 拓扑配置类**

`RabbitMqConfig.java` 中声明：

- topic exchange：`medical.event`
- 通知队列 + DLQ
- 统计队列 + DLQ
- 审计队列 + DLQ
- routing key 绑定：`appointment.created`、`appointment.cancelled`

- [ ] **Step 2: MQ 开关控制**

在配置类上加条件：

```java
@ConditionalOnProperty(prefix = "medical.mq", name = "enabled", havingValue = "true")
```

- [ ] **Step 3: 创建 Outbox 服务接口**

定义至少两个方法：

```java
void saveCreatedEvent(Appointment appointment);
void saveCancelledEvent(Appointment appointment);
```

- [ ] **Step 4: 实现 Outbox 落盘**

`AppointmentEventOutboxServiceImpl.java` 中：

- 构造 `AppointmentDomainEvent`
- JSON 序列化写入 `appointment_event_outbox.payload`
- `publish_status = 0`

允许使用 Jackson `ObjectMapper`。

- [ ] **Step 5: 为 Outbox 服务写单测骨架（先红）**

Create test file:
- `medical-ai/medical-service/medical-appointment-service/src/test/java/com/medical/appointment/service/impl/AppointmentEventOutboxServiceImplTest.java`

写一个最小测试，验证 `saveCreatedEvent()` 会调用 mapper 插入一条 `event_type=appointment.created` 的记录。

- [ ] **Step 6: 跑测试并修正实现**

Run:

```bash
mvn test -pl medical-service/medical-appointment-service -f medical-ai/pom.xml -Dtest=AppointmentEventOutboxServiceImplTest
```

Expected: PASS。

---

### Task 5: 将 Outbox 写入接到预约主事务

**Files:**
- Modify: `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/service/impl/AppointmentServiceImpl.java`
- Modify: `medical-ai/medical-service/medical-appointment-service/src/test/java/com/medical/appointment/service/impl/AppointmentServiceImplTest.java`

- [ ] **Step 1: 在 `AppointmentServiceImpl` 中注入 Outbox 服务**

新增构造注入：

```java
private final AppointmentEventOutboxService appointmentEventOutboxService;
```

- [ ] **Step 2: 创建预约成功后落 Outbox**

在 `appointmentMapper.insert(appointment);` 成功之后、返回之前调用：

```java
appointmentEventOutboxService.saveCreatedEvent(appointment);
```

- [ ] **Step 3: 取消预约成功后落 Outbox**

在 `appointmentMapper.updateById(appointment);` 成功之后调用：

```java
appointmentEventOutboxService.saveCancelledEvent(appointment);
```

- [ ] **Step 4: 扩展 `AppointmentServiceImplTest`**

新增测试：

- `createAppointment_shouldSaveOutboxEventAfterSuccess`
- `cancelAppointment_shouldSaveOutboxEventAfterSuccess`

验证：

- 主链路成功时调用 Outbox 服务
- Sentinel 热点限流失败时不调用 Outbox

- [ ] **Step 5: 运行 appointment-service 测试**

Run:

```bash
mvn test -pl medical-service/medical-appointment-service -f medical-ai/pom.xml -Dtest=AppointmentServiceImplTest,AppointmentControllerTest
```

Expected: PASS。

---

## Chunk 3: Publisher 与第一批通知消费者

### Task 6: 实现 Outbox Publisher

**Files:**
- Create: `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/service/AppointmentEventPublisher.java`
- Create: `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/service/impl/AppointmentEventPublisherImpl.java`
- Create: `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/job/AppointmentOutboxPublishJob.java`
- Create: `medical-ai/medical-service/medical-appointment-service/src/test/java/com/medical/appointment/service/impl/AppointmentEventPublisherImplTest.java`

- [ ] **Step 1: 创建 Publisher 接口**

定义方法：

```java
int publishPendingEvents(int limit);
```

- [ ] **Step 2: 实现 Publisher**

逻辑要求：

- 查询 `publish_status = 0` 的记录
- 逐条发送到 exchange `medical.event`
- routing key 直接使用 `eventType`
- 成功后更新 `publish_status = 1`, `published_at = now`
- 失败时 `retry_count + 1`, `publish_status` 保持 0 或置 2，并记录 `last_error`

- [ ] **Step 3: 创建定时任务**

`AppointmentOutboxPublishJob.java`：

- 使用 `@Scheduled(fixedDelay = 5000)`
- 调用 `publishPendingEvents(100)`
- 受 `medical.mq.enabled=true` 控制

- [ ] **Step 4: 写发布器单测（先红）**

至少覆盖：

- 发布成功后更新状态
- RabbitTemplate 抛异常时记录失败并增加重试次数

- [ ] **Step 5: 跑发布器测试**

Run:

```bash
mvn test -pl medical-service/medical-appointment-service -f medical-ai/pom.xml -Dtest=AppointmentEventPublisherImplTest
```

Expected: PASS。

---

### Task 7: 实现第一批通知消费者

**Files:**
- Create: `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/messaging/AppointmentNotificationConsumer.java`
- Create: `medical-ai/medical-service/medical-appointment-service/src/test/java/com/medical/appointment/messaging/AppointmentNotificationConsumerTest.java`

- [ ] **Step 1: 创建通知消费者类**

使用：

```java
@RabbitListener(queues = "${medical.mq.notification-queue}")
```

方法签名包含：

- `AppointmentDomainEvent event`
- `Channel channel`
- `Message message`

- [ ] **Step 2: 实现幂等控制**

消费前：

- 查询/插入 `appointment_event_consume_log`
- 若已消费过，直接 ack 返回

- [ ] **Step 3: 落通知记录**

按事件类型生成简单内容：

- `appointment.created` → “预约成功”
- `appointment.cancelled` → “预约已取消”

落库 `appointment_notification_record`

- [ ] **Step 4: ack / nack 处理**

- 成功：`basicAck`
- 业务不可恢复异常：`basicReject(requeue=false)`
- 瞬时异常：`basicNack(requeue=true)`

- [ ] **Step 5: 写通知消费者测试**

至少覆盖：

- 首次消费成功写通知并 ack
- 重复消费直接 ack，不重复写通知

- [ ] **Step 6: 跑通知消费者测试**

Run:

```bash
mvn test -pl medical-service/medical-appointment-service -f medical-ai/pom.xml -Dtest=AppointmentNotificationConsumerTest
```

Expected: PASS。

---

## Chunk 4: 第二批统计与审计消费者

### Task 8: 实现统计消费者

**Files:**
- Create: `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/messaging/AppointmentMetricsConsumer.java`
- Create: `medical-ai/medical-service/medical-appointment-service/src/test/java/com/medical/appointment/messaging/AppointmentMetricsConsumerTest.java`

- [ ] **Step 1: 创建统计消费者**

消费相同事件，但只做轻量统计落库/日志占位。

第一版允许使用：

- `log.info(...)` + 预留 TODO
- 或新增简单统计表（若决定落库）

但必须体现独立消费者边界与幂等处理。

- [ ] **Step 2: 复用幂等策略**

消费者名使用独立常量，例如：

```java
private static final String CONSUMER_NAME = "appointmentMetricsConsumer";
```

- [ ] **Step 3: 写测试**

验证成功消费与重复消费。

- [ ] **Step 4: 跑测试**

Run:

```bash
mvn test -pl medical-service/medical-appointment-service -f medical-ai/pom.xml -Dtest=AppointmentMetricsConsumerTest
```

Expected: PASS。

---

### Task 9: 实现审计消费者

**Files:**
- Create: `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/messaging/AppointmentAuditConsumer.java`
- Create: `medical-ai/medical-service/medical-appointment-service/src/test/java/com/medical/appointment/messaging/AppointmentAuditConsumerTest.java`

- [ ] **Step 1: 创建审计消费者**

消费事件后写 `appointment_audit_record`。

- [ ] **Step 2: details 字段写明事件摘要**

例如：

```java
String details = String.format("event=%s, appointmentId=%s, patientId=%s", ...);
```

- [ ] **Step 3: 加入幂等与 ack 逻辑**

与通知消费者保持一致。

- [ ] **Step 4: 写测试并执行**

Run:

```bash
mvn test -pl medical-service/medical-appointment-service -f medical-ai/pom.xml -Dtest=AppointmentAuditConsumerTest
```

Expected: PASS。

---

## Chunk 5: 运行态验证与规划更新

### Task 10: appointment-service 模块测试回归

**Files:**
- 无新增，运行验证

- [ ] **Step 1: 运行 appointment-service 全模块测试**

Run:

```bash
mvn test -pl medical-service/medical-appointment-service -f medical-ai/pom.xml
```

Expected: BUILD SUCCESS。

- [ ] **Step 2: 如失败，仅修复本次 RabbitMQ 相关回归**

不要顺手修 unrelated 代码。

---

### Task 11: Docker 运行态验证

**Files:**
- Create: `tests/test_11_rabbitmq_side_effects.py`

- [ ] **Step 1: 参考现有 Python 测试风格写新集成测试**

要求：

- 复用 `conftest.py` 中 `ApiClient`、`SharedState`、`assert_success`
- 单文件可独立运行
- 覆盖：
  - 创建预约后，Outbox 最终发布成功
  - RabbitMQ 消费后，通知记录存在
  - 审计记录存在

说明：若当前阶段统计仅做日志占位，则不要在测试中对统计落库做强断言。

- [ ] **Step 2: 先做语法校验**

Run:

```bash
python -m py_compile tests/test_11_rabbitmq_side_effects.py
```

Expected: 无报错。

- [ ] **Step 3: 打包 jar 供 Docker 使用**

Run:

```bash
mvn clean package -DskipTests -f medical-ai/pom.xml
```

Expected: BUILD SUCCESS。

- [ ] **Step 4: 启动 Docker 栈**

Run:

```bash
docker compose -f medical-ai/docker/docker-compose.yml up -d --build
```

Expected: RabbitMQ、MySQL、Nacos、gateway、appointment-service、doctor-service 均正常启动。

- [ ] **Step 5: 若使用旧 MySQL volume，手工补齐 SQL**

Run:

```bash
docker exec medical-mysql sh -lc "mysql -uroot -p${MYSQL_PASSWORD:-root123} < /docker-entrypoint-initdb.d/rabbitmq-outbox-init.sql"
```

Expected: 无报错。

- [ ] **Step 6: 如使用 Nacos 配置中心承载 Seata/Rabbit 关键键，按需补配置**

如果运行态发现某些 key 只存在本地配置、不存在 Nacos，则通过现有 Nacos OpenAPI 同步补齐。

- [ ] **Step 7: 执行 RabbitMQ 集成测试**

Run:

```bash
pytest tests/test_11_rabbitmq_side_effects.py -v
```

Expected: PASS。

---

### Task 12: 更新规划文件

**Files:**
- Modify: `task_plan.md`
- Modify: `findings.md`
- Modify: `progress.md`

- [ ] **Step 1: 在 `task_plan.md` 新增 Phase 32**

新增：

```markdown
| 32 | RabbitMQ 副作用异步化 | 预约域副作用异步化、Outbox、通知/统计/审计消费者 | complete |
```

- [ ] **Step 2: 在 `findings.md` 记录运行态发现**

必须记录：

- RabbitMQ 容器接入方式
- Outbox 表与消费幂等表设计
- 如果存在旧 volume 需要手工补 SQL 的事实
- 如果 Nacos 需要补配置的事实

- [ ] **Step 3: 在 `progress.md` 记录实现与验证结果**

记录：

- 单测结果
- Docker 联调结果
- `pytest tests/test_11_rabbitmq_side_effects.py -v` 结果
- 运行态排障过程

---

Plan complete and saved to `docs/superpowers/plans/2026-03-25-rabbitmq-async-side-effects-implementation.md`. Ready to execute?
