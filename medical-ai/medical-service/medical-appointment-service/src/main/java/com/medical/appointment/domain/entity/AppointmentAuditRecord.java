package com.medical.appointment.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.medical.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("appointment_audit_record")
public class AppointmentAuditRecord extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long eventId;
    private Long appointmentId;
    private String actionType;
    private Long operatorId;
    private String detail;
    private LocalDateTime actionTime;
}
