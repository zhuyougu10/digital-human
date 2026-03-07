package com.medical.doctor.domain.dto;

import java.time.LocalTime;
import lombok.Data;

@Data
public class ScheduleTemplateDTO {
    private Integer dayOfWeek;
    private String period;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer maxPatients;
    private Integer status;
}
