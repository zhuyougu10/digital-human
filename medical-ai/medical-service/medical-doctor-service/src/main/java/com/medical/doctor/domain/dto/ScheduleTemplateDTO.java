package com.medical.doctor.domain.dto;

import java.time.LocalTime;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ScheduleTemplateDTO {
    @NotNull(message = "星期不能为空")
    private Integer dayOfWeek;
    private String period;
    @NotNull(message = "开始时间不能为空")
    private LocalTime startTime;
    @NotNull(message = "结束时间不能为空")
    private LocalTime endTime;
    private Integer maxPatients;
    private Integer status;
}
