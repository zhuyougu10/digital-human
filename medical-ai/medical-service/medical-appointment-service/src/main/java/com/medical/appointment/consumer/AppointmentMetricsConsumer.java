package com.medical.appointment.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.appointment.domain.entity.AppointmentEventConsumeLog;
import com.medical.appointment.domain.vo.AppointmentDomainEvent;
import com.medical.appointment.mapper.AppointmentEventConsumeLogMapper;
import com.medical.appointment.service.AppointmentEventPublisher;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "medical.mq", name = "enabled", havingValue = "true")
public class AppointmentMetricsConsumer {

    private static final int CONSUME_STATUS_FAILED = 0;
    private static final int CONSUME_STATUS_SUCCESS = 1;
    private static final String CONSUMER_NAME = "AppointmentMetricsConsumer";

    private final ObjectMapper objectMapper;
    private final AppointmentEventConsumeLogMapper appointmentEventConsumeLogMapper;
    private final String queueName;

    public AppointmentMetricsConsumer(
            ObjectMapper objectMapper,
            AppointmentEventConsumeLogMapper appointmentEventConsumeLogMapper,
            @Value("${medical.mq.metrics-queue}") String queueName) {
        this.objectMapper = objectMapper;
        this.appointmentEventConsumeLogMapper = appointmentEventConsumeLogMapper;
        this.queueName = queueName;
    }

    @RabbitListener(queues = "${medical.mq.metrics-queue}")
    public void consume(Message message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        Long eventId = extractEventId(message);
        AppointmentEventConsumeLog existingLog = findConsumeLog(eventId);
        if (existingLog != null && CONSUME_STATUS_SUCCESS == existingLog.getConsumeStatus()) {
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            AppointmentDomainEvent event = objectMapper.readValue(message.getBody(), AppointmentDomainEvent.class);
            log.info("Processed appointment metrics event, eventId={}, appointmentId={}, eventType={}",
                    eventId, event.getAppointmentId(), event.getEventType());
            saveSuccessLog(eventId, existingLog);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Failed to consume appointment metrics event, eventId={}", eventId, e);
            saveFailureLog(eventId, existingLog, e.getMessage());
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private AppointmentEventConsumeLog findConsumeLog(Long eventId) {
        return appointmentEventConsumeLogMapper.selectOne(new LambdaQueryWrapper<AppointmentEventConsumeLog>()
                .eq(AppointmentEventConsumeLog::getEventId, eventId)
                .eq(AppointmentEventConsumeLog::getConsumerName, CONSUMER_NAME));
    }

    private void saveSuccessLog(Long eventId, AppointmentEventConsumeLog existingLog) {
        AppointmentEventConsumeLog logRecord = existingLog == null ? new AppointmentEventConsumeLog() : existingLog;
        logRecord.setEventId(eventId);
        logRecord.setConsumerName(CONSUMER_NAME);
        logRecord.setQueueName(queueName);
        logRecord.setConsumeStatus(CONSUME_STATUS_SUCCESS);
        logRecord.setErrorMessage(null);
        logRecord.setConsumedAt(LocalDateTime.now());
        if (existingLog == null) {
            logRecord.setRetryCount(0);
            appointmentEventConsumeLogMapper.insert(logRecord);
            return;
        }
        appointmentEventConsumeLogMapper.updateById(logRecord);
    }

    private void saveFailureLog(Long eventId, AppointmentEventConsumeLog existingLog, String errorMessage) {
        AppointmentEventConsumeLog logRecord = existingLog == null ? new AppointmentEventConsumeLog() : existingLog;
        logRecord.setEventId(eventId);
        logRecord.setConsumerName(CONSUMER_NAME);
        logRecord.setQueueName(queueName);
        logRecord.setConsumeStatus(CONSUME_STATUS_FAILED);
        logRecord.setRetryCount(existingLog == null ? 1 : (existingLog.getRetryCount() == null ? 0 : existingLog.getRetryCount()) + 1);
        logRecord.setErrorMessage(errorMessage);
        logRecord.setConsumedAt(LocalDateTime.now());
        if (existingLog == null) {
            appointmentEventConsumeLogMapper.insert(logRecord);
            return;
        }
        appointmentEventConsumeLogMapper.updateById(logRecord);
    }

    private Long extractEventId(Message message) {
        Object headerValue = message.getMessageProperties().getHeaders().get(AppointmentEventPublisher.EVENT_ID_HEADER);
        if (headerValue instanceof Long value) {
            return value;
        }
        if (headerValue instanceof Integer value) {
            return value.longValue();
        }
        if (headerValue instanceof String value) {
            return Long.parseLong(value);
        }
        throw new IllegalArgumentException("Missing appointment event id header");
    }
}
