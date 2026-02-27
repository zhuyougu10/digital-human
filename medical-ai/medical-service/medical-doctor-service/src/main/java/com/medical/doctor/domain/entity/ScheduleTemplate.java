package com.medical.doctor.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Data;

@Data
@TableName("schedule_template")
public class ScheduleTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long doctorId;
    private Integer dayOfWeek;
    private String period;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer maxPatients;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
