package com.medical.doctor.domain.dto;

import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DoctorProfileDTO {
    private Long userId;
    @NotBlank(message = "医生姓名不能为空")
    private String name;
    private String title;
    private String introduction;
    private String specialties;
    private String treatmentAreas;
    private BigDecimal consultationFee;
    private List<Long> departmentIds;
}
