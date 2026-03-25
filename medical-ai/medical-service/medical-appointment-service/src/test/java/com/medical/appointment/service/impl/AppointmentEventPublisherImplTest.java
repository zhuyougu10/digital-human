package com.medical.appointment.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medical.appointment.domain.entity.AppointmentEventOutbox;
import com.medical.appointment.mapper.AppointmentEventOutboxMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class AppointmentEventPublisherImplTest {

    @Mock
    private AppointmentEventOutboxMapper appointmentEventOutboxMapper;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private AppointmentEventPublisherImpl appointmentEventPublisher;

    @BeforeEach
    void setUp() {
        appointmentEventPublisher =
                new AppointmentEventPublisherImpl(appointmentEventOutboxMapper, rabbitTemplate, "medical.event", 50, 10);
    }

    @Test
    void publishPendingEvents_shouldClaimPendingOutboxRowsAndMarkPublishedOnlyAfterBrokerConfirm() {
        AppointmentEventOutbox outbox = buildOutbox();
        when(appointmentEventOutboxMapper.selectPublishCandidates(anyInt(), anyInt(), any(), anyInt()))
                .thenReturn(List.of(outbox));
        when(appointmentEventOutboxMapper.claimForPublish(eq(outbox.getId()), anyInt(), anyInt(), any()))
                .thenReturn(1);
        when(appointmentEventOutboxMapper.markPublished(eq(outbox.getId()), anyInt(), any())).thenReturn(1);
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(4);
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate)
                .convertAndSend(eq("medical.event"), eq(outbox.getRoutingKey()), eq(outbox.getPayload()),
                        any(MessagePostProcessor.class), any(CorrelationData.class));

        appointmentEventPublisher.publishPendingEvents();

        ArgumentCaptor<MessagePostProcessor> messagePostProcessorCaptor =
                ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate)
                .convertAndSend(eq("medical.event"), eq(outbox.getRoutingKey()), eq(outbox.getPayload()),
                        messagePostProcessorCaptor.capture(), any(CorrelationData.class));

        Message message = messagePostProcessorCaptor.getValue().postProcessMessage(new Message(new byte[0]));
        assertEquals(outbox.getId(), message.getMessageProperties().getHeaders()
                .get(AppointmentEventPublisherImpl.EVENT_ID_HEADER));

        verify(appointmentEventOutboxMapper).claimForPublish(eq(outbox.getId()),
                eq(AppointmentEventPublisherImpl.PUBLISH_STATUS_PENDING),
                eq(AppointmentEventPublisherImpl.PUBLISH_STATUS_PUBLISHING), any());

        ArgumentCaptor<LocalDateTime> publishedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(appointmentEventOutboxMapper).markPublished(
                eq(outbox.getId()), eq(AppointmentEventPublisherImpl.PUBLISH_STATUS_PUBLISHING), publishedAtCaptor.capture());
        assertNotNull(publishedAtCaptor.getValue());
        verify(appointmentEventOutboxMapper, never()).markPendingForRetry(any(), anyInt(), anyInt(), anyString(), anyInt());
    }

    @Test
    void publishPendingEvents_shouldReturnClaimToPendingAndIncrementRetryCountWhenBrokerNacks() {
        AppointmentEventOutbox outbox = buildOutbox();
        outbox.setRetryCount(2);
        when(appointmentEventOutboxMapper.selectPublishCandidates(anyInt(), anyInt(), any(), anyInt()))
                .thenReturn(List.of(outbox));
        when(appointmentEventOutboxMapper.claimForPublish(eq(outbox.getId()), anyInt(), anyInt(), any()))
                .thenReturn(1);
        when(appointmentEventOutboxMapper.markPendingForRetry(eq(outbox.getId()), eq(AppointmentEventPublisherImpl.PUBLISH_STATUS_PUBLISHING),
                eq(3), eq("broker nack: exchange missing"), eq(AppointmentEventPublisherImpl.PUBLISH_STATUS_PENDING))).thenReturn(1);
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(4);
            correlationData.getFuture().complete(new CorrelationData.Confirm(false, "exchange missing"));
            return null;
        }).when(rabbitTemplate)
                .convertAndSend(eq("medical.event"), eq(outbox.getRoutingKey()), eq(outbox.getPayload()),
                        any(MessagePostProcessor.class), any(CorrelationData.class));

        appointmentEventPublisher.publishPendingEvents();

        verify(appointmentEventOutboxMapper).markPendingForRetry(
                outbox.getId(), AppointmentEventPublisherImpl.PUBLISH_STATUS_PUBLISHING, 3,
                "broker nack: exchange missing", AppointmentEventPublisherImpl.PUBLISH_STATUS_PENDING);
        verify(appointmentEventOutboxMapper, never()).markPublished(any(), anyInt(), any());
    }

    @Test
    void publishPendingEvents_shouldReturnClaimToPendingWhenBrokerReturnsMessage() {
        AppointmentEventOutbox outbox = buildOutbox();
        when(appointmentEventOutboxMapper.selectPublishCandidates(anyInt(), anyInt(), any(), anyInt()))
                .thenReturn(List.of(outbox));
        when(appointmentEventOutboxMapper.claimForPublish(eq(outbox.getId()), anyInt(), anyInt(), any()))
                .thenReturn(1);
        when(appointmentEventOutboxMapper.markPendingForRetry(eq(outbox.getId()), eq(AppointmentEventPublisherImpl.PUBLISH_STATUS_PUBLISHING),
                eq(1), eq("broker returned replyCode=312, replyText=NO_ROUTE"), eq(AppointmentEventPublisherImpl.PUBLISH_STATUS_PENDING))).thenReturn(1);
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(4);
            correlationData.setReturned(new ReturnedMessage(new Message(new byte[0]), 312, "NO_ROUTE",
                    "medical.event", outbox.getRoutingKey()));
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate)
                .convertAndSend(eq("medical.event"), eq(outbox.getRoutingKey()), eq(outbox.getPayload()),
                        any(MessagePostProcessor.class), any(CorrelationData.class));

        appointmentEventPublisher.publishPendingEvents();

        verify(appointmentEventOutboxMapper).markPendingForRetry(
                outbox.getId(), AppointmentEventPublisherImpl.PUBLISH_STATUS_PUBLISHING, 1,
                "broker returned replyCode=312, replyText=NO_ROUTE", AppointmentEventPublisherImpl.PUBLISH_STATUS_PENDING);
        verify(appointmentEventOutboxMapper, never()).markPublished(any(), anyInt(), any());
    }

    @Test
    void publishPendingEvents_shouldSkipPublishWhenAnotherInstanceAlreadyClaimedRow() {
        AppointmentEventOutbox outbox = buildOutbox();
        when(appointmentEventOutboxMapper.selectPublishCandidates(anyInt(), anyInt(), any(), anyInt()))
                .thenReturn(List.of(outbox));
        when(appointmentEventOutboxMapper.claimForPublish(eq(outbox.getId()), anyInt(), anyInt(), any()))
                .thenReturn(0);

        appointmentEventPublisher.publishPendingEvents();

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class), any(CorrelationData.class));
        verify(appointmentEventOutboxMapper, never()).markPublished(any(), anyInt(), any());
        verify(appointmentEventOutboxMapper, never()).markPendingForRetry(any(), anyInt(), anyInt(), anyString(), anyInt());
    }

    private AppointmentEventOutbox buildOutbox() {
        AppointmentEventOutbox outbox = new AppointmentEventOutbox();
        outbox.setId(1L);
        outbox.setAppointmentId(10L);
        outbox.setEventType("APPOINTMENT_CREATED");
        outbox.setRoutingKey("appointment.created");
        outbox.setPayload("{\"appointmentId\":10}");
        outbox.setPublishStatus(0);
        outbox.setRetryCount(0);
        outbox.setOccurredAt(LocalDateTime.of(2026, 3, 25, 10, 0));
        return outbox;
    }
}
