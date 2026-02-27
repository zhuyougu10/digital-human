# 07 - 预约服务 (appointment-service)

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现完整预约挂号流程：创建预约、查看预约、取消预约、医生查看预约列表（含对话摘要）。

**Architecture:** 预约服务通过 Feign 调用 doctor-service 的号源锁定接口完成预约。预约成功后异步通知 ai-service 生成对话摘要。

**Tech Stack:** Spring Boot 3.3.x, MyBatis-Plus, MySQL, Redis（号源防重复）

**前置依赖:** `03-user-service` + `04-doctor-service` 完成

---

## Task 1: 数据库表设计

```sql
-- 预约表
CREATE TABLE IF NOT EXISTS `appointment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `patient_id` BIGINT NOT NULL COMMENT '患者用户ID',
    `doctor_id` BIGINT NOT NULL COMMENT '医生档案ID',
    `department_id` BIGINT NOT NULL COMMENT '科室ID',
    `slot_id` BIGINT NOT NULL COMMENT '号源ID',
    `session_id` BIGINT DEFAULT NULL COMMENT '关联对话会话ID',
    `appointment_date` DATE NOT NULL COMMENT '预约日期',
    `period` VARCHAR(16) NOT NULL COMMENT '时段 morning/afternoon',
    `start_time` TIME NOT NULL,
    `end_time` TIME NOT NULL,
    `queue_number` INT DEFAULT NULL COMMENT '排队号',
    `status` TINYINT DEFAULT 0 COMMENT '0待就诊 1已就诊 2已取消 3爽约',
    `cancel_reason` VARCHAR(256) DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_patient_slot` (`patient_id`, `slot_id`),
    KEY `idx_doctor_date` (`doctor_id`, `appointment_date`),
    KEY `idx_patient_id` (`patient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约表';
```

---

## Task 2: Entity + Mapper + DTO/VO

Entity: `Appointment`
DTO:
- `CreateAppointmentDTO(patientId, doctorId, departmentId, slotId, sessionId)`
- `AppointmentQueryDTO(patientId, doctorId, date, status)` - 查询条件
VO:
- `AppointmentVO` - 含医生姓名、科室名称、预约时间、对话摘要（可选加载）
- `AppointmentListVO` - 列表简略版

---

## Task 3: Service 层

**Files:**
- Create: `com/medical/appointment/service/AppointmentService.java`
- Create: `com/medical/appointment/service/impl/AppointmentServiceImpl.java`

功能清单：
- `createAppointment(dto)` - 创建预约
  1. 检查是否重复预约（同患者+同号源）
  2. 调用 doctor-service bookSlot 锁定号源
  3. 计算排队号（当前该号源已预约数 +1）
  4. 保存预约记录
  5. 异步触发对话摘要生成（如果有 sessionId）
- `cancelAppointment(appointmentId, userId)` - 取消预约
  1. 验证归属权
  2. 调用 doctor-service cancelSlot 释放号源
  3. 更新状态
- `getMyAppointments(userId, pageQuery)` - 患者查看自己的预约列表
- `getDoctorAppointments(doctorId, date)` - 医生查看某日预约列表
- `getAppointmentDetail(appointmentId)` - 预约详情（含对话摘要）
- `listAll(queryDTO, pageQuery)` - 管理端全量查询
- `getStatistics(dateRange)` - 预约统计（今日预约数、本周趋势）

---

## Task 4: Controller 层

```
POST   /appointment              - 创建预约
GET    /appointment/my            - 我的预约列表（患者）
GET    /appointment/doctor        - 今日预约列表（医生） ?date=2026-02-28
GET    /appointment/{id}          - 预约详情
PUT    /appointment/{id}/cancel   - 取消预约
GET    /appointment/list          - 全量列表（ADMIN）
GET    /appointment/statistics    - 统计数据（ADMIN）
```

---

## Task 5: Feign API 定义

```java
@FeignClient(name = "medical-appointment-service", path = "/appointment")
public interface RemoteAppointmentService {
    @PostMapping("/inner/create")
    R<Long> createAppointment(@RequestBody CreateAppointmentDTO dto);
}
```

---

## Task 6: 编译验证 + Commit

```bash
git add .
git commit -m "feat(appointment-service): implement full appointment flow with slot booking"
```

---

## 检查清单

- [ ] DDL: appointment 表
- [ ] 创建预约 + 号源锁定（防重复）
- [ ] 取消预约 + 号源释放
- [ ] 患者/医生/管理端三种查询视角
- [ ] 预约详情含对话摘要
- [ ] 预约统计接口
- [ ] Feign API
- [ ] 编译通过
