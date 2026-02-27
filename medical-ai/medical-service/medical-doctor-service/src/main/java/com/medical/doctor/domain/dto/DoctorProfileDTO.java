package com.medical.doctor.domain.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class DoctorProfileDTO {
    private String name;
    private String title;
    private String introduction;
    private String specialties;
    private String treatmentAreas;
    private BigDecimal consultationFee;
    private List<Long> departmentIds;
}
