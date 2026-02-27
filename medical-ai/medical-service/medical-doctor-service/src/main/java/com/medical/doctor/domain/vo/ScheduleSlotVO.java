package com.medical.doctor.domain.vo;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Data;

@Data
public class ScheduleSlotVO {
    private Long id;
    private Long doctorId;
    private String doctorName;
    private LocalDate scheduleDate;
    private String period;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer totalSlots;
    private Integer bookedSlots;
    private Integer availableSlots;
    private Integer status;
}
