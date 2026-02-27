package com.medical.doctor.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("doctor_department")
public class DoctorDepartment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long doctorId;
    private Long departmentId;
}
