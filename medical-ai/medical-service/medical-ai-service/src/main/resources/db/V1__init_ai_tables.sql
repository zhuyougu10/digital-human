CREATE TABLE IF NOT EXISTS `chat_session` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `session_type` VARCHAR(32) NOT NULL COMMENT '会话类型: TRIAGE/QA/ENCYCLOPEDIA',
    `title` VARCHAR(128) DEFAULT '新对话' COMMENT '会话标题',
    `agent_type` VARCHAR(32) DEFAULT NULL COMMENT '当前Agent类型',
    `status` TINYINT DEFAULT 0 COMMENT '0进行中 1已结束 2已总结',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
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
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_id` (`session_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_appointment_id` (`appointment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话总结表';
