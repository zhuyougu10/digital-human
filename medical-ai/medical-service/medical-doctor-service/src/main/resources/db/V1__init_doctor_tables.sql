-- 科室表
CREATE TABLE IF NOT EXISTS department (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL COMMENT '科室名称',
    description VARCHAR(500) DEFAULT NULL COMMENT '科室描述',
    icon VARCHAR(256) DEFAULT NULL COMMENT '科室图标URL',
    sort INT DEFAULT 0 COMMENT '排序',
    status TINYINT DEFAULT 0 COMMENT '0正常 1禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
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
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
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
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
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
