package com.medical.appointment.domain.vo;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Data;

@Data
public class AppointmentListVO {
    private Long id;
    private Long patientId;
    private String patientName;
    private String patientPhone;
    private Long doctorId;
    private LocalDate appointmentDate;
    private String period;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer status;
    private String doctorName;
    private String departmentName;
}
