package com.medical.appointment.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.appointment.domain.entity.AppointmentEventConsumeLog;
import com.medical.appointment.domain.entity.AppointmentNotificationRecord;
import com.medical.appointment.domain.vo.AppointmentDomainEvent;
import com.medical.appointment.mapper.AppointmentEventConsumeLogMapper;
import com.medical.appointment.mapper.AppointmentNotificationRecordMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

@ExtendWith(MockitoExtension.class)
class AppointmentNotificationConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private AppointmentNotificationRecordMapper appointmentNotificationRecordMapper;

    @Mock
    private AppointmentEventConsumeLogMapper appointmentEventConsumeLogMapper;

    @Mock
    private Channel channel;

    private AppointmentNotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AppointmentNotificationConsumer(
                objectMapper,
                appointmentNotificationRecordMapper,
                appointmentEventConsumeLogMapper,
                "medical.notification.queue");
    }

    @Test
    void consume_shouldPersistNotificationRecordAndAckWhenEventIsNew() throws Exception {
        AppointmentDomainEvent event = buildEvent();
        when(appointmentEventConsumeLogMapper.selectOne(any())).thenReturn(null);

        consumer.consume(buildMessage(101L, event), channel, 7L);

        ArgumentCaptor<AppointmentNotificationRecord> recordCaptor =
                ArgumentCaptor.forClass(AppointmentNotificationRecord.class);
        verify(appointmentNotificationRecordMapper).insert(recordCaptor.capture());
        AppointmentNotificationRecord record = recordCaptor.getValue();
        assertEquals(101L, record.getEventId());
        assertEquals(event.getAppointmentId(), record.getAppointmentId());
        assertEquals(event.getPatientId(), record.getPatientId());
        assertEquals(event.getEventType(), record.getNotificationType());
        assertEquals(1, record.getStatus());
        assertNotNull(record.getSentAt());

        ArgumentCaptor<AppointmentEventConsumeLog> logCaptor = ArgumentCaptor.forClass(AppointmentEventConsumeLog.class);
        verify(appointmentEventConsumeLogMapper).insert(logCaptor.capture());
        assertEquals(1, logCaptor.getValue().getConsumeStatus());
        assertEquals("AppointmentNotificationConsumer", logCaptor.getValue().getConsumerName());
        assertEquals("medical.notification.queue", logCaptor.getValue().getQueueName());
        verify(channel).basicAck(7L, false);
    }

    @Test
    void consume_shouldAckAndSkipWhenEventAlreadyProcessed() throws Exception {
        AppointmentEventConsumeLog existing = new AppointmentEventConsumeLog();
        existing.setId(1L);
        existing.setConsumeStatus(1);
        when(appointmentEventConsumeLogMapper.selectOne(any())).thenReturn(existing);

        consumer.consume(buildMessage(101L, buildEvent()), channel, 8L);

        verify(appointmentNotificationRecordMapper, never()).insert(any(AppointmentNotificationRecord.class));
        verify(appointmentEventConsumeLogMapper, never()).insert(any(AppointmentEventConsumeLog.class));
        verify(channel).basicAck(8L, false);
    }

    @Test
    void consume_shouldRecordFailureAndRejectWithoutRequeueWhenInsertFails() throws Exception {
        AppointmentDomainEvent event = buildEvent();
        when(appointmentEventConsumeLogMapper.selectOne(any())).thenReturn(null);
        doThrow(new RuntimeException("db unavailable"))
                .when(appointmentNotificationRecordMapper)
                .insert(any(AppointmentNotificationRecord.class));

        consumer.consume(buildMessage(101L, event), channel, 9L);

        ArgumentCaptor<AppointmentEventConsumeLog> logCaptor = ArgumentCaptor.forClass(AppointmentEventConsumeLog.class);
        verify(appointmentEventConsumeLogMapper).insert(logCaptor.capture());
        assertEquals(0, logCaptor.getValue().getConsumeStatus());
        assertEquals(1, logCaptor.getValue().getRetryCount());
        assertEquals("db unavailable", logCaptor.getValue().getErrorMessage());
        verify(channel).basicNack(9L, false, false);
    }

    @Test
    void consume_shouldTreatDuplicateNotificationInsertAsAlreadyAppliedSuccess() throws Exception {
        AppointmentDomainEvent event = buildEvent();
        when(appointmentEventConsumeLogMapper.selectOne(any())).thenReturn(null);
        doThrow(new org.springframework.dao.DuplicateKeyException("duplicate notification"))
                .when(appointmentNotificationRecordMapper)
                .insert(any(AppointmentNotificationRecord.class));

        consumer.consume(buildMessage(101L, event), channel, 10L);

        ArgumentCaptor<AppointmentEventConsumeLog> logCaptor = ArgumentCaptor.forClass(AppointmentEventConsumeLog.class);
        verify(appointmentEventConsumeLogMapper).insert(logCaptor.capture());
        assertEquals(1, logCaptor.getValue().getConsumeStatus());
        assertEquals(0, logCaptor.getValue().getRetryCount());
        verify(channel).basicAck(10L, false);
        verify(channel, never()).basicNack(10L, false, false);
    }

    private Message buildMessage(Long eventId, AppointmentDomainEvent event) throws IOException {
        MessageProperties properties = new MessageProperties();
        properties.setHeader("appointmentEventId", eventId);
        return new Message(objectMapper.writeValueAsBytes(event), properties);
    }

    private AppointmentDomainEvent buildEvent() {
        AppointmentDomainEvent event = new AppointmentDomainEvent();
        event.setAppointmentId(10L);
        event.setPatientId(20L);
        event.setDoctorId(30L);
        event.setEventType("APPOINTMENT_CREATED");
        event.setOccurredAt(LocalDateTime.of(2026, 3, 25, 10, 0));
        return event;
    }
}
