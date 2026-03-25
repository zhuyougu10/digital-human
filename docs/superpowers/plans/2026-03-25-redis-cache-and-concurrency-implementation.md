# Redis 缓存与高并发支撑 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为系统落地一套以缓存优先、高并发保护辅助为主线的 Redis 能力，优先覆盖医生/科室/号源查询缓存与预约防重复提交保护。

**Architecture:** 基于现有 `medical-common-redis` 公共模块，在 `doctor-service`、`knowledge-service`、`appointment-service`、`ai-service` 逐步引入旁路缓存。第一阶段只实现医生/科室/号源缓存和预约防重复 key，不改变主事务语义，统一采用“读缓存命中、未命中回源回填、写成功后删缓存”的策略。

**Tech Stack:** Spring Boot 3.3.6, Spring Data Redis, RedisTemplate, MyBatis-Plus, Redis 7, Docker Compose, JUnit 5, pytest

---

## File Structure

| 文件 | 职责 | 操作 |
|------|------|------|
| `medical-ai/docker/docker-compose.yml` | 补充 Redis 运行态说明（如已有则仅校验） | Modify if needed |
| `medical-ai/medical-common/medical-common-redis/src/main/java/com/medical/common/redis/util/RedisUtil.java` | 如现有能力不足，补充 `setIfAbsent` / `delete` / `batch delete` / TTL 辅助接口 | Modify |
| `medical-ai/medical-service/medical-doctor-service/src/main/java/com/medical/doctor/service/impl/DoctorProfileServiceImpl.java` | 医生列表/详情缓存 | Modify |
| `medical-ai/medical-service/medical-doctor-service/src/main/java/com/medical/doctor/service/impl/DepartmentServiceImpl.java` | 科室列表缓存 | Modify |
| `medical-ai/medical-service/medical-doctor-service/src/main/java/com/medical/doctor/service/impl/ScheduleServiceImpl.java` | 号源短 TTL 缓存 + 写后删缓存 | Modify |
| `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/service/impl/AppointmentServiceImpl.java` | 预约防重复提交 Redis key | Modify |
| `medical-ai/medical-service/medical-knowledge-service/src/main/java/com/medical/knowledge/service/impl/KnowledgeBaseServiceImpl.java` | 第二阶段知识检索缓存（可选先预留） | Modify later |
| `medical-ai/medical-service/medical-ai-service/src/main/java/com/medical/ai/service/impl/ChatServiceImpl.java` | 第三阶段 AI 会话短态（可选先预留） | Modify later |
| `medical-ai/medical-service/medical-doctor-service/src/test/java/**` | doctor-service 缓存单测 | Modify/Create |
| `medical-ai/medical-service/medical-appointment-service/src/test/java/**` | appointment-service 防重复单测 | Modify/Create |
| `tests/test_04_doctor.py` | 运行态医生/科室/号源缓存回归验证（必要时少量补充） | Modify if needed |
| `tests/test_07_appointment.py` | 运行态预约防重复回归验证（必要时少量补充） | Modify if needed |
| `task_plan.md` | 增加 Redis 实施 Phase | Modify |
| `findings.md` | 记录缓存键、TTL、删除策略与运行态发现 | Modify |
| `progress.md` | 记录实现与验证结果 | Modify |

---

## Chunk 1: 公共 Redis 能力补齐与缓存键规范

### Task 1: 盘点并补齐 `RedisUtil` 能力

**Files:**
- Modify: `medical-ai/medical-common/medical-common-redis/src/main/java/com/medical/common/redis/util/RedisUtil.java`
- Test: `medical-ai/medical-common/medical-common-redis/src/test/java/**` (如当前模块无测试，可先不新增，改由使用方模块测试覆盖)

- [ ] **Step 1: 阅读现有 `RedisUtil` 能力**

Read:
- `medical-ai/medical-common/medical-common-redis/src/main/java/com/medical/common/redis/util/RedisUtil.java`

确认当前是否缺少以下接口：

- `setIfAbsent(key, value, timeout, unit)`
- `delete(Collection<String> keys)`
- `getExpire(key)`（可选）

- [ ] **Step 2: 若缺少 `setIfAbsent`，补充实现**

目标接口：

```java
public Boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit)
```

底层使用：

```java
redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit)
```

- [ ] **Step 3: 若缺少批量删除，补充实现**

目标接口：

```java
public Long delete(Collection<String> keys)
```

- [ ] **Step 4: 编译验证**

Run:

```bash
mvn -q compile -pl medical-common/medical-common-redis -am -DskipTests -f medical-ai/pom.xml
```

Expected: BUILD SUCCESS。

---

### Task 2: 定义缓存键与 TTL 常量

**Files:**
- Create: `medical-ai/medical-service/medical-doctor-service/src/main/java/com/medical/doctor/constant/DoctorCacheConstants.java`
- Create: `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/constant/AppointmentCacheConstants.java`

- [ ] **Step 1: 创建 doctor 缓存常量类**

至少包含：

```java
public static final String DOCTOR_LIST_KEY = "doctor:list:";
public static final String DOCTOR_DETAIL_KEY = "doctor:detail:";
public static final String DEPARTMENT_LIST_KEY = "department:list";
public static final String SCHEDULE_SLOTS_KEY = "schedule:slots:";

public static final long DOCTOR_LIST_TTL_MINUTES = 15L;
public static final long DOCTOR_DETAIL_TTL_MINUTES = 15L;
public static final long DEPARTMENT_LIST_TTL_MINUTES = 30L;
public static final long SCHEDULE_SLOTS_TTL_SECONDS = 60L;
```

- [ ] **Step 2: 创建 appointment 缓存常量类**

至少包含：

```java
public static final String APPOINTMENT_DEDUP_KEY = "appointment:dedup:";
public static final long APPOINTMENT_DEDUP_TTL_SECONDS = 30L;
```

- [ ] **Step 3: 编译验证**

Run:

```bash
mvn -q compile -pl medical-service/medical-doctor-service,medical-service/medical-appointment-service -am -DskipTests -f medical-ai/pom.xml
```

Expected: BUILD SUCCESS。

---

## Chunk 2: doctor-service 读缓存

### Task 3: 医生列表与详情缓存

**Files:**
- Modify: `medical-ai/medical-service/medical-doctor-service/src/main/java/com/medical/doctor/service/impl/DoctorProfileServiceImpl.java`
- Test: `medical-ai/medical-service/medical-doctor-service/src/test/java/**/DoctorProfileServiceImplTest.java`（若不存在则创建）

- [ ] **Step 1: 阅读 `DoctorProfileServiceImpl` 的列表/详情查询路径**

定位：

- 医生列表查询方法
- 医生详情查询方法
- 医生资料更新方法（用于删缓存）

- [ ] **Step 2: 为列表查询加入旁路缓存**

缓存 key 建议：

```java
String cacheKey = DOCTOR_LIST_KEY + pageNum + ":" + pageSize + ":" + keyword;
```

实现要求：

- 命中 Redis 直接返回
- 未命中查 DB，再写入 Redis
- TTL 使用常量并加入 `0~120s` 抖动

- [ ] **Step 3: 为详情查询加入旁路缓存**

缓存 key：

```java
String cacheKey = DOCTOR_DETAIL_KEY + doctorId;
```

- [ ] **Step 4: 在医生更新后删除详情/列表缓存**

至少删除：

- `doctor:detail:{doctorId}`
- 受影响的列表缓存（第一版可采用删除列表前缀命中的保守策略；若 RedisUtil 不支持前缀删除，则记录为后续优化并先删除 detail + 常见列表 key）

> 注意：不要为了“前缀删除”引入危险的 `KEYS` 全库扫描。若当前基础能力不支持，第一版可通过约定少量固定列表 key 或保守删缓存策略落地。

- [ ] **Step 5: 为缓存逻辑写单测（先红）**

测试点：

- 命中缓存时不再访问 mapper
- 未命中时访问 mapper 并回填缓存
- 更新后删除详情缓存

- [ ] **Step 6: 跑 doctor-service 定向测试**

Run:

```bash
mvn test -pl medical-service/medical-doctor-service -f medical-ai/pom.xml -Dtest=DoctorProfileServiceImplTest
```

Expected: PASS。

---

### Task 4: 科室列表缓存

**Files:**
- Modify: `medical-ai/medical-service/medical-doctor-service/src/main/java/com/medical/doctor/service/impl/DepartmentServiceImpl.java`
- Test: `medical-ai/medical-service/medical-doctor-service/src/test/java/**/DepartmentServiceImplTest.java`（若不存在则创建）

- [ ] **Step 1: 为科室列表加入单 key 缓存**

缓存 key：

```java
DEPARTMENT_LIST_KEY
```

- [ ] **Step 2: 科室写操作成功后删除该 key**

- [ ] **Step 3: 写单测并运行**

Run:

```bash
mvn test -pl medical-service/medical-doctor-service -f medical-ai/pom.xml -Dtest=DepartmentServiceImplTest
```

Expected: PASS。

---

### Task 5: 号源查询短 TTL 缓存

**Files:**
- Modify: `medical-ai/medical-service/medical-doctor-service/src/main/java/com/medical/doctor/service/impl/ScheduleServiceImpl.java`
- Test: `medical-ai/medical-service/medical-doctor-service/src/test/java/**/ScheduleServiceImplTest.java`

- [ ] **Step 1: 阅读 `ScheduleServiceImpl` 中号源查询与写操作路径**

重点定位：

- `getSlots(...)` 或等效查询方法
- `bookSlot(...)`
- `cancelSlot(...)`
- `generate...` 之类批量排班生成方法

- [ ] **Step 2: 为号源查询加入短 TTL 缓存**

缓存 key：

```java
SCHEDULE_SLOTS_KEY + doctorId + ":" + date;
```

TTL：`30~120s`

- [ ] **Step 3: 所有会影响号源的写路径都删缓存**

至少覆盖：

- 预约占号成功后
- 取消释放号源后
- 排班生成后

- [ ] **Step 4: 写定向单测并运行**

Run:

```bash
mvn test -pl medical-service/medical-doctor-service -f medical-ai/pom.xml -Dtest=ScheduleServiceImplTest,ScheduleControllerTest
```

Expected: PASS。

---

## Chunk 3: appointment-service 高并发辅助

### Task 6: 预约防重复提交 Redis key

**Files:**
- Modify: `medical-ai/medical-service/medical-appointment-service/src/main/java/com/medical/appointment/service/impl/AppointmentServiceImpl.java`
- Test: `medical-ai/medical-service/medical-appointment-service/src/test/java/com/medical/appointment/service/impl/AppointmentServiceImplTest.java`

- [ ] **Step 1: 在 `createAppointment()` 最前面加入 Redis 防重**

key：

```java
APPOINTMENT_DEDUP_KEY + patientId + ":" + slotId
```

逻辑：

- `setIfAbsent` 成功才继续
- 若失败，抛 `BusinessException(ErrorCode.FAIL, "请勿重复提交预约请求")`

- [ ] **Step 2: 失败/成功后的 key 清理策略**

要求：

- 主流程异常退出时删除 key，避免误锁死
- 主流程成功后允许 key 自然过期即可，不要提前删除，保留短暂防重复窗口

- [ ] **Step 3: 写单测**

覆盖：

- 首次请求拿到 key，继续主流程
- 重复请求拿不到 key，直接失败
- 主流程异常时 key 被清理

- [ ] **Step 4: 运行 appointment-service 定向测试**

Run:

```bash
mvn test -pl medical-service/medical-appointment-service -f medical-ai/pom.xml -Dtest=AppointmentServiceImplTest,AppointmentControllerTest
```

Expected: PASS。

---

## Chunk 4: 预留 knowledge / ai 扩展点

### Task 7: knowledge-service 缓存切入设计占位

**Files:**
- Modify: `findings.md`
- Modify: `progress.md`

- [ ] **Step 1: 不急着写实现，先在规划文件记录第二批切入点**

明确：

- `knowledge:search:{kbId}:{queryHash}:{topK}`
- TTL `5~15 min`
- 空值缓存短 TTL

- [ ] **Step 2: 不实现代码，仅记录实施边界**

原因：先把第一批缓存与预约防重做透，避免一次改动过大。

---

### Task 8: ai-service 短态缓存切入设计占位

**Files:**
- Modify: `findings.md`
- Modify: `progress.md`

- [ ] **Step 1: 在规划文件记录第三批切入点**

明确：

- `ai:session:{sessionId}`
- 用于短生命周期状态，不缓存完整模型回复

- [ ] **Step 2: 不实现代码，仅标注边界与理由**

---

## Chunk 5: 回归验证与规划更新

### Task 9: doctor-service 全模块测试回归

**Files:**
- 无新增，运行验证

- [ ] **Step 1: 运行 doctor-service 全测试**

Run:

```bash
mvn test -pl medical-service/medical-doctor-service -f medical-ai/pom.xml
```

Expected: BUILD SUCCESS。

---

### Task 10: appointment-service 全模块测试回归

**Files:**
- 无新增，运行验证

- [ ] **Step 1: 运行 appointment-service 全测试**

Run:

```bash
mvn test -pl medical-service/medical-appointment-service -f medical-ai/pom.xml
```

Expected: BUILD SUCCESS。

---

### Task 11: Python 集成测试最小回归

**Files:**
- Modify: `tests/test_04_doctor.py`（仅在需要补缓存相关断言时）
- Modify: `tests/test_07_appointment.py`（仅在需要补防重相关断言时）

- [ ] **Step 1: 先不增加慢测试，优先做最小回归**

若现有测试已经能覆盖主要行为，则只执行，不新增大量集成脚本。

- [ ] **Step 2: 运行最小 Python 回归**

Run:

```bash
pytest tests/test_04_doctor.py -v
pytest tests/test_07_appointment.py -v
```

Expected: PASS。

---

### Task 12: 更新规划文件

**Files:**
- Modify: `task_plan.md`
- Modify: `findings.md`
- Modify: `progress.md`

- [ ] **Step 1: 在 `task_plan.md` 新增 Redis 实施 Phase**

新增：

```markdown
| 34 | Redis 缓存与高并发支撑实现 | 医生/科室/号源缓存 + 预约防重复提交 | complete |
```

- [ ] **Step 2: 在 `findings.md` 记录运行态发现**

必须记录：

- 实际缓存键
- TTL 策略
- 删除策略
- 防重复 key 行为

- [ ] **Step 3: 在 `progress.md` 记录验证结果**

记录：

- doctor-service 测试结果
- appointment-service 测试结果
- Python 回归结果

---

Plan complete and saved to `docs/superpowers/plans/2026-03-25-redis-cache-and-concurrency-implementation.md`. Ready to execute?
