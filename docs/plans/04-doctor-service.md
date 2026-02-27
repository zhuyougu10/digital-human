# 04 - 医生服务 (doctor-service)

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现医生画像维护、科室管理、排班模板配置、号源生成，供预约服务和 AI 导诊使用。

**Architecture:** doctor-service 管理医生/科室/排班三大领域。排班采用"周模板 + 每日号源自动生成"模式。对外通过 Feign API 暴露医生查询和号源查询接口。

**Tech Stack:** Spring Boot 3.3.x, MyBatis-Plus, MySQL

**前置依赖:** `01-project-init.md` + `02-common-modules.md` 完成

---

## Task 1: 数据库表设计

**Files:**
- Create: `medical-ai/medical-service/medical-doctor-service/src/main/resources/db/V1__init_doctor_tables.sql`

```sql
-- 科室表
CREATE TABLE IF NOT EXISTS `department` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(64) NOT NULL COMMENT '科室名称',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '科室描述',
    `icon` VARCHAR(256) DEFAULT NULL COMMENT '科室图标URL',
    `sort` INT DEFAULT 0 COMMENT '排序',
    `status` TINYINT DEFAULT 0 COMMENT '0正常 1禁用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='科室表';

-- 医生档案表
CREATE TABLE IF NOT EXISTS `doctor_profile` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '关联 sys_user.id',
    `name` VARCHAR(32) NOT NULL COMMENT '医生姓名',
    `title` VARCHAR(32) DEFAULT NULL COMMENT '职称(主任医师/副主任医师/主治医师/住院医师)',
    `avatar` VARCHAR(512) DEFAULT NULL COMMENT '头像',
    `introduction` TEXT COMMENT '个人简介',
    `specialties` VARCHAR(500) DEFAULT NULL COMMENT '擅长方向(逗号分隔)',
    `treatment_areas` VARCHAR(500) DEFAULT NULL COMMENT '主治领域(逗号分隔)',
    `consultation_fee` DECIMAL(10,2) DEFAULT 0.00 COMMENT '挂号费',
    `status` TINYINT DEFAULT 0 COMMENT '0正常 1停诊',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `create_by` BIGINT DEFAULT NULL,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `update_by` BIGINT DEFAULT NULL,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='医生档案表';

-- 医生-科室关联表
CREATE TABLE IF NOT EXISTS `doctor_department` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `doctor_id` BIGINT NOT NULL COMMENT '医生档案ID',
    `department_id` BIGINT NOT NULL COMMENT '科室ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_doctor_dept` (`doctor_id`, `department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='医生科室关联表';

-- 排班模板表 (周模板)
CREATE TABLE IF NOT EXISTS `schedule_template` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `doctor_id` BIGINT NOT NULL COMMENT '医生档案ID',
    `day_of_week` TINYINT NOT NULL COMMENT '星期几 1-7 (1=周一)',
    `period` VARCHAR(16) NOT NULL COMMENT '时段: morning/afternoon',
    `start_time` TIME NOT NULL COMMENT '开始时间',
    `end_time` TIME NOT NULL COMMENT '结束时间',
    `max_patients` INT DEFAULT 20 COMMENT '最大接诊数',
    `status` TINYINT DEFAULT 0 COMMENT '0启用 1禁用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_doctor_day_period` (`doctor_id`, `day_of_week`, `period`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排班模板表';

-- 每日号源表 (由模板自动生成)
CREATE TABLE IF NOT EXISTS `schedule_slot` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `doctor_id` BIGINT NOT NULL COMMENT '医生档案ID',
    `schedule_date` DATE NOT NULL COMMENT '排班日期',
    `period` VARCHAR(16) NOT NULL COMMENT '时段',
    `start_time` TIME NOT NULL,
    `end_time` TIME NOT NULL,
    `total_slots` INT NOT NULL COMMENT '总号源',
    `booked_slots` INT DEFAULT 0 COMMENT '已预约数',
    `status` TINYINT DEFAULT 0 COMMENT '0可预约 1已满 2停诊',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_doctor_date_period` (`doctor_id`, `schedule_date`, `period`),
    KEY `idx_date` (`schedule_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日号源表';

-- 初始化科室数据
INSERT INTO `department` (`name`, `description`, `sort`) VALUES
('内科', '心血管、呼吸、消化等内科疾病', 1),
('外科', '普外、骨科、泌尿等外科疾病', 2),
('神经内科', '头痛、眩晕、脑血管疾病', 3),
('儿科', '儿童常见病、发育异常', 4),
('妇产科', '妇科疾病、产前检查', 5),
('眼科', '近视、白内障、青光眼', 6),
('耳鼻喉科', '听力下降、鼻炎、咽喉炎', 7),
('皮肤科', '皮疹、湿疹、过敏', 8),
('中医科', '中医调理、针灸推拿', 9),
('口腔科', '牙齿疾病、口腔修复', 10);
```

---

## Task 2: Entity 实体类

**Files:**
- Create: `medical-ai/medical-service/medical-doctor-service/src/main/java/com/medical/doctor/domain/entity/Department.java`
- Create: `medical-ai/medical-service/medical-doctor-service/src/main/java/com/medical/doctor/domain/entity/DoctorProfile.java`
- Create: `medical-ai/medical-service/medical-doctor-service/src/main/java/com/medical/doctor/domain/entity/DoctorDepartment.java`
- Create: `medical-ai/medical-service/medical-doctor-service/src/main/java/com/medical/doctor/domain/entity/ScheduleTemplate.java`
- Create: `medical-ai/medical-service/medical-doctor-service/src/main/java/com/medical/doctor/domain/entity/ScheduleSlot.java`

**Step 1: Department**

```java
package com.medical.doctor.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.medical.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("department")
public class Department extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String icon;
    private Integer sort;
    private Integer status;
}
```

**Step 2: DoctorProfile**

```java
package com.medical.doctor.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.medical.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("doctor_profile")
public class DoctorProfile extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String title;
    private String avatar;
    private String introduction;
    private String specialties;
    private String treatmentAreas;
    private BigDecimal consultationFee;
    private Integer status;
}
```

**Step 3: DoctorDepartment**

```java
package com.medical.doctor.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("doctor_department")
public class DoctorDepartment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long doctorId;
    private Long departmentId;
}
```

**Step 4: ScheduleTemplate**

```java
package com.medical.doctor.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("schedule_template")
public class ScheduleTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long doctorId;
    private Integer dayOfWeek;
    private String period;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer maxPatients;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

**Step 5: ScheduleSlot**

```java
package com.medical.doctor.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("schedule_slot")
public class ScheduleSlot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long doctorId;
    private LocalDate scheduleDate;
    private String period;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer totalSlots;
    private Integer bookedSlots;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 剩余号源 */
    @TableField(exist = false)
    public int getAvailableSlots() {
        return totalSlots - bookedSlots;
    }
}
```

---

## Task 3: Mapper 层

**Files:**
- Create 5 个 Mapper 接口（同 user-service 模式，继承 BaseMapper）

```java
// DepartmentMapper, DoctorProfileMapper, DoctorDepartmentMapper, 
// ScheduleTemplateMapper, ScheduleSlotMapper
// 均在 com.medical.doctor.mapper 包下，继承 BaseMapper<Entity>
```

---

## Task 4: DTO/VO 定义

**Files:**
- Create: `com/medical/doctor/domain/dto/DepartmentDTO.java` - name, description, icon, sort
- Create: `com/medical/doctor/domain/dto/DoctorProfileDTO.java` - name, title, introduction, specialties, treatmentAreas, consultationFee, departmentIds
- Create: `com/medical/doctor/domain/dto/ScheduleTemplateDTO.java` - dayOfWeek, period, startTime, endTime, maxPatients
- Create: `com/medical/doctor/domain/vo/DepartmentVO.java`
- Create: `com/medical/doctor/domain/vo/DoctorVO.java` - 含科室列表、擅长方向
- Create: `com/medical/doctor/domain/vo/ScheduleSlotVO.java` - 含医生姓名、可用号源

---

## Task 5: Service 层 - 科室管理

**Files:**
- Create: `com/medical/doctor/service/DepartmentService.java`
- Create: `com/medical/doctor/service/impl/DepartmentServiceImpl.java`

功能清单：
- `list()` - 查询全部科室（按 sort 排序）
- `getById(id)` - 查询科室详情
- `create(dto)` - 创建科室
- `update(id, dto)` - 更新科室
- `delete(id)` - 删除科室
- `toggleStatus(id)` - 启用/禁用

---

## Task 6: Service 层 - 医生档案管理

**Files:**
- Create: `com/medical/doctor/service/DoctorProfileService.java`
- Create: `com/medical/doctor/service/impl/DoctorProfileServiceImpl.java`

功能清单：
- `listByDepartment(departmentId, pageQuery)` - 按科室分页查询
- `searchBySymptom(symptomKeywords)` - 根据症状关键词匹配医生（供 AI 导诊调用）
- `getById(id)` / `getByUserId(userId)` - 查询详情
- `create(dto)` / `update(id, dto)` - 创建/更新画像
- `updateMyProfile(userId, dto)` - 医生自己维护画像
- `delete(id)` - 删除

关键实现 - `searchBySymptom`：
```java
public List<DoctorVO> searchBySymptom(String symptomKeywords) {
    // 将症状关键词用逗号拆分
    // 在 doctor_profile.specialties 和 treatment_areas 中 LIKE 模糊匹配
    // 同时关联科室信息返回
    // 按匹配度排序（匹配字段越多越靠前）
}
```

---

## Task 7: Service 层 - 排班管理

**Files:**
- Create: `com/medical/doctor/service/ScheduleService.java`
- Create: `com/medical/doctor/service/impl/ScheduleServiceImpl.java`

功能清单：
- `getTemplatesByDoctor(doctorId)` - 查询医生排班模板
- `saveTemplate(doctorId, dto)` - 保存/更新排班模板
- `deleteTemplate(templateId)` - 删除排班模板
- `generateSlots(startDate, endDate)` - 按模板批量生成每日号源（定时任务/手动触发）
- `getAvailableSlots(doctorId, date)` - 查询某医生某日可用号源
- `getAvailableSlotsByDepartment(deptId, date)` - 查询某科室某日所有医生可用号源
- `bookSlot(slotId)` - 预约号源（booked_slots + 1, 乐观锁）
- `cancelSlot(slotId)` - 取消号源（booked_slots - 1）

关键实现 - 号源生成定时任务：
```java
@Scheduled(cron = "0 0 0 * * ?") // 每天凌晨执行
public void autoGenerateSlots() {
    // 生成未来7天的号源
    LocalDate start = LocalDate.now().plusDays(1);
    LocalDate end = start.plusDays(6);
    generateSlots(start, end);
}
```

关键实现 - 乐观锁预约：
```java
public boolean bookSlot(Long slotId) {
    // UPDATE schedule_slot 
    // SET booked_slots = booked_slots + 1, status = IF(booked_slots + 1 >= total_slots, 1, 0)
    // WHERE id = #{slotId} AND booked_slots < total_slots AND status = 0
    // 返回影响行数 > 0 表示成功
}
```

---

## Task 8: Controller 层

**Files:**
- Create: `com/medical/doctor/controller/DepartmentController.java`
  - `GET /department/list` - 科室列表
  - `POST /department` - 创建（ADMIN）
  - `PUT /department/{id}` - 更新（ADMIN）
  - `DELETE /department/{id}` - 删除（ADMIN）

- Create: `com/medical/doctor/controller/DoctorController.java`
  - `GET /doctor/list` - 医生列表（分页，可按科室筛选）
  - `GET /doctor/{id}` - 医生详情
  - `GET /doctor/search` - 症状搜索医生（供 AI Agent 调用）
  - `GET /doctor/my-profile` - 当前医生画像（DOCTOR）
  - `PUT /doctor/my-profile` - 更新自己画像（DOCTOR）
  - `POST /doctor` - 创建医生（ADMIN）
  - `PUT /doctor/{id}` - 更新医生（ADMIN）

- Create: `com/medical/doctor/controller/ScheduleController.java`
  - `GET /schedule/template/{doctorId}` - 排班模板
  - `POST /schedule/template/{doctorId}` - 保存排班模板（ADMIN/DOCTOR）
  - `GET /schedule/slots` - 查询号源（doctorId + date）
  - `POST /schedule/generate` - 手动生成号源（ADMIN）

---

## Task 9: Feign API 定义

**Files:**
- Create: `medical-ai/medical-api/medical-doctor-api/src/main/java/com/medical/api/doctor/RemoteDoctorService.java`
- Create: `medical-ai/medical-api/medical-doctor-api/src/main/java/com/medical/api/doctor/dto/DoctorInfoDTO.java`
- Create: `medical-ai/medical-api/medical-doctor-api/src/main/java/com/medical/api/doctor/dto/SlotInfoDTO.java`

```java
@FeignClient(name = "medical-doctor-service", path = "/doctor")
public interface RemoteDoctorService {
    @GetMapping("/inner/{doctorId}")
    R<DoctorInfoDTO> getDoctorById(@PathVariable("doctorId") Long doctorId);

    @GetMapping("/inner/search")
    R<List<DoctorInfoDTO>> searchBySymptom(@RequestParam("keywords") String keywords);

    @GetMapping("/inner/slots")
    R<List<SlotInfoDTO>> getAvailableSlots(@RequestParam("doctorId") Long doctorId,
                                            @RequestParam("date") String date);

    @PostMapping("/inner/slots/{slotId}/book")
    R<Boolean> bookSlot(@PathVariable("slotId") Long slotId);

    @PostMapping("/inner/slots/{slotId}/cancel")
    R<Boolean> cancelSlot(@PathVariable("slotId") Long slotId);
}
```

---

## Task 10: 编译验证 + Commit

Run: `mvn clean compile -f medical-ai/pom.xml`
Expected: BUILD SUCCESS

```bash
git add .
git commit -m "feat(doctor-service): implement department CRUD, doctor profile, schedule management"
```

---

## 检查清单

- [ ] DDL: department, doctor_profile, doctor_department, schedule_template, schedule_slot
- [ ] 初始化 10 个科室
- [ ] 科室 CRUD
- [ ] 医生画像 CRUD + 医生自维护
- [ ] 症状关键词搜索医生（供 AI 导诊）
- [ ] 排班模板 CRUD
- [ ] 每日号源自动生成（定时任务）
- [ ] 号源查询 + 乐观锁预约/取消
- [ ] Feign API 暴露医生查询 / 号源查询 / 预约号源接口
- [ ] 编译通过
