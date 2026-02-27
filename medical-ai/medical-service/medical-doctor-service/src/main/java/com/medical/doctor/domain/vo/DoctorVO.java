package com.medical.doctor.domain.vo;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class DoctorVO {
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
    private List<DepartmentVO> departments;
}
