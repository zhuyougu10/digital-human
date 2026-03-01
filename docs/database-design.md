# 数据库设计文档

## 1. 数据库概览
系统采用微服务独立库模式，共 5 个业务数据库：

| 数据库 | 归属服务 | 说明 |
|---|---|---|
| `medical_user` | user-service | 用户、角色、微信绑定 |
| `medical_doctor` | doctor-service | 科室、医生档案、排班与号源 |
| `medical_ai` | ai-service | 会话、消息、问诊摘要 |
| `medical_appointment` | appointment-service | 预约挂号主数据 |
| `medical_knowledge` | knowledge-service | 知识库、文档、分块元数据 |

说明：依据 `medical-ai/docker/mysql/init.sql`，除核心业务表外还包含若干关系表（如 `sys_user_role`、`doctor_department`、`wx_user_binding`）。

## 2. ER 关系描述

### 2.1 库内关系
- `medical_user`
  - `sys_user` 与 `sys_role` 为多对多，通过 `sys_user_role` 关联
  - `sys_user` 与 `wx_user_binding` 为一对一（按 `user_id` 唯一）
- `medical_doctor`
  - `doctor_profile` 与 `department` 为多对多，通过 `doctor_department` 关联
  - `doctor_profile` 与 `schedule_template` 为一对多
  - `doctor_profile` 与 `schedule_slot` 为一对多
- `medical_ai`
  - `chat_session` 与 `chat_message` 为一对多
  - `chat_session` 与 `conversation_summary` 为一对一（`session_id` 唯一）
- `medical_knowledge`
  - `knowledge_base` 与 `knowledge_document` 为一对多
  - `knowledge_document` 与 `knowledge_chunk` 为一对多
  - `knowledge_base` 与 `knowledge_chunk` 为一对多

### 2.2 跨服务引用关系（逻辑外键）
- `doctor_profile.user_id` 逻辑引用 `medical_user.sys_user.id`
- `appointment.patient_id` 逻辑引用 `medical_user.sys_user.id`
- `appointment.doctor_id` 逻辑引用 `medical_doctor.doctor_profile.id`
- `appointment.department_id` 逻辑引用 `medical_doctor.department.id`
- `appointment.slot_id` 逻辑引用 `medical_doctor.schedule_slot.id`
- `appointment.session_id` 逻辑引用 `medical_ai.chat_session.id`
- `conversation_summary.appointment_id` 逻辑引用 `medical_appointment.appointment.id`

## 3. 核心表结构（13 张）

以下为 13 张核心主业务表（字段/类型/约束）。通用审计字段采用统一风格：`create_time/create_by/update_time/update_by/deleted`（部分表不含 `deleted`）。

### 3.1 `medical_user.sys_user`
| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | 用户ID |
| `username` | VARCHAR(64) | NOT NULL, UNIQUE(`uk_username`) | 用户名 |
| `password` | VARCHAR(128) | NULL | BCrypt 密码 |
| `nickname` | VARCHAR(64) | NULL | 昵称 |
| `avatar` | VARCHAR(512) | NULL | 头像URL |
| `phone` | VARCHAR(20) | NULL | 手机号 |
| `email` | VARCHAR(128) | NULL | 邮箱 |
| `gender` | TINYINT | DEFAULT 0 | 性别 |
| `status` | TINYINT | DEFAULT 0 | 状态 |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| `create_by` | BIGINT | NULL | 创建人 |
| `update_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |
| `update_by` | BIGINT | NULL | 更新人 |
| `deleted` | TINYINT | DEFAULT 0 | 逻辑删除 |

### 3.2 `medical_user.sys_role`
| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | 角色ID |
| `role_key` | VARCHAR(32) | NOT NULL, UNIQUE(`uk_role_key`) | 角色标识 |
| `role_name` | VARCHAR(64) | NOT NULL | 角色名称 |
| `sort` | INT | DEFAULT 0 | 排序 |
| `status` | TINYINT | DEFAULT 0 | 状态 |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| `create_by` | BIGINT | NULL | 创建人 |
| `update_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |
| `update_by` | BIGINT | NULL | 更新人 |
| `deleted` | TINYINT | DEFAULT 0 | 逻辑删除 |

### 3.3 `medical_doctor.department`
| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | 科室ID |
| `name` | VARCHAR(64) | NOT NULL | 科室名称 |
| `description` | VARCHAR(500) | NULL | 科室描述 |
| `icon` | VARCHAR(256) | NULL | 图标 |
| `sort` | INT | DEFAULT 0 | 排序 |
| `status` | TINYINT | DEFAULT 0 | 状态 |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| `create_by` | BIGINT | NULL | 创建人 |
| `update_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |
| `update_by` | BIGINT | NULL | 更新人 |
| `deleted` | TINYINT | DEFAULT 0 | 逻辑删除 |

### 3.4 `medical_doctor.doctor_profile`
| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | 医生档案ID |
| `user_id` | BIGINT | NOT NULL, UNIQUE(`uk_user_id`) | 关联用户ID |
| `name` | VARCHAR(32) | NOT NULL | 医生姓名 |
| `title` | VARCHAR(32) | NULL | 职称 |
| `avatar` | VARCHAR(512) | NULL | 头像 |
| `introduction` | TEXT | NULL | 简介 |
| `specialties` | VARCHAR(500) | NULL | 擅长方向 |
| `treatment_areas` | VARCHAR(500) | NULL | 主治领域 |
| `consultation_fee` | DECIMAL(10,2) | DEFAULT 0.00 | 挂号费 |
| `status` | TINYINT | DEFAULT 0 | 状态 |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| `create_by` | BIGINT | NULL | 创建人 |
| `update_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |
| `update_by` | BIGINT | NULL | 更新人 |
| `deleted` | TINYINT | DEFAULT 0 | 逻辑删除 |

### 3.5 `medical_doctor.schedule_template`
| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | 模板ID |
| `doctor_id` | BIGINT | NOT NULL | 医生ID |
| `day_of_week` | TINYINT | NOT NULL | 星期(1-7) |
| `period` | VARCHAR(16) | NOT NULL | 时段 |
| `start_time` | TIME | NOT NULL | 开始时间 |
| `end_time` | TIME | NOT NULL | 结束时间 |
| `max_patients` | INT | DEFAULT 20 | 最大接诊量 |
| `status` | TINYINT | DEFAULT 0 | 启用状态 |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| `create_by` | BIGINT | NULL | 创建人 |
| `update_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |
| `update_by` | BIGINT | NULL | 更新人 |
| UNIQUE | - | `uk_doctor_day_period(doctor_id, day_of_week, period)` | 防重复模板 |

### 3.6 `medical_doctor.schedule_slot`
| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | 号源ID |
| `doctor_id` | BIGINT | NOT NULL | 医生ID |
| `schedule_date` | DATE | NOT NULL | 排班日期 |
| `period` | VARCHAR(16) | NOT NULL | 时段 |
| `start_time` | TIME | NOT NULL | 开始时间 |
| `end_time` | TIME | NOT NULL | 结束时间 |
| `total_slots` | INT | NOT NULL | 总号源 |
| `booked_slots` | INT | DEFAULT 0 | 已预约数 |
| `status` | TINYINT | DEFAULT 0 | 状态 |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| `create_by` | BIGINT | NULL | 创建人 |
| `update_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |
| `update_by` | BIGINT | NULL | 更新人 |
| UNIQUE | - | `uk_doctor_date_period(doctor_id, schedule_date, period)` | 防重复号源 |
| INDEX | - | `idx_date(schedule_date)` | 日期查询优化 |

### 3.7 `medical_ai.chat_session`
| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | 会话ID |
| `user_id` | BIGINT | NOT NULL | 用户ID |
| `session_type` | VARCHAR(32) | NOT NULL | TRIAGE/QA/ENCYCLOPEDIA |
| `title` | VARCHAR(128) | DEFAULT '新对话' | 会话标题 |
| `agent_type` | VARCHAR(32) | NULL | Agent 类型 |
| `status` | TINYINT | DEFAULT 0 | 0进行中/1结束/2已总结 |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| `create_by` | BIGINT | NULL | 创建人 |
| `update_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |
| `update_by` | BIGINT | NULL | 更新人 |
| `deleted` | TINYINT | DEFAULT 0 | 逻辑删除 |
| INDEX | - | `idx_user_id(user_id)` | 用户会话查询 |

### 3.8 `medical_ai.chat_message`
| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | 消息ID |
| `session_id` | BIGINT | NOT NULL | 会话ID |
| `role` | VARCHAR(16) | NOT NULL | user/assistant/system/tool |
| `content` | TEXT | NULL | 消息内容 |
| `tool_call_id` | VARCHAR(64) | NULL | 工具调用ID |
| `tool_name` | VARCHAR(64) | NULL | 工具名称 |
| `metadata` | JSON | NULL | 扩展元数据 |
| `tts_url` | VARCHAR(512) | NULL | TTS 音频地址 |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| `create_by` | BIGINT | NULL | 创建人 |
| `update_by` | BIGINT | NULL | 更新人 |
| INDEX | - | `idx_session_id(session_id)` | 会话消息查询 |

### 3.9 `medical_ai.conversation_summary`
| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | 摘要ID |
| `session_id` | BIGINT | NOT NULL, UNIQUE(`uk_session_id`) | 会话ID |
| `user_id` | BIGINT | NOT NULL | 患者ID |
| `appointment_id` | BIGINT | NULL | 关联预约ID |
| `chief_complaint` | VARCHAR(500) | NULL | 主诉 |
| `symptoms` | VARCHAR(500) | NULL | 伴随症状 |
| `duration` | VARCHAR(128) | NULL | 持续时间 |
| `severity` | VARCHAR(64) | NULL | 严重程度 |
| `medical_history` | VARCHAR(500) | NULL | 既往史 |
| `ai_assessment` | VARCHAR(500) | NULL | AI 判断 |
| `full_summary` | TEXT | NULL | 完整摘要 |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| `create_by` | BIGINT | NULL | 创建人 |
| `update_by` | BIGINT | NULL | 更新人 |
| INDEX | - | `idx_user_id(user_id)`、`idx_appointment_id(appointment_id)` | 查询优化 |

### 3.10 `medical_appointment.appointment`
| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | 预约ID |
| `patient_id` | BIGINT | NOT NULL | 患者ID |
| `doctor_id` | BIGINT | NOT NULL | 医生ID |
| `department_id` | BIGINT | NOT NULL | 科室ID |
| `slot_id` | BIGINT | NOT NULL | 号源ID |
| `session_id` | BIGINT | NULL | 会话ID |
| `appointment_date` | DATE | NOT NULL | 就诊日期 |
| `period` | VARCHAR(16) | NOT NULL | 时段 |
| `start_time` | TIME | NOT NULL | 开始时间 |
| `end_time` | TIME | NOT NULL | 结束时间 |
| `queue_number` | INT | NULL | 排队号 |
| `status` | TINYINT | DEFAULT 0 | 预约状态 |
| `cancel_reason` | VARCHAR(256) | NULL | 取消原因 |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| `create_by` | BIGINT | NULL | 创建人 |
| `update_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |
| `update_by` | BIGINT | NULL | 更新人 |
| `deleted` | TINYINT | DEFAULT 0 | 逻辑删除 |
| UNIQUE | - | `uk_patient_slot(patient_id, slot_id)` | 防重复预约 |
| INDEX | - | `idx_doctor_date(doctor_id, appointment_date)`、`idx_patient_id(patient_id)` | 查询优化 |

### 3.11 `medical_knowledge.knowledge_base`
| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | 知识库ID |
| `name` | VARCHAR(128) | NOT NULL, UNIQUE(`uk_name`) | 知识库名称 |
| `description` | VARCHAR(500) | NULL | 描述 |
| `collection_name` | VARCHAR(128) | NOT NULL | Milvus 集合名 |
| `document_count` | INT | DEFAULT 0 | 文档数 |
| `chunk_count` | INT | DEFAULT 0 | 分块数 |
| `status` | TINYINT | DEFAULT 0 | 0正常/1构建中/2异常 |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| `create_by` | BIGINT | NULL | 创建人 |
| `update_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |
| `update_by` | BIGINT | NULL | 更新人 |
| `deleted` | TINYINT | DEFAULT 0 | 逻辑删除 |

### 3.12 `medical_knowledge.knowledge_document`
| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | 文档ID |
| `kb_id` | BIGINT | NOT NULL | 知识库ID |
| `file_name` | VARCHAR(256) | NOT NULL | 原文件名 |
| `file_path` | VARCHAR(512) | NOT NULL | 存储路径 |
| `file_type` | VARCHAR(32) | NOT NULL | 文档类型 |
| `file_size` | BIGINT | DEFAULT 0 | 文件大小 |
| `chunk_count` | INT | DEFAULT 0 | 分块数 |
| `parse_status` | TINYINT | DEFAULT 0 | 0待处理/1处理中/2成功/3失败 |
| `error_msg` | VARCHAR(1000) | NULL | 错误信息 |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| `create_by` | BIGINT | NULL | 创建人 |
| `update_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |
| `update_by` | BIGINT | NULL | 更新人 |
| `deleted` | TINYINT | DEFAULT 0 | 逻辑删除 |
| INDEX | - | `idx_kb_id(kb_id)` | 按知识库分页 |

### 3.13 `medical_knowledge.knowledge_chunk`
| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | 分块ID |
| `kb_id` | BIGINT | NOT NULL | 知识库ID |
| `doc_id` | BIGINT | NOT NULL | 文档ID |
| `chunk_index` | INT | NOT NULL | 分块序号 |
| `content` | TEXT | NOT NULL | 分块文本 |
| `token_count` | INT | DEFAULT 0 | Token 数 |
| `milvus_id` | VARCHAR(64) | NULL | Milvus 向量ID |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| `create_by` | BIGINT | NULL | 创建人 |
| `update_by` | BIGINT | NULL | 更新人 |
| INDEX | - | `idx_doc_id(doc_id)`、`idx_kb_id(kb_id)` | 检索优化 |

## 4. 初始数据说明

### 4.1 用户与角色初始化
- `sys_role` 默认插入 3 个角色：`ADMIN`、`DOCTOR`、`PATIENT`
- `sys_user` 默认插入管理员账号：
  - 用户名：`admin`
  - 密码：`admin123`（BCrypt 存储）
- `sys_user_role` 默认建立 `admin -> ADMIN`

### 4.2 科室初始化
`department` 默认插入 10 个科室：
- 内科、外科、神经内科、儿科、妇产科、眼科、耳鼻喉科、皮肤科、中医科、口腔科

## 5. Milvus 向量存储结构

知识库向量数据存储在 Milvus，MySQL 保存元数据。

### 5.1 设计要点
- 向量维度：`1536`
- 距离度量：`COSINE`
- 每个知识库对应一个 `collection_name`
- `knowledge_chunk.milvus_id` 与 Milvus 向量主键映射

### 5.2 数据流
1. 上传文档进入 `knowledge_document`
2. 解析后生成分块写入 `knowledge_chunk`
3. 对分块文本做 Embedding（1536 维）
4. 向量写入 Milvus，对应 `collection_name`
5. 检索时先向量召回，再回表补充文本与文档信息

## 6. 设计约束与演进建议
- 当前跨服务引用采用“逻辑外键”，避免跨库强一致依赖
- 高并发场景建议对 `appointment` 引入更严格的乐观锁/幂等键
- `knowledge_chunk.content` 可按规模考虑冷热分层与归档
