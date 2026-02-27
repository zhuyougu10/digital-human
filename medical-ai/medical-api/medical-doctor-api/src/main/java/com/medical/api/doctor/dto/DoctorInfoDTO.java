package com.medical.api.doctor.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class DoctorInfoDTO {
    private Long id;
    private Long userId;
    private String name;
    private String title;
    private String avatar;
    private String specialties;
    private String departmentNames;
    private BigDecimal consultationFee;
}
