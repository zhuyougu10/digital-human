package com.medical.appointment.domain.dto;

import lombok.Data;

@Data
public class CreateAppointmentDTO {
    private Long patientId;
    private Long doctorId;
    private Long departmentId;
    private Long slotId;
    private Long sessionId;
}
