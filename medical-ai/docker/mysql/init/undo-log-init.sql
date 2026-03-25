-- Seata AT mode undo_log tables (Seata 2.1.0 official DDL baseline)
-- One table is required in each business database.
-- Mounted under /docker-entrypoint-initdb.d, so Docker applies this only on first MySQL init / fresh volume.

USE `medical_appointment`;
CREATE TABLE IF NOT EXISTS `undo_log` (
  `id`            BIGINT NOT NULL AUTO_INCREMENT COMMENT 'increment id',
  `branch_id`     BIGINT NOT NULL COMMENT 'branch transaction id',
  `xid`           VARCHAR(128) NOT NULL COMMENT 'global transaction id',
  `context`       VARCHAR(128) NOT NULL COMMENT 'undo_log context, such as serialization',
  `rollback_info` LONGBLOB NOT NULL COMMENT 'rollback info',
  `log_status`    INT NOT NULL COMMENT '0:normal status, 1:defense status',
  `log_created`   DATETIME(6) NOT NULL COMMENT 'create datetime',
  `log_modified`  DATETIME(6) NOT NULL COMMENT 'modify datetime',
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_undo_log` (`xid`, `branch_id`),
  KEY `ix_log_created` (`log_created`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='AT transaction mode undo table';

USE `medical_doctor`;
CREATE TABLE IF NOT EXISTS `undo_log` (
  `id`            BIGINT NOT NULL AUTO_INCREMENT COMMENT 'increment id',
  `branch_id`     BIGINT NOT NULL COMMENT 'branch transaction id',
  `xid`           VARCHAR(128) NOT NULL COMMENT 'global transaction id',
  `context`       VARCHAR(128) NOT NULL COMMENT 'undo_log context, such as serialization',
  `rollback_info` LONGBLOB NOT NULL COMMENT 'rollback info',
  `log_status`    INT NOT NULL COMMENT '0:normal status, 1:defense status',
  `log_created`   DATETIME(6) NOT NULL COMMENT 'create datetime',
  `log_modified`  DATETIME(6) NOT NULL COMMENT 'modify datetime',
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_undo_log` (`xid`, `branch_id`),
  KEY `ix_log_created` (`log_created`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='AT transaction mode undo table';
