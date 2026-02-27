package com.medical.appointment.domain.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Data;

@Data
public class AppointmentVO {
    private Long id;
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
    private String doctorName;
    private String departmentName;
    private String conversationSummary;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
