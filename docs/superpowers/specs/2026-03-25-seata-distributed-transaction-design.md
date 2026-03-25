# Seata 分布式事务集成设计

> 日期：2026-03-25
> 状态：已确认（Review v2）
> 模式：Seata AT（自动补偿）

## 1. 背景与问题

项目采用 5 个微服务 + 各自独立数据库的架构。当前预约创建/取消流程中，`appointment-service` 通过 Feign 调用 `doctor-service` 的 `bookSlot`/`cancelSlot` 扣减/释放号源后，再操作本地 `appointment` 表。两步操作跨越两个数据库，仅有本地 `@Transactional` 保护，存在以下数据一致性风险：

| 场景 | 风险 |
|------|------|
| 预约创建 | `bookSlot` 扣了号源但 `INSERT appointment` 失败 → 号源永久泄漏 |
| 预约取消 | `cancelSlot` 释放号源但 `UPDATE appointment` 失败 → 状态不一致 |

AI 导诊的 Tool Call 间接触发上述链路，继承相同风险。

## 2. 方案选型

评估了 Seata AT / TCC / SAGA 三种模式，选择 **AT 模式**：

- 预约场景操作链短（2 个服务、2 张表），AT 模式的全局锁开销可忽略
- 对业务代码几乎无侵入，只需加注解
- 毕设 TPS 远低于 AT 全局锁的性能瓶颈
- 答辩展示友好：一个 `@GlobalTransactional` 注解体现分布式事务治理能力

## 3. 角色划分与事务边界

### 3.1 Seata 角色

| 服务 | Seata 角色 | 说明 |
|------|-----------|------|
| `appointment-service` (8084) | **TM + RM** | 事务发起方 + 管理 `medical_appointment` 库 |
| `doctor-service` (8082) | **RM** | 事务参与方，管理 `medical_doctor` 库 |
| Seata Server (Docker) | **TC** | 事务协调者 |
| `ai-service` / `user-service` / `knowledge-service` / `gateway` | 不参与 | 无跨服务写操作或不直接持有数据库事务 |

### 3.2 受保护的事务边界

```
@GlobalTransactional + @Transactional (appointment-service: createAppointment)
├── remoteScheduleService.bookSlot(slotId)    → doctor-service RM: UPDATE schedule_slot
└── appointmentMapper.insert(appointment)      → appointment-service RM: INSERT appointment

@GlobalTransactional + @Transactional (appointment-service: cancelAppointment)
├── remoteScheduleService.cancelSlot(slotId)   → doctor-service RM: UPDATE schedule_slot
└── appointmentMapper.updateById(appointment)  → appointment-service RM: UPDATE appointment
```

## 4. Seata Server (TC) 部署

### 4.1 Docker Compose 配置

| 配置项 | 值 |
|--------|-----|
| 镜像 | `seataio/seata-server:2.0.0` |
| 端口 | `8091:8091`（TC 事务通信 Netty RPC）, `7091:7091`（控制台 HTTP） |
| 注册中心 | Nacos（复用现有 `nacos:8848`，standalone） |
| 配置中心 | Nacos（同上） |
| 事务日志存储 | MySQL（复用现有 `mysql:3306`，新建 `seata` 库） |
| 启动依赖 | `depends_on: [mysql, nacos]` |
| 健康检查 | `curl -f http://localhost:7091` (控制台端口) |

### 4.2 Seata Server 环境变量

Docker Compose 中通过环境变量配置 TC：

```yaml
seata-server:
  image: seataio/seata-server:2.0.0
  container_name: seata-server
  ports:
    - "8091:8091"
    - "7091:7091"
  environment:
    - STORE_MODE=db
    - STORE_DB_DRIVER_CLASS_NAME=com.mysql.cj.jdbc.Driver
    - STORE_DB_URL=jdbc:mysql://mysql:3306/seata?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    - STORE_DB_USER=${MYSQL_USER:-root}
    - STORE_DB_PASSWORD=${MYSQL_PASSWORD:-root123}
    - SEATA_PORT=8091
    - CONSOLE_PORT=7091
    # 注册中心 - Nacos
    - SEATA_REGISTRY_TYPE=nacos
    - SEATA_REGISTRY_NACOS_SERVER_ADDR=nacos:8848
    - SEATA_REGISTRY_NACOS_GROUP=SEATA_GROUP
    - SEATA_REGISTRY_NACOS_CLUSTER=default
    - SEATA_REGISTRY_NACOS_NAMESPACE=
    # 配置中心 - Nacos
    - SEATA_CONFIG_TYPE=nacos
    - SEATA_CONFIG_NACOS_SERVER_ADDR=nacos:8848
    - SEATA_CONFIG_NACOS_GROUP=SEATA_GROUP
    - SEATA_CONFIG_NACOS_NAMESPACE=
  depends_on:
    mysql:
      condition: service_healthy
    nacos:
      condition: service_started
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:7091"]
    interval: 10s
    timeout: 5s
    retries: 10
    start_period: 30s
```

> **注意**：现有 Milvus 容器已占用 `9091` 端口，Seata 控制台使用 `7091` 不冲突。

### 4.3 版本兼容性

| 组件 | 版本 |
|------|------|
| Spring Boot | 3.3.6 |
| Spring Cloud | 2023.0.4 |
| Spring Cloud Alibaba | 2023.0.3.2 |
| Seata Server | 2.0.0 |
| Seata Client (starter) | 需验证，见下文 |

**版本校验要求**：实施前必须执行 `mvn dependency:tree -pl medical-service/medical-appointment-service | grep seata` 确认 SCA BOM 管理的 Seata 客户端版本。如果 SCA 2023.0.3.2 内置的 Seata 版本不是 2.0.0：
1. 在父 POM `<dependencyManagement>` 中显式声明 `seata-spring-boot-starter:2.0.0` 覆盖 BOM 版本
2. 确保客户端与 TC Server 主版本号一致（都是 2.x），否则 RPC 协议不兼容

### 4.4 TC 存储 DDL（新建 `seata` 数据库）

使用 Seata 2.0.0 官方 DDL（来源：`script/server/db/mysql.sql`）：

```sql
CREATE DATABASE IF NOT EXISTS `seata` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `seata`;

-- 全局事务表
CREATE TABLE IF NOT EXISTS `global_table` (
  `xid`                       VARCHAR(128)  NOT NULL,
  `transaction_id`            BIGINT,
  `status`                    TINYINT       NOT NULL,
  `application_id`            VARCHAR(32),
  `transaction_service_group` VARCHAR(32),
  `transaction_name`          VARCHAR(128),
  `timeout`                   INT,
  `begin_time`                BIGINT,
  `application_data`          VARCHAR(2000),
  `gmt_create`                DATETIME,
  `gmt_modified`              DATETIME,
  PRIMARY KEY (`xid`),
  KEY `idx_status_gmt_modified` (`status`, `gmt_modified`),
  KEY `idx_transaction_id` (`transaction_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 分支事务表
CREATE TABLE IF NOT EXISTS `branch_table` (
  `branch_id`         BIGINT        NOT NULL,
  `xid`               VARCHAR(128)  NOT NULL,
  `transaction_id`    BIGINT,
  `resource_group_id` VARCHAR(32),
  `resource_id`       VARCHAR(256),
  `branch_type`       VARCHAR(8),
  `status`            TINYINT,
  `client_id`         VARCHAR(64),
  `application_data`  VARCHAR(2000),
  `gmt_create`        DATETIME(6),
  `gmt_modified`      DATETIME(6),
  PRIMARY KEY (`branch_id`),
  KEY `idx_xid` (`xid`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 全局锁表
CREATE TABLE IF NOT EXISTS `lock_table` (
  `row_key`        VARCHAR(128) NOT NULL,
  `xid`            VARCHAR(128),
  `transaction_id` BIGINT,
  `branch_id`      BIGINT       NOT NULL,
  `resource_id`    VARCHAR(256),
  `table_name`     VARCHAR(32),
  `pk`             VARCHAR(36),
  `status`         TINYINT      NOT NULL DEFAULT 0 COMMENT '0:locked, 1:rollbacking',
  `gmt_create`     DATETIME,
  `gmt_modified`   DATETIME,
  PRIMARY KEY (`row_key`),
  KEY `idx_branch_id` (`branch_id`),
  KEY `idx_xid` (`xid`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 分布式锁表
CREATE TABLE IF NOT EXISTS `distributed_lock` (
  `lock_key`   CHAR(20)    NOT NULL,
  `lock_value` VARCHAR(20) NOT NULL,
  `expire`     BIGINT,
  PRIMARY KEY (`lock_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT INTO `distributed_lock` (lock_key, lock_value, expire) VALUES ('AsyncCommitting', ' ', 0);
INSERT INTO `distributed_lock` (lock_key, lock_value, expire) VALUES ('RetryCommitting', ' ', 0);
INSERT INTO `distributed_lock` (lock_key, lock_value, expire) VALUES ('RetryRollbacking', ' ', 0);
INSERT INTO `distributed_lock` (lock_key, lock_value, expire) VALUES ('TxTimeoutCheck', ' ', 0);
```

## 5. 客户端集成

### 5.1 Maven 依赖

| 模块 | 变更 |
|------|------|
| `medical-ai/pom.xml` (父 POM) | `<seata.version>2.0.0</seata.version>` + `dependencyManagement` 声明 `spring-cloud-starter-alibaba-seata`；若 SCA BOM 内置版本不匹配，额外声明 `seata-spring-boot-starter:2.0.0` 覆盖 |
| `medical-appointment-service/pom.xml` | 引入 `spring-cloud-starter-alibaba-seata` |
| `medical-doctor-service/pom.xml` | 引入 `spring-cloud-starter-alibaba-seata` |

### 5.2 application.yml 配置（两个服务各加一段）

```yaml
seata:
  enabled: true
  application-id: ${spring.application.name}
  tx-service-group: medical_tx_group
  service:
    vgroup-mapping:
      medical_tx_group: default
  registry:
    type: nacos
    nacos:
      server-addr: ${NACOS_HOST:localhost}:8848
      group: SEATA_GROUP
      namespace: ""
  config:
    type: nacos
    nacos:
      server-addr: ${NACOS_HOST:localhost}:8848
      group: SEATA_GROUP
      namespace: ""
  client:
    tm:
      default-global-transaction-timeout: 30000
```

### 5.3 数据源代理

Spring Cloud Alibaba Seata starter 默认 `seata.enable-auto-data-source-proxy=true`，自动对 `DataSource` Bean 做 AT 模式代理，无需手动配置 `DataSourceProxy`。

**Druid 兼容性验证**：项目使用 `druid-spring-boot-3-starter` 自动配置数据源。Seata 通过 `BeanPostProcessor` 包装 `DataSource` Bean。实施后需在启动日志中确认 `DataSource` Bean 类型为 `SeataDataSourceProxy` 包装 `DruidDataSource`。若发现代理未生效（undo_log 表无数据），可通过 Spring Boot Actuator `/beans` 端点或日志 grep `SeataDataSourceProxy` 排查。

### 5.4 XID 传播

Seata starter 自动注册 Feign 拦截器，通过 HTTP Header 传播全局事务 XID，无需手动配置。

### 5.5 undo_log 表（两个业务库各建一张）

使用 Seata 2.0.0 官方 DDL（来源：`script/client/at/db/mysql.sql`）：

```sql
-- medical_appointment 库和 medical_doctor 库各执行一次
CREATE TABLE IF NOT EXISTS `undo_log` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'increment id',
  `branch_id`     BIGINT       NOT NULL COMMENT 'branch transaction id',
  `xid`           VARCHAR(128) NOT NULL COMMENT 'global transaction id',
  `context`       VARCHAR(128) NOT NULL COMMENT 'undo_log context, such as serialization',
  `rollback_info` LONGBLOB     NOT NULL COMMENT 'rollback info',
  `log_status`    INT          NOT NULL COMMENT '0:normal status, 1:defense status',
  `log_created`   DATETIME(6)  NOT NULL COMMENT 'create datetime',
  `log_modified`  DATETIME(6)  NOT NULL COMMENT 'modify datetime',
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_undo_log` (`xid`, `branch_id`)
) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4 COMMENT = 'AT transaction mode undo table';

ALTER TABLE `undo_log` ADD INDEX `ix_log_created` (`log_created`);
```

## 6. 业务代码改动

### 6.1 AppointmentServiceImpl.java

- `createAppointment()`: **保留** `@Transactional`，**新增** `@GlobalTransactional(name = "createAppointment", rollbackFor = Exception.class)`
- `cancelAppointment()`: **保留** `@Transactional`，**新增** `@GlobalTransactional(name = "cancelAppointment", rollbackFor = Exception.class)`
- 新增 import: `org.apache.seata.spring.annotation.GlobalTransactional`（Seata 2.0.0 使用 `org.apache.seata` 包名，非旧版 `io.seata`）

> **重要**：`@GlobalTransactional` 管理全局事务协调，`@Transactional` 管理本地 Spring 事务边界。两者共存，不可替换。

### 6.2 Sentinel 交互

当前 `createAppointment()` 方法内使用了 `SphU.entry()` 做 Sentinel 热点参数限流。`@GlobalTransactional` 加在方法级别，意味着 Sentinel 限流判断在全局事务启动之后。

**处理方式**：Sentinel 的 `BlockException` 会导致方法抛异常，触发全局事务回滚。由于此时尚未执行任何 SQL，回滚代价为零（空回滚）。Seata 对空回滚有内置支持，不会引发问题。无需改变 Sentinel 位置。

### 6.3 doctor-service 侧

零改动。`ScheduleServiceImpl` 保持 `@Transactional`，数据源代理自动拦截。

### 6.4 AI Tool Call 侧

零改动。`AppointmentTool` 通过 Feign 调用 `appointment-service`，由 TM 发起全局事务。

## 7. 测试策略

### 7.1 单元测试（H2 隔离）

在 appointment-service 和 doctor-service 的 `application-test.yml` 中加 `seata.enabled: false`，`@GlobalTransactional` 退化为普通本地事务，不影响现有单元测试。

**验证**：实施后执行两个服务的完整测试套件，确认全部通过。

### 7.2 集成测试（Python pytest）

新增 `tests/test_10_seata.py`：

| 用例 | 验证点 | 故障注入方式 |
|------|--------|-------------|
| 正常预约创建 | 号源扣减 + 预约记录同时成功 | 无 |
| 正常预约取消 | 号源释放 + 预约状态同时更新 | 无 |
| 预约创建异常回滚 | bookSlot 成功但后续失败时号源自动恢复 | 通过重复预约同一号源触发唯一约束冲突 |

### 7.3 Docker Compose 启动顺序

- `seata-server` 的 `depends_on`: `mysql` + `nacos`
- `appointment-service` 和 `doctor-service` 的 `depends_on` 补充 `seata-server`（condition: service_healthy）

## 8. 回滚与应急策略

如果 Seata 引入后导致问题：

1. **快速禁用**：在 Nacos 或各服务 `application.yml` 中设置 `seata.enabled: false` 并重启服务，`@GlobalTransactional` 自动退化为本地 `@Transactional`
2. **完全回退**：移除 `@GlobalTransactional` 注解（保留 `@Transactional`），移除 Maven 依赖和配置段，停止 `seata-server` 容器
3. **TC 宕机时的行为**：Seata 客户端在 TC 不可达时会抛异常导致事务失败（不会出现部分提交），降级为"快速失败"而非"数据不一致"

## 9. 改动文件总清单

| 文件 | 改动类型 |
|------|---------|
| `medical-ai/pom.xml` | 新增 seata 版本属性 + dependencyManagement |
| `medical-appointment-service/pom.xml` | 新增 seata starter 依赖 |
| `medical-doctor-service/pom.xml` | 新增 seata starter 依赖 |
| `medical-appointment-service/application.yml` | 新增 seata 配置段 |
| `medical-doctor-service/application.yml` | 新增 seata 配置段 |
| `medical-appointment-service/application-test.yml` | 新增 `seata.enabled: false` |
| `medical-doctor-service/application-test.yml` | 新增 `seata.enabled: false` |
| `AppointmentServiceImpl.java` | 2 处新增 `@GlobalTransactional` 注解 + 1 处 import |
| `docker/docker-compose.yml` | 新增 seata-server 容器 + depends_on 调整 |
| SQL 初始化脚本 | 新建 seata 库 4 表 + 两业务库各 1 张 undo_log |
| `tests/test_10_seata.py` | 新增集成测试 |
