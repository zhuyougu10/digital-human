package com.medical.ai.agent.tool;

import lombok.Data;

@Data
public class CreateAppointmentRequest {
    private Long patientId;
    private Long doctorId;
    private Long slotId;
}
