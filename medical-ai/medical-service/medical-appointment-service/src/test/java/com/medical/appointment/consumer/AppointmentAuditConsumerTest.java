package com.medical.appointment.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.appointment.domain.entity.AppointmentAuditRecord;
import com.medical.appointment.domain.entity.AppointmentEventConsumeLog;
import com.medical.appointment.domain.vo.AppointmentDomainEvent;
import com.medical.appointment.mapper.AppointmentAuditRecordMapper;
import com.medical.appointment.mapper.AppointmentEventConsumeLogMapper;
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
class AppointmentAuditConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private AppointmentAuditRecordMapper appointmentAuditRecordMapper;

    @Mock
    private AppointmentEventConsumeLogMapper appointmentEventConsumeLogMapper;

    @Mock
    private Channel channel;

    private AppointmentAuditConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AppointmentAuditConsumer(
                objectMapper,
                appointmentAuditRecordMapper,
                appointmentEventConsumeLogMapper,
                "medical.audit.queue");
    }

    @Test
    void consume_shouldPersistAuditRecordAndAckWhenEventIsNew() throws Exception {
        AppointmentDomainEvent event = buildEvent();
        when(appointmentEventConsumeLogMapper.selectOne(any())).thenReturn(null);

        consumer.consume(buildMessage(301L, event), channel, 21L);

        ArgumentCaptor<AppointmentAuditRecord> recordCaptor = ArgumentCaptor.forClass(AppointmentAuditRecord.class);
        verify(appointmentAuditRecordMapper).insert(recordCaptor.capture());
        AppointmentAuditRecord record = recordCaptor.getValue();
        assertEquals(301L, record.getEventId());
        assertEquals(event.getAppointmentId(), record.getAppointmentId());
        assertEquals(event.getEventType(), record.getActionType());
        assertEquals(event.getPatientId(), record.getOperatorId());
        assertNotNull(record.getActionTime());

        ArgumentCaptor<AppointmentEventConsumeLog> logCaptor = ArgumentCaptor.forClass(AppointmentEventConsumeLog.class);
        verify(appointmentEventConsumeLogMapper).insert(logCaptor.capture());
        assertEquals(1, logCaptor.getValue().getConsumeStatus());
        assertEquals("AppointmentAuditConsumer", logCaptor.getValue().getConsumerName());
        verify(channel).basicAck(21L, false);
    }

    @Test
    void consume_shouldAckAndSkipWhenEventAlreadyProcessed() throws Exception {
        AppointmentEventConsumeLog existing = new AppointmentEventConsumeLog();
        existing.setConsumeStatus(1);
        when(appointmentEventConsumeLogMapper.selectOne(any())).thenReturn(existing);

        consumer.consume(buildMessage(301L, buildEvent()), channel, 22L);

        verify(appointmentAuditRecordMapper, never()).insert(any(AppointmentAuditRecord.class));
        verify(appointmentEventConsumeLogMapper, never()).insert(any(AppointmentEventConsumeLog.class));
        verify(channel).basicAck(22L, false);
    }

    @Test
    void consume_shouldRecordFailureAndRejectWithoutRequeueWhenInsertFails() throws Exception {
        AppointmentDomainEvent event = buildEvent();
        when(appointmentEventConsumeLogMapper.selectOne(any())).thenReturn(null);
        doThrow(new RuntimeException("audit insert failed"))
                .when(appointmentAuditRecordMapper)
                .insert(any(AppointmentAuditRecord.class));

        consumer.consume(buildMessage(301L, event), channel, 23L);

        ArgumentCaptor<AppointmentEventConsumeLog> logCaptor = ArgumentCaptor.forClass(AppointmentEventConsumeLog.class);
        verify(appointmentEventConsumeLogMapper).insert(logCaptor.capture());
        assertEquals(0, logCaptor.getValue().getConsumeStatus());
        assertEquals(1, logCaptor.getValue().getRetryCount());
        assertEquals("audit insert failed", logCaptor.getValue().getErrorMessage());
        verify(channel).basicNack(23L, false, false);
    }

    @Test
    void consume_shouldTreatDuplicateAuditInsertAsAlreadyAppliedSuccess() throws Exception {
        AppointmentDomainEvent event = buildEvent();
        when(appointmentEventConsumeLogMapper.selectOne(any())).thenReturn(null);
        doThrow(new org.springframework.dao.DuplicateKeyException("duplicate audit"))
                .when(appointmentAuditRecordMapper)
                .insert(any(AppointmentAuditRecord.class));

        consumer.consume(buildMessage(301L, event), channel, 24L);

        ArgumentCaptor<AppointmentEventConsumeLog> logCaptor = ArgumentCaptor.forClass(AppointmentEventConsumeLog.class);
        verify(appointmentEventConsumeLogMapper).insert(logCaptor.capture());
        assertEquals(1, logCaptor.getValue().getConsumeStatus());
        assertEquals(0, logCaptor.getValue().getRetryCount());
        verify(channel).basicAck(24L, false);
        verify(channel, never()).basicNack(24L, false, false);
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
        event.setEventType("APPOINTMENT_CANCELLED");
        event.setOccurredAt(LocalDateTime.of(2026, 3, 25, 10, 0));
        return event;
    }
}
