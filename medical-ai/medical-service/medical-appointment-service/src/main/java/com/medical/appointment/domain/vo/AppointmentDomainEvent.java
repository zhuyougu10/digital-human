package com.medical.appointment.domain.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Data;

@Data
public class AppointmentDomainEvent {

    private Long appointmentId;
    private Long patientId;
    private Long doctorId;
    private Long departmentId;
    private Long slotId;
    private Long sessionId;
    private LocalDate appointmentDate;
    private String period;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer queueNumber;
    private Integer status;
    private String cancelReason;
    private String eventType;
    private LocalDateTime occurredAt;
}
