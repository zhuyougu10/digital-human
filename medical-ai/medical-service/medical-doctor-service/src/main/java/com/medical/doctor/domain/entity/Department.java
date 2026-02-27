package com.medical.doctor.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.medical.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("department")
public class Department extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String icon;
    private Integer sort;
    private Integer status;
}
