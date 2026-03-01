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
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_key` (`role_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS `sys_user_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
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
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
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
