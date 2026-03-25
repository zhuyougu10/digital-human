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
@TableName("appointment_event_outbox")
public class AppointmentEventOutbox extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long appointmentId;
    private String eventType;
    private String routingKey;
    private String payload;
    private Integer publishStatus;
    private Integer retryCount;
    private String lastError;
    private LocalDateTime occurredAt;
    private LocalDateTime publishedAt;
}
