package com.medical.doctor.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.medical.common.core.domain.BaseEntity;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

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
