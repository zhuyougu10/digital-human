package com.medical.appointment.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.appointment.domain.entity.AppointmentEventConsumeLog;
import com.medical.appointment.domain.vo.AppointmentDomainEvent;
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
class AppointmentMetricsConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private AppointmentEventConsumeLogMapper appointmentEventConsumeLogMapper;

    @Mock
    private Channel channel;

    private AppointmentMetricsConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AppointmentMetricsConsumer(objectMapper, appointmentEventConsumeLogMapper, "medical.metrics.queue");
    }

    @Test
    void consume_shouldRecordSuccessAndAckWhenEventIsNew() throws Exception {
        when(appointmentEventConsumeLogMapper.selectOne(any())).thenReturn(null);

        consumer.consume(buildMessage(201L, buildEvent()), channel, 11L);

        ArgumentCaptor<AppointmentEventConsumeLog> logCaptor = ArgumentCaptor.forClass(AppointmentEventConsumeLog.class);
        verify(appointmentEventConsumeLogMapper).insert(logCaptor.capture());
        assertEquals(201L, logCaptor.getValue().getEventId());
        assertEquals(1, logCaptor.getValue().getConsumeStatus());
        assertEquals("AppointmentMetricsConsumer", logCaptor.getValue().getConsumerName());
        verify(channel).basicAck(11L, false);
    }

    @Test
    void consume_shouldAckAndSkipWhenEventAlreadyProcessed() throws Exception {
        AppointmentEventConsumeLog existing = new AppointmentEventConsumeLog();
        existing.setConsumeStatus(1);
        when(appointmentEventConsumeLogMapper.selectOne(any())).thenReturn(existing);

        consumer.consume(buildMessage(201L, buildEvent()), channel, 12L);

        verify(appointmentEventConsumeLogMapper, never()).insert(any(AppointmentEventConsumeLog.class));
        verify(channel).basicAck(12L, false);
    }

    @Test
    void consume_shouldRecordFailureAndRejectWhenPayloadCannotBeRead() throws Exception {
        when(appointmentEventConsumeLogMapper.selectOne(any())).thenReturn(null);

        consumer.consume(buildInvalidMessage(201L), channel, 13L);

        ArgumentCaptor<AppointmentEventConsumeLog> logCaptor = ArgumentCaptor.forClass(AppointmentEventConsumeLog.class);
        verify(appointmentEventConsumeLogMapper).insert(logCaptor.capture());
        assertEquals(0, logCaptor.getValue().getConsumeStatus());
        assertEquals(1, logCaptor.getValue().getRetryCount());
        verify(channel).basicNack(13L, false, false);
    }

    private Message buildMessage(Long eventId, AppointmentDomainEvent event) throws IOException {
        MessageProperties properties = new MessageProperties();
        properties.setHeader("appointmentEventId", eventId);
        return new Message(objectMapper.writeValueAsBytes(event), properties);
    }

    private Message buildInvalidMessage(Long eventId) {
        MessageProperties properties = new MessageProperties();
        properties.setHeader("appointmentEventId", eventId);
        return new Message("not-json".getBytes(), properties);
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
