package com.medical.appointment.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.medical.common.core.domain.BaseEntity;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("appointment")
public class Appointment extends BaseEntity {

    @TableId(type = IdType.AUTO)
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
}
