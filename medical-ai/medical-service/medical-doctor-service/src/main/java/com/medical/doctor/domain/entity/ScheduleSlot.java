package com.medical.doctor.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Data;

@Data
@TableName("schedule_slot")
public class ScheduleSlot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long doctorId;
    private LocalDate scheduleDate;
    private String period;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer totalSlots;
    private Integer bookedSlots;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
