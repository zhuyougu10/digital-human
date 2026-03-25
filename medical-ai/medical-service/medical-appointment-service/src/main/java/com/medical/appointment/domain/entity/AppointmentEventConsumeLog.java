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
@TableName("appointment_event_consume_log")
public class AppointmentEventConsumeLog extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long eventId;
    private String consumerName;
    private String queueName;
    private Integer consumeStatus;
    private Integer retryCount;
    private String errorMessage;
    private LocalDateTime consumedAt;
}
