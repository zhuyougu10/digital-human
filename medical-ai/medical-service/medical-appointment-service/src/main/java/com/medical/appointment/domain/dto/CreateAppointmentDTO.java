package com.medical.appointment.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateAppointmentDTO {
    private Long patientId;
    @NotNull(message = "医生ID不能为空")
    private Long doctorId;
    private Long departmentId;
    @NotNull(message = "号源ID不能为空")
    private Long slotId;
    private Long sessionId;
}
