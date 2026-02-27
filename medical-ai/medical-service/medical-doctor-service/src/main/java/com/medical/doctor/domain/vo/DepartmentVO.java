package com.medical.doctor.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class DepartmentVO {
    private Long id;
    private String name;
    private String description;
    private String icon;
    private Integer sort;
    private Integer status;
    private LocalDateTime createTime;
}
