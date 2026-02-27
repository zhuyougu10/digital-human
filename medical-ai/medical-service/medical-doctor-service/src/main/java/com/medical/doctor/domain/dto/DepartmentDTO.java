package com.medical.doctor.domain.dto;

import lombok.Data;

@Data
public class DepartmentDTO {
    private String name;
    private String description;
    private String icon;
    private Integer sort;
}
