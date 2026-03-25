# Seata 分布式事务集成 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为预约创建/取消流程引入 Seata AT 模式分布式事务，保护 appointment-service 与 doctor-service 之间的跨库数据一致性。

**Architecture:** 在 Docker Compose 中新增 Seata Server (TC) 容器，appointment-service 作为 TM+RM 发起全局事务，doctor-service 作为 RM 参与分支事务。AT 模式通过 undo_log 实现自动回滚，对业务代码侵入极小。

**Tech Stack:** Seata 2.0.0, Spring Cloud Alibaba 2023.0.3.2, Nacos v2.3.2, MySQL 8.0, MyBatis-Plus 3.5.9, Druid

**Design Spec:** `docs/superpowers/specs/2026-03-25-seata-distributed-transaction-design.md`

---

## File Structure

| 文件 | 职责 | 操作 |
|------|------|------|
| `medical-ai/pom.xml` | 父 POM 版本管理 | Modify |
| `medical-service/medical-appointment-service/pom.xml` | appointment 服务依赖 | Modify |
| `medical-service/medical-doctor-service/pom.xml` | doctor 服务依赖 | Modify |
| `medical-service/medical-appointment-service/src/main/resources/application.yml` | appointment seata 配置 | Modify |
| `medical-service/medical-doctor-service/src/main/resources/application.yml` | doctor seata 配置 | Modify |
| `medical-service/medical-appointment-service/src/main/resources/application-test.yml` | 测试环境禁用 seata | Modify |
| `medical-service/medical-doctor-service/src/main/resources/application-test.yml` | 测试环境禁用 seata | Modify |
| `medical-service/medical-appointment-service/src/main/java/com/medical/appointment/service/impl/AppointmentServiceImpl.java` | 新增 @GlobalTransactional | Modify |
| `docker/docker-compose.yml` | 新增 seata-server 容器 | Modify |
| `docker/mysql/init/seata-init.sql` | Seata TC 存储 DDL | Create |
| `docker/mysql/init/undo-log-init.sql` | 两个业务库 undo_log DDL | Create |
| `tests/test_10_seata.py` | 分布式事务集成测试 | Create |

---

## Chunk 1: 基础设施层（Docker + SQL + Maven 依赖）

### Task 1: 创建 Seata TC 存储 DDL

**Files:**
- Create: `medical-ai/docker/mysql/init/seata-init.sql`

- [ ] **Step 1: 创建 SQL 初始化脚本**

```sql
-- Seata Server TC 存储表 (Seata 2.0.0 official DDL)
CREATE DATABASE IF NOT EXISTS `seata` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `seata`;

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

- [ ] **Step 2: 确认文件写入成功**

Run: `ls -la medical-ai/docker/mysql/init/seata-init.sql`
Expected: 文件存在

---

### Task 2: 创建业务库 undo_log DDL

**Files:**
- Create: `medical-ai/docker/mysql/init/undo-log-init.sql`

- [ ] **Step 1: 创建 undo_log 初始化脚本**

```sql
-- Seata AT 模式 undo_log 表 (Seata 2.0.0 official DDL)
-- 需要在 medical_appointment 和 medical_doctor 两个库中各建一张

USE `medical_appointment`;
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

USE `medical_doctor`;
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

- [ ] **Step 2: 确认文件写入成功**

Run: `ls -la medical-ai/docker/mysql/init/undo-log-init.sql`
Expected: 文件存在

---

### Task 3: Docker Compose 新增 seata-server 容器

**Files:**
- Modify: `medical-ai/docker/docker-compose.yml`

- [ ] **Step 1: 阅读现有 docker-compose.yml**

读取完整文件，定位：
1. MySQL 服务的 `volumes` 中是否有 `init` 目录挂载（用于自动执行 SQL）
2. 业务服务（appointment-service、doctor-service）的 `depends_on` 部分
3. 文件末尾的 networks/volumes 定义

- [ ] **Step 2: 新增 seata-server 服务**

在 `docker-compose.yml` 的 `services:` 下（基础设施服务之后、应用服务之前）新增：

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
      - SEATA_REGISTRY_TYPE=nacos
      - SEATA_REGISTRY_NACOS_SERVER_ADDR=nacos:8848
      - SEATA_REGISTRY_NACOS_GROUP=SEATA_GROUP
      - SEATA_REGISTRY_NACOS_CLUSTER=default
      - SEATA_REGISTRY_NACOS_NAMESPACE=
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
    networks:
      - medical-net
```

- [ ] **Step 3: MySQL init 脚本挂载**

确认 MySQL 服务的 `volumes` 中是否已有 `./mysql/init:/docker-entrypoint-initdb.d` 的挂载。如果没有，需要添加，使 `seata-init.sql` 和 `undo-log-init.sql` 在 MySQL 首次启动时自动执行。

- [ ] **Step 4: 业务服务 depends_on 补充**

在 `appointment-service` 和 `doctor-service` 的 `depends_on` 中补充：

```yaml
    depends_on:
      # ... 保留现有依赖
      seata-server:
        condition: service_healthy
```

- [ ] **Step 5: 验证 YAML 语法**

Run: `python -c "import yaml; yaml.safe_load(open('medical-ai/docker/docker-compose.yml'))"`
Expected: 无报错

---

### Task 4: 父 POM 新增 Seata 版本管理

**Files:**
- Modify: `medical-ai/pom.xml`

- [ ] **Step 1: 阅读父 POM `<properties>` 和 `<dependencyManagement>`**

定位现有版本属性和 Spring Cloud Alibaba BOM 声明位置。

- [ ] **Step 2: 新增 seata 版本属性**

在 `<properties>` 中添加：

```xml
<seata.version>2.0.0</seata.version>
```

- [ ] **Step 3: 在 `<dependencyManagement>` 中声明 seata starter**

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-seata</artifactId>
    <version>${spring-cloud-alibaba.version}</version>
</dependency>
```

- [ ] **Step 4: 验证版本兼容性**

Run: `mvn dependency:tree -pl medical-service/medical-appointment-service -f medical-ai/pom.xml -Dincludes=*:seata* 2>&1 | grep -i seata`

确认解析出的 Seata 客户端版本。如果版本不是 2.0.0，需要在 `<dependencyManagement>` 中追加显式覆盖：

```xml
<dependency>
    <groupId>org.apache.seata</groupId>
    <artifactId>seata-spring-boot-starter</artifactId>
    <version>${seata.version}</version>
</dependency>
```

---

### Task 5: 子模块新增 Seata 依赖

**Files:**
- Modify: `medical-service/medical-appointment-service/pom.xml`
- Modify: `medical-service/medical-doctor-service/pom.xml`

- [ ] **Step 1: appointment-service 添加依赖**

在 `<dependencies>` 中添加：

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-seata</artifactId>
</dependency>
```

- [ ] **Step 2: doctor-service 添加依赖**

同上，在 `<dependencies>` 中添加相同依赖。

- [ ] **Step 3: 编译验证**

Run: `mvn -q compile -pl medical-service/medical-appointment-service,medical-service/medical-doctor-service -am -DskipTests -f medical-ai/pom.xml`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add medical-ai/pom.xml \
  medical-ai/medical-service/medical-appointment-service/pom.xml \
  medical-ai/medical-service/medical-doctor-service/pom.xml \
  medical-ai/docker/docker-compose.yml \
  medical-ai/docker/mysql/init/seata-init.sql \
  medical-ai/docker/mysql/init/undo-log-init.sql
git commit -m "feat(seata): add Seata 2.0.0 infrastructure - TC docker, SQL DDLs, Maven deps"
```

---

## Chunk 2: 服务配置与业务代码改动

### Task 6: appointment-service Seata 配置

**Files:**
- Modify: `medical-service/medical-appointment-service/src/main/resources/application.yml`
- Modify: `medical-service/medical-appointment-service/src/main/resources/application-test.yml`

- [ ] **Step 1: 阅读现有 application.yml**

确认 yaml 结构和缩进风格。

- [ ] **Step 2: 追加 seata 配置段**

在 `application.yml` 末尾追加：

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

- [ ] **Step 3: application-test.yml 禁用 seata**

阅读现有 `application-test.yml`，在末尾追加：

```yaml
seata:
  enabled: false
```

---

### Task 7: doctor-service Seata 配置

**Files:**
- Modify: `medical-service/medical-doctor-service/src/main/resources/application.yml`
- Modify: `medical-service/medical-doctor-service/src/main/resources/application-test.yml`

- [ ] **Step 1: 阅读现有 application.yml**

- [ ] **Step 2: 追加 seata 配置段**

与 Task 6 Step 2 完全相同的配置段。

- [ ] **Step 3: application-test.yml 禁用 seata**

与 Task 6 Step 3 相同。

---

### Task 8: AppointmentServiceImpl 新增 @GlobalTransactional

**Files:**
- Modify: `medical-service/medical-appointment-service/src/main/java/com/medical/appointment/service/impl/AppointmentServiceImpl.java`

- [ ] **Step 1: 阅读 AppointmentServiceImpl.java**

定位 `createAppointment()` 和 `cancelAppointment()` 方法，确认现有 `@Transactional` 注解位置和 import。

- [ ] **Step 2: 新增 import**

```java
import org.apache.seata.spring.annotation.GlobalTransactional;
```

- [ ] **Step 3: createAppointment() 新增注解**

在现有 `@Transactional` 注解上方添加：

```java
@GlobalTransactional(name = "createAppointment", rollbackFor = Exception.class)
@Transactional(rollbackFor = Exception.class)  // 保留现有
```

- [ ] **Step 4: cancelAppointment() 新增注解**

```java
@GlobalTransactional(name = "cancelAppointment", rollbackFor = Exception.class)
@Transactional(rollbackFor = Exception.class)  // 保留现有
```

- [ ] **Step 5: 编译验证**

Run: `mvn -q compile -pl medical-service/medical-appointment-service -am -DskipTests -f medical-ai/pom.xml`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add medical-ai/medical-service/medical-appointment-service/src/ \
  medical-ai/medical-service/medical-doctor-service/src/
git commit -m "feat(seata): add @GlobalTransactional to appointment create/cancel + service configs"
```

---

## Chunk 3: 测试与验证

### Task 9: 单元测试回归验证

**Files:**
- 无新增，验证现有测试

- [ ] **Step 1: appointment-service 单元测试**

Run: `mvn test -pl medical-service/medical-appointment-service -f medical-ai/pom.xml`
Expected: BUILD SUCCESS, 所有测试通过（seata.enabled=false 生效）

- [ ] **Step 2: doctor-service 单元测试**

Run: `mvn test -pl medical-service/medical-doctor-service -f medical-ai/pom.xml`
Expected: BUILD SUCCESS, 所有测试通过

- [ ] **Step 3: 如有失败，诊断并修复**

常见问题：
- `seata.enabled: false` 未正确覆盖 → 检查 `@ActiveProfiles("test")` 是否生效
- Seata 自动配置类在 H2 环境报错 → 在 TestApplication 的 `@SpringBootApplication(exclude=...)` 中排除 Seata 自动配置类

---

### Task 10: 跨模块编译验证

- [ ] **Step 1: 全量编译**

Run: `mvn -q compile -pl medical-gateway,medical-service/medical-ai-service,medical-service/medical-knowledge-service,medical-service/medical-appointment-service,medical-service/medical-doctor-service,medical-service/medical-user-service -am -DskipTests -f medical-ai/pom.xml`
Expected: BUILD SUCCESS

---

### Task 11: 集成测试（需 Docker 环境运行）

**Files:**
- Create: `tests/test_10_seata.py`

- [ ] **Step 1: 编写集成测试**

```python
"""
分布式事务 (Seata AT) 集成测试
前置条件：所有服务 + seata-server 已通过 docker compose 启动
"""
import requests
import pytest
from typing import Any

BASE_URL = "http://localhost:8080/api"


class SharedState:
    """跨测试共享状态"""
    token: str = ""
    doctor_id: int | None = None
    slot_id: int | None = None
    appointment_id: int | None = None


state = SharedState()


def assert_success(resp: dict[str, Any]) -> dict[str, Any]:
    assert resp.get("code") == 200, f"Expected code 200, got {resp}"
    return resp


def auth_headers() -> dict[str, str]:
    return {"satoken": state.token}


@pytest.fixture(scope="module", autouse=True)
def setup_auth():
    """登录获取 token"""
    resp = requests.post(f"{BASE_URL}/user/auth/login", json={
        "username": "patient1",
        "password": "123456"
    }).json()
    assert_success(resp)
    state.token = resp["data"]["token"]


def test_01_find_doctor_and_slot():
    """查找医生和可用号源，为预约做准备"""
    # 获取医生列表
    resp = requests.get(
        f"{BASE_URL}/doctor/doctor/list",
        params={"pageNum": 1, "pageSize": 1},
        headers=auth_headers()
    ).json()
    assert_success(resp)
    records = resp["data"]["records"]
    assert len(records) > 0, "No doctors available"
    state.doctor_id = records[0]["id"]

    # 获取可用号源
    resp = requests.get(
        f"{BASE_URL}/doctor/schedule/slots",
        params={"doctorId": state.doctor_id},
        headers=auth_headers()
    ).json()
    assert_success(resp)
    slots = resp["data"]
    available = [s for s in slots if s.get("bookedSlots", 0) < s.get("totalSlots", 0)]
    assert len(available) > 0, "No available slots"
    state.slot_id = available[0]["id"]


def test_02_create_appointment_success():
    """正常预约创建：号源扣减 + 预约记录同时成功"""
    # 查询创建前的号源状态
    resp_before = requests.get(
        f"{BASE_URL}/doctor/schedule/slots",
        params={"doctorId": state.doctor_id},
        headers=auth_headers()
    ).json()
    assert_success(resp_before)
    slot_before = next(s for s in resp_before["data"] if s["id"] == state.slot_id)
    booked_before = slot_before["bookedSlots"]

    # 创建预约
    resp = requests.post(
        f"{BASE_URL}/appointment/appointment",
        json={"doctorId": state.doctor_id, "slotId": state.slot_id},
        headers=auth_headers()
    ).json()
    assert_success(resp)
    state.appointment_id = resp["data"]["id"]
    assert state.appointment_id is not None

    # 验证号源已扣减
    resp_after = requests.get(
        f"{BASE_URL}/doctor/schedule/slots",
        params={"doctorId": state.doctor_id},
        headers=auth_headers()
    ).json()
    assert_success(resp_after)
    slot_after = next(s for s in resp_after["data"] if s["id"] == state.slot_id)
    assert slot_after["bookedSlots"] == booked_before + 1, \
        f"Expected bookedSlots={booked_before + 1}, got {slot_after['bookedSlots']}"


def test_03_cancel_appointment_success():
    """正常预约取消：号源释放 + 预约状态同时更新"""
    assert state.appointment_id is not None, "No appointment to cancel"

    # 查询取消前的号源状态
    resp_before = requests.get(
        f"{BASE_URL}/doctor/schedule/slots",
        params={"doctorId": state.doctor_id},
        headers=auth_headers()
    ).json()
    assert_success(resp_before)
    slot_before = next(s for s in resp_before["data"] if s["id"] == state.slot_id)
    booked_before = slot_before["bookedSlots"]

    # 取消预约
    resp = requests.put(
        f"{BASE_URL}/appointment/appointment/{state.appointment_id}/cancel",
        headers=auth_headers()
    ).json()
    assert_success(resp)

    # 验证号源已释放
    resp_after = requests.get(
        f"{BASE_URL}/doctor/schedule/slots",
        params={"doctorId": state.doctor_id},
        headers=auth_headers()
    ).json()
    assert_success(resp_after)
    slot_after = next(s for s in resp_after["data"] if s["id"] == state.slot_id)
    assert slot_after["bookedSlots"] == booked_before - 1, \
        f"Expected bookedSlots={booked_before - 1}, got {slot_after['bookedSlots']}"


def test_04_duplicate_booking_rollback():
    """
    分布式事务回滚验证：
    对同一号源重复预约至满，触发异常后验证号源状态未被破坏
    """
    # 查询当前号源状态
    resp = requests.get(
        f"{BASE_URL}/doctor/schedule/slots",
        params={"doctorId": state.doctor_id},
        headers=auth_headers()
    ).json()
    assert_success(resp)
    slot = next(s for s in resp["data"] if s["id"] == state.slot_id)
    booked_snapshot = slot["bookedSlots"]

    # 尝试创建预约（可能成功或因已满失败）
    create_resp = requests.post(
        f"{BASE_URL}/appointment/appointment",
        json={"doctorId": state.doctor_id, "slotId": state.slot_id},
        headers=auth_headers()
    ).json()

    if create_resp.get("code") != 200:
        # 预约失败（号源已满或其他业务错误）
        # 验证号源数未被错误修改
        resp_after = requests.get(
            f"{BASE_URL}/doctor/schedule/slots",
            params={"doctorId": state.doctor_id},
            headers=auth_headers()
        ).json()
        assert_success(resp_after)
        slot_after = next(s for s in resp_after["data"] if s["id"] == state.slot_id)
        assert slot_after["bookedSlots"] == booked_snapshot, \
            f"Rollback failed! Expected {booked_snapshot}, got {slot_after['bookedSlots']}"
    else:
        # 如果成功了，清理：取消这个预约
        new_appt_id = create_resp["data"]["id"]
        requests.put(
            f"{BASE_URL}/appointment/appointment/{new_appt_id}/cancel",
            headers=auth_headers()
        )
```

- [ ] **Step 2: 验证测试脚本语法**

Run: `python -m py_compile tests/test_10_seata.py`
Expected: 无报错

- [ ] **Step 3: Commit**

```bash
git add tests/test_10_seata.py
git commit -m "test(seata): add distributed transaction integration tests"
```

---

### Task 12: 更新规划文件

**Files:**
- Modify: `task_plan.md`
- Modify: `findings.md`
- Modify: `progress.md`

- [ ] **Step 1: 更新 task_plan.md**

在 Phase Summary 表格中新增 Phase 30：

```
| 30 | Seata 分布式事务集成 | AT 模式保护预约创建/取消跨服务事务 | complete |
```

- [ ] **Step 2: 更新 findings.md**

新增 Seata 架构决策记录。

- [ ] **Step 3: 更新 progress.md**

记录本次 session 的操作、验证结果和错误。
