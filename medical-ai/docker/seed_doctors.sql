SET NAMES utf8mb4;

USE medical_user;

INSERT INTO sys_user (id, username, password, nickname, status, gender) VALUES
(6, 'doctor_zhaoliu', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '赵六', 0, 1),
(7, 'doctor_sunqi', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '孙七', 0, 1),
(8, 'doctor_zhouba', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '周八', 0, 2),
(9, 'doctor_wujiu', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '吴九', 0, 1),
(10, 'doctor_zhengshi', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '郑十', 0, 1),
(11, 'doctor_fengshiyi', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '冯十一', 0, 2),
(12, 'doctor_chenshi_er', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '陈十二', 0, 1);

INSERT INTO sys_user_role (user_id, role_id) VALUES
(6, 2),
(7, 2),
(8, 2),
(9, 2),
(10, 2),
(11, 2),
(12, 2);

USE medical_doctor;

INSERT INTO doctor_profile (id, name, title, specialties, treatment_areas, introduction, user_id, status) VALUES
(4, '赵六', '主治医师', '头疼,偏头痛,头晕,失眠,癫痫', '头痛,神经系统疾病,脑血管疾病', '擅长神经系统常见病与头痛头晕类疾病诊治。', 6, 0),
(5, '孙七', '主治医师', '发烧,咳嗽,腹泻,儿童哮喘', '儿童常见病,新生儿疾病', '长期从事儿童呼吸道与消化道常见病诊疗。', 7, 0),
(6, '周八', '副主任医师', '月经不调,孕检,妇科炎症', '妇科疾病,产前检查', '专注妇科常见病、孕期检查与女性健康管理。', 8, 0),
(7, '吴九', '主治医师', '近视,视力模糊,眼干,结膜炎', '屈光不正,眼科手术', '擅长屈光不正、眼表疾病与常见眼科门诊诊治。', 9, 0),
(8, '郑十', '主治医师', '耳鸣,鼻炎,咽喉痛,听力下降', '耳鼻咽喉疾病', '擅长耳鼻喉常见炎症及听力相关疾病诊疗。', 10, 0),
(9, '冯十一', '副主任医师', '皮疹,湿疹,过敏,痤疮,荨麻疹', '皮肤病,过敏性疾病', '擅长常见皮肤病、过敏性疾病及慢性皮肤问题管理。', 11, 0),
(10, '陈十二', '主治医师', '中医调理,失眠,体虚,腰痛', '中医内科,针灸推拿', '擅长中医体质调理、失眠腰痛等亚健康与慢病干预。', 12, 0);

INSERT INTO doctor_department (doctor_id, department_id) VALUES
(4, 3),
(5, 4),
(6, 5),
(7, 6),
(8, 7),
(9, 8),
(10, 9);

UPDATE doctor_profile
SET specialties = '高血压,心脏病,胸闷,心悸,冠心病',
    treatment_areas = '心血管疾病,高血压病,内科综合'
WHERE id = 1;

UPDATE doctor_profile
SET specialties = '糖尿病,感冒,发烧,咳嗽,肺炎',
    treatment_areas = '呼吸系统疾病,内分泌疾病'
WHERE id = 2;

UPDATE doctor_profile
SET specialties = '骨折,外伤,阑尾炎,疝气,腹痛',
    treatment_areas = '普通外科,急腹症'
WHERE id = 3;

INSERT INTO schedule_template (doctor_id, day_of_week, period, start_time, end_time, max_patients)
SELECT doctor_ids.doctor_id,
       weekdays.day_of_week,
       periods.period,
       periods.start_time,
       periods.end_time,
       20
FROM (
    SELECT 1 AS doctor_id UNION ALL
    SELECT 2 UNION ALL
    SELECT 3 UNION ALL
    SELECT 4 UNION ALL
    SELECT 5 UNION ALL
    SELECT 6 UNION ALL
    SELECT 7 UNION ALL
    SELECT 8 UNION ALL
    SELECT 9 UNION ALL
    SELECT 10
) AS doctor_ids
CROSS JOIN (
    SELECT 1 AS day_of_week UNION ALL
    SELECT 2 UNION ALL
    SELECT 3 UNION ALL
    SELECT 4 UNION ALL
    SELECT 5
) AS weekdays
CROSS JOIN (
    SELECT 'morning' AS period, '08:00:00' AS start_time, '12:00:00' AS end_time UNION ALL
    SELECT 'afternoon', '14:00:00', '18:00:00'
) AS periods
ORDER BY doctor_ids.doctor_id, weekdays.day_of_week, periods.period;

INSERT INTO schedule_slot (doctor_id, schedule_date, period, start_time, end_time, total_slots, booked_slots, status)
SELECT doctor_ids.doctor_id,
       dates.schedule_date,
       periods.period,
       periods.start_time,
       periods.end_time,
       20,
       0,
       0
FROM (
    SELECT 1 AS doctor_id UNION ALL
    SELECT 2 UNION ALL
    SELECT 3 UNION ALL
    SELECT 4 UNION ALL
    SELECT 5 UNION ALL
    SELECT 6 UNION ALL
    SELECT 7 UNION ALL
    SELECT 8 UNION ALL
    SELECT 9 UNION ALL
    SELECT 10
) AS doctor_ids
CROSS JOIN (
    SELECT DATE('2026-03-20') AS schedule_date UNION ALL
    SELECT DATE('2026-03-21') UNION ALL
    SELECT DATE('2026-03-22') UNION ALL
    SELECT DATE('2026-03-23') UNION ALL
    SELECT DATE('2026-03-24') UNION ALL
    SELECT DATE('2026-03-25')
) AS dates
CROSS JOIN (
    SELECT 'morning' AS period, '08:00:00' AS start_time, '12:00:00' AS end_time UNION ALL
    SELECT 'afternoon', '14:00:00', '18:00:00'
) AS periods
ORDER BY doctor_ids.doctor_id, dates.schedule_date, periods.period;
