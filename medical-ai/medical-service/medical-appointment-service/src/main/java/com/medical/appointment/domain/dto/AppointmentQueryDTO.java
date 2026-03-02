package com.medical.appointment.domain.dto;

import java.time.LocalDate;
import lombok.Data;

@Data
public class AppointmentQueryDTO {
    private Long patientId;
    private Long doctorId;
    private LocalDate date;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer status;
}
