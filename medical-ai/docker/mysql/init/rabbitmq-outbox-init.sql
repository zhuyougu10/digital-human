USE `medical_appointment`;

CREATE TABLE IF NOT EXISTS `appointment_event_outbox` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `appointment_id` BIGINT NOT NULL,
  `event_type` VARCHAR(64) NOT NULL,
  `routing_key` VARCHAR(64) NOT NULL,
  `payload` JSON NOT NULL,
  `publish_status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=pending,1=published,2=publishing',
  `retry_count` INT NOT NULL DEFAULT 0,
  `last_error` VARCHAR(500) DEFAULT NULL,
  `occurred_at` DATETIME NOT NULL,
  `published_at` DATETIME DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `create_by` VARCHAR(64) DEFAULT NULL,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `update_by` VARCHAR(64) DEFAULT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_status_create_time` (`publish_status`, `create_time`)
);

CREATE TABLE IF NOT EXISTS `appointment_event_consume_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `event_id` BIGINT NOT NULL,
  `consumer_name` VARCHAR(64) NOT NULL,
  `queue_name` VARCHAR(64) DEFAULT NULL,
  `consume_status` TINYINT NOT NULL DEFAULT 1,
  `retry_count` INT NOT NULL DEFAULT 0,
  `error_message` VARCHAR(500) DEFAULT NULL,
  `consumed_at` DATETIME DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `create_by` VARCHAR(64) DEFAULT NULL,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `update_by` VARCHAR(64) DEFAULT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_consumer` (`event_id`, `consumer_name`)
);

CREATE TABLE IF NOT EXISTS `appointment_notification_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `event_id` BIGINT NOT NULL,
  `appointment_id` BIGINT NOT NULL,
  `patient_id` BIGINT NOT NULL,
  `notification_type` VARCHAR(32) NOT NULL,
  `status` TINYINT NOT NULL DEFAULT 0,
  `failure_reason` VARCHAR(500) DEFAULT NULL,
  `sent_at` DATETIME DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `create_by` VARCHAR(64) DEFAULT NULL,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `update_by` VARCHAR(64) DEFAULT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_notification` (`event_id`, `notification_type`)
);

CREATE TABLE IF NOT EXISTS `appointment_audit_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `event_id` BIGINT NOT NULL,
  `appointment_id` BIGINT NOT NULL,
  `action_type` VARCHAR(64) NOT NULL,
  `operator_id` BIGINT DEFAULT NULL,
  `detail` VARCHAR(1000) DEFAULT NULL,
  `action_time` DATETIME DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `create_by` VARCHAR(64) DEFAULT NULL,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `update_by` VARCHAR(64) DEFAULT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_audit` (`event_id`)
);
