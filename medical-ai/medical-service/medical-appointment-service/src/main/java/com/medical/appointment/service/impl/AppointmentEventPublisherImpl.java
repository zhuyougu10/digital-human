package com.medical.appointment.service.impl;

import com.medical.appointment.domain.entity.AppointmentEventOutbox;
import com.medical.appointment.mapper.AppointmentEventOutboxMapper;
import com.medical.appointment.service.AppointmentEventPublisher;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "medical.mq", name = "enabled", havingValue = "true")
public class AppointmentEventPublisherImpl implements AppointmentEventPublisher {

    public static final String EVENT_ID_HEADER = AppointmentEventPublisher.EVENT_ID_HEADER;
    static final int PUBLISH_STATUS_PENDING = 0;
    static final int PUBLISH_STATUS_PUBLISHED = 1;
    static final int PUBLISH_STATUS_PUBLISHING = 2;

    private final AppointmentEventOutboxMapper appointmentEventOutboxMapper;
    private final RabbitTemplate rabbitTemplate;
    private final String exchangeName;
    private final int claimTimeoutSeconds;
    private final int confirmTimeoutSeconds;

    public AppointmentEventPublisherImpl(
            AppointmentEventOutboxMapper appointmentEventOutboxMapper,
            RabbitTemplate rabbitTemplate,
            @Value("${medical.mq.exchange}") String exchangeName,
            @Value("${medical.mq.publish-claim-timeout-seconds:120}") int claimTimeoutSeconds,
            @Value("${medical.mq.publish-confirm-timeout-seconds:10}") int confirmTimeoutSeconds) {
        this.appointmentEventOutboxMapper = appointmentEventOutboxMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.exchangeName = exchangeName;
        this.claimTimeoutSeconds = claimTimeoutSeconds;
        this.confirmTimeoutSeconds = confirmTimeoutSeconds;
    }

    @Override
    public void publishPendingEvents() {
        LocalDateTime reclaimBefore = LocalDateTime.now().minusSeconds(claimTimeoutSeconds);
        List<AppointmentEventOutbox> pendingEvents = appointmentEventOutboxMapper.selectPublishCandidates(
                PUBLISH_STATUS_PENDING, PUBLISH_STATUS_PUBLISHING, reclaimBefore, 100);
        for (AppointmentEventOutbox outbox : pendingEvents) {
            if (!claimForPublish(outbox.getId(), reclaimBefore)) {
                continue;
            }
            publishSingleEvent(outbox);
        }
    }

    private boolean claimForPublish(Long outboxId, LocalDateTime reclaimBefore) {
        return appointmentEventOutboxMapper.claimForPublish(
                outboxId,
                PUBLISH_STATUS_PENDING,
                PUBLISH_STATUS_PUBLISHING,
                reclaimBefore) > 0;
    }

    private void publishSingleEvent(AppointmentEventOutbox outbox) {
        try {
            CorrelationData correlationData = new CorrelationData(String.valueOf(outbox.getId()));
            rabbitTemplate.convertAndSend(
                    exchangeName,
                    outbox.getRoutingKey(),
                    outbox.getPayload(),
                    buildMessagePostProcessor(outbox.getId()),
                    correlationData);
            CorrelationData.Confirm confirm = correlationData.getFuture().get(confirmTimeoutSeconds, TimeUnit.SECONDS);
            ReturnedMessage returned = correlationData.getReturned();
            if (returned != null) {
                throw new IllegalStateException(buildReturnedMessage(returned));
            }
            if (confirm == null || !confirm.isAck()) {
                throw new IllegalStateException(buildNackMessage(confirm));
            }
            appointmentEventOutboxMapper.markPublished(outbox.getId(), PUBLISH_STATUS_PUBLISHING, LocalDateTime.now());
        } catch (Exception e) {
            log.error("Failed to publish appointment outbox event, outboxId={}, eventType={}",
                    outbox.getId(), outbox.getEventType(), e);
            appointmentEventOutboxMapper.markPendingForRetry(
                    outbox.getId(),
                    PUBLISH_STATUS_PUBLISHING,
                    nextRetryCount(outbox),
                    e.getMessage(),
                    PUBLISH_STATUS_PENDING);
        }
    }

    private MessagePostProcessor buildMessagePostProcessor(Long eventId) {
        return message -> {
            message.getMessageProperties().setHeader(EVENT_ID_HEADER, eventId);
            return message;
        };
    }

    private int nextRetryCount(AppointmentEventOutbox outbox) {
        return (outbox.getRetryCount() == null ? 0 : outbox.getRetryCount()) + 1;
    }

    private String buildNackMessage(CorrelationData.Confirm confirm) {
        if (confirm == null || confirm.getReason() == null || confirm.getReason().isBlank()) {
            return "broker nack";
        }
        return "broker nack: " + confirm.getReason();
    }

    private String buildReturnedMessage(ReturnedMessage returned) {
        return "broker returned replyCode=" + returned.getReplyCode() + ", replyText=" + returned.getReplyText();
    }
}
