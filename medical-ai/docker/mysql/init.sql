-- 创建各服务数据库
CREATE DATABASE IF NOT EXISTS medical_user DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE medical_user;
-- 用户表
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(64) NOT NULL COMMENT '用户名',
    `password` VARCHAR(128) DEFAULT NULL COMMENT '密码(BCrypt)',
    `nickname` VARCHAR(64) DEFAULT NULL COMMENT '昵称',
    `avatar` VARCHAR(512) DEFAULT NULL COMMENT '头像URL',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `email` VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `gender` TINYINT DEFAULT 0 COMMENT '性别 0未知 1男 2女',
    `status` TINYINT DEFAULT 0 COMMENT '状态 0正常 1禁用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `create_by` BIGINT DEFAULT NULL,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `update_by` BIGINT DEFAULT NULL,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 角色表
CREATE TABLE IF NOT EXISTS `sys_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    `role_key` VARCHAR(32) NOT NULL COMMENT '角色标识(PATIENT/DOCTOR/ADMIN)',
    `role_name` VARCHAR(64) NOT NULL COMMENT '角色名称',
    `sort` INT DEFAULT 0 COMMENT '排序',
    `status` TINYINT DEFAULT 0 COMMENT '状态 0正常 1禁用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `create_by` BIGINT DEFAULT NULL,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `update_by` BIGINT DEFAULT NULL,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_key` (`role_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS `sys_user_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `create_by` BIGINT DEFAULT NULL,
    `update_by` BIGINT DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 微信用户绑定表
CREATE TABLE IF NOT EXISTS `wx_user_binding` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '系统用户ID',
    `openid` VARCHAR(64) NOT NULL COMMENT '微信OpenID',
    `unionid` VARCHAR(64) DEFAULT NULL COMMENT '微信UnionID',
    `session_key` VARCHAR(128) DEFAULT NULL COMMENT '会话密钥',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `create_by` BIGINT DEFAULT NULL,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `update_by` BIGINT DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_openid` (`openid`),
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信用户绑定表';

-- 初始化角色数据
INSERT INTO `sys_role` (`role_key`, `role_name`, `sort`) VALUES
('ADMIN', '管理员', 1),
('DOCTOR', '医生', 2),
('PATIENT', '患者', 3);

-- 初始化管理员账号 (密码: admin123)
INSERT INTO `sys_user` (`username`, `password`, `nickname`, `status`) VALUES
('admin', '$2b$10$.Lzfrzpy7U.7xK6GyYkZqOGqyubd/oBF/70BGQsE7ndEL4VMaqVWy', '系统管理员', 0);

INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES (1, 1);

CREATE DATABASE IF NOT EXISTS medical_doctor DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE medical_doctor;
-- 科室表
CREATE TABLE IF NOT EXISTS department (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL COMMENT '科室名称',
    description VARCHAR(500) DEFAULT NULL COMMENT '科室描述',
    icon VARCHAR(256) DEFAULT NULL COMMENT '科室图标URL',
    sort INT DEFAULT 0 COMMENT '排序',
    status TINYINT DEFAULT 0 COMMENT '0正常 1禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    create_by BIGINT DEFAULT NULL,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    update_by BIGINT DEFAULT NULL,
    deleted TINYINT DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='科室表';

-- 医生档案表
CREATE TABLE IF NOT EXISTS doctor_profile (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '关联 sys_user.id',
    name VARCHAR(32) NOT NULL COMMENT '医生姓名',
    title VARCHAR(32) DEFAULT NULL COMMENT '职称(主任医师/副主任医师/主治医师/住院医师)',
    avatar VARCHAR(512) DEFAULT NULL COMMENT '头像',
    introduction TEXT COMMENT '个人简介',
    specialties VARCHAR(500) DEFAULT NULL COMMENT '擅长方向(逗号分隔)',
    treatment_areas VARCHAR(500) DEFAULT NULL COMMENT '主治领域(逗号分隔)',
    consultation_fee DECIMAL(10,2) DEFAULT 0.00 COMMENT '挂号费',
    status TINYINT DEFAULT 0 COMMENT '0正常 1停诊',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    create_by BIGINT DEFAULT NULL,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    update_by BIGINT DEFAULT NULL,
    deleted TINYINT DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='医生档案表';

-- 医生-科室关联表
CREATE TABLE IF NOT EXISTS doctor_department (
    id BIGINT NOT NULL AUTO_INCREMENT,
    doctor_id BIGINT NOT NULL COMMENT '医生档案ID',
    department_id BIGINT NOT NULL COMMENT '科室ID',
    create_by BIGINT DEFAULT NULL,
    update_by BIGINT DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_doctor_dept (doctor_id, department_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='医生科室关联表';

-- 排班模板表 (周模板)
CREATE TABLE IF NOT EXISTS schedule_template (
    id BIGINT NOT NULL AUTO_INCREMENT,
    doctor_id BIGINT NOT NULL COMMENT '医生档案ID',
    day_of_week TINYINT NOT NULL COMMENT '星期几 1-7 (1=周一)',
    period VARCHAR(16) NOT NULL COMMENT '时段: morning/afternoon',
    start_time TIME NOT NULL COMMENT '开始时间',
    end_time TIME NOT NULL COMMENT '结束时间',
    max_patients INT DEFAULT 20 COMMENT '最大接诊数',
    status TINYINT DEFAULT 0 COMMENT '0启用 1禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    create_by BIGINT DEFAULT NULL,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    update_by BIGINT DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_doctor_day_period (doctor_id, day_of_week, period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排班模板表';

-- 每日号源表 (由模板自动生成)
CREATE TABLE IF NOT EXISTS schedule_slot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    doctor_id BIGINT NOT NULL COMMENT '医生档案ID',
    schedule_date DATE NOT NULL COMMENT '排班日期',
    period VARCHAR(16) NOT NULL COMMENT '时段',
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    total_slots INT NOT NULL COMMENT '总号源',
    booked_slots INT DEFAULT 0 COMMENT '已预约数',
    status TINYINT DEFAULT 0 COMMENT '0可预约 1已满 2停诊',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    create_by BIGINT DEFAULT NULL,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    update_by BIGINT DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_doctor_date_period (doctor_id, schedule_date, period),
    KEY idx_date (schedule_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日号源表';

-- 初始化科室数据
INSERT INTO department (name, description, sort) VALUES
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

CREATE DATABASE IF NOT EXISTS medical_ai DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE medical_ai;
CREATE TABLE IF NOT EXISTS `chat_session` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `session_type` VARCHAR(32) NOT NULL COMMENT '会话类型: TRIAGE/QA/ENCYCLOPEDIA',
    `title` VARCHAR(128) DEFAULT '新对话' COMMENT '会话标题',
    `agent_type` VARCHAR(32) DEFAULT NULL COMMENT '当前Agent类型',
    `status` TINYINT DEFAULT 0 COMMENT '0进行中 1已结束 2已总结',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `create_by` BIGINT DEFAULT NULL,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `update_by` BIGINT DEFAULT NULL,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话会话表';

CREATE TABLE IF NOT EXISTS `chat_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `session_id` BIGINT NOT NULL COMMENT '会话ID',
    `role` VARCHAR(16) NOT NULL COMMENT 'user/assistant/system/tool',
    `content` TEXT COMMENT '消息内容',
    `tool_call_id` VARCHAR(64) DEFAULT NULL COMMENT 'tool call ID',
    `tool_name` VARCHAR(64) DEFAULT NULL COMMENT '工具名称',
    `metadata` JSON DEFAULT NULL COMMENT '元数据',
    `tts_url` VARCHAR(512) DEFAULT NULL COMMENT 'TTS音频URL',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `create_by` BIGINT DEFAULT NULL,
    `update_by` BIGINT DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话消息表';

CREATE TABLE IF NOT EXISTS `conversation_summary` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `session_id` BIGINT NOT NULL COMMENT '会话ID',
    `user_id` BIGINT NOT NULL COMMENT '患者ID',
    `appointment_id` BIGINT DEFAULT NULL COMMENT '关联预约ID',
    `chief_complaint` VARCHAR(500) DEFAULT NULL COMMENT '主诉',
    `symptoms` VARCHAR(500) DEFAULT NULL COMMENT '伴随症状',
    `duration` VARCHAR(128) DEFAULT NULL COMMENT '持续时间',
    `severity` VARCHAR(64) DEFAULT NULL COMMENT '严重程度',
    `medical_history` VARCHAR(500) DEFAULT NULL COMMENT '既往史',
    `ai_assessment` VARCHAR(500) DEFAULT NULL COMMENT 'AI初步判断',
    `full_summary` TEXT COMMENT '完整摘要文本',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `create_by` BIGINT DEFAULT NULL,
    `update_by` BIGINT DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_id` (`session_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_appointment_id` (`appointment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话总结表';

CREATE DATABASE IF NOT EXISTS medical_appointment DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE medical_appointment;
CREATE TABLE IF NOT EXISTS `appointment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `patient_id` BIGINT NOT NULL COMMENT 'patient user id',
    `doctor_id` BIGINT NOT NULL COMMENT 'doctor profile id',
    `department_id` BIGINT NOT NULL COMMENT 'department id',
    `slot_id` BIGINT NOT NULL COMMENT 'slot id',
    `session_id` BIGINT DEFAULT NULL COMMENT 'chat session id',
    `appointment_date` DATE NOT NULL COMMENT 'appointment date',
    `period` VARCHAR(16) NOT NULL COMMENT 'morning/afternoon',
    `start_time` TIME NOT NULL,
    `end_time` TIME NOT NULL,
    `queue_number` INT DEFAULT NULL COMMENT 'queue number',
    `status` TINYINT DEFAULT 0 COMMENT '0 pending,1 completed,2 cancelled,3 no-show',
    `cancel_reason` VARCHAR(256) DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `create_by` BIGINT DEFAULT NULL,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `update_by` BIGINT DEFAULT NULL,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_patient_slot` (`patient_id`, `slot_id`),
    KEY `idx_doctor_date` (`doctor_id`, `appointment_date`),
    KEY `idx_patient_id` (`patient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='appointment table';

CREATE DATABASE IF NOT EXISTS medical_knowledge DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE medical_knowledge;
-- knowledge base table
CREATE TABLE IF NOT EXISTS `knowledge_base` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(128) NOT NULL COMMENT 'Knowledge base name',
    `description` VARCHAR(500) DEFAULT NULL COMMENT 'Description',
    `collection_name` VARCHAR(128) NOT NULL COMMENT 'Milvus collection name',
    `document_count` INT DEFAULT 0 COMMENT 'Document count',
    `chunk_count` INT DEFAULT 0 COMMENT 'Chunk count',
    `status` TINYINT DEFAULT 0 COMMENT '0-normal 1-building 2-error',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `create_by` BIGINT DEFAULT NULL,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `update_by` BIGINT DEFAULT NULL,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Knowledge base';

-- knowledge document table
CREATE TABLE IF NOT EXISTS `knowledge_document` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `kb_id` BIGINT NOT NULL COMMENT 'Knowledge base ID',
    `file_name` VARCHAR(256) NOT NULL COMMENT 'Original file name',
    `file_path` VARCHAR(512) NOT NULL COMMENT 'Storage path',
    `file_type` VARCHAR(32) NOT NULL COMMENT 'File type(pdf/docx/txt)',
    `file_size` BIGINT DEFAULT 0 COMMENT 'File size(bytes)',
    `chunk_count` INT DEFAULT 0 COMMENT 'Chunk count',
    `parse_status` TINYINT DEFAULT 0 COMMENT '0-pending 1-processing 2-success 3-failed',
    `error_msg` VARCHAR(1000) DEFAULT NULL COMMENT 'Error message',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `create_by` BIGINT DEFAULT NULL,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `update_by` BIGINT DEFAULT NULL,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_kb_id` (`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Knowledge document';

-- knowledge chunk table (metadata in MySQL, vector in Milvus)
CREATE TABLE IF NOT EXISTS `knowledge_chunk` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `kb_id` BIGINT NOT NULL COMMENT 'Knowledge base ID',
    `doc_id` BIGINT NOT NULL COMMENT 'Document ID',
    `chunk_index` INT NOT NULL COMMENT 'Chunk sequence index',
    `content` TEXT NOT NULL COMMENT 'Chunk text content',
    `token_count` INT DEFAULT 0 COMMENT 'Token count',
    `milvus_id` VARCHAR(64) DEFAULT NULL COMMENT 'Vector ID in Milvus',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `create_by` BIGINT DEFAULT NULL,
    `update_by` BIGINT DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_doc_id` (`doc_id`),
    KEY `idx_kb_id` (`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Knowledge chunk';
