package com.medical.appointment.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.appointment.domain.entity.Appointment;
import com.medical.appointment.domain.entity.AppointmentEventOutbox;
import com.medical.appointment.domain.vo.AppointmentDomainEvent;
import com.medical.appointment.mapper.AppointmentEventOutboxMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppointmentEventOutboxServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private AppointmentEventOutboxMapper appointmentEventOutboxMapper;

    private AppointmentEventOutboxServiceImpl appointmentEventOutboxService;

    @BeforeEach
    void setUp() {
        appointmentEventOutboxService = new AppointmentEventOutboxServiceImpl(appointmentEventOutboxMapper, objectMapper);
    }

    @Test
    void saveCreatedEvent_shouldSerializeAppointmentPayloadIntoOutboxRecord() {
        Appointment appointment = buildAppointment();

        appointmentEventOutboxService.saveCreatedEvent(appointment);

        ArgumentCaptor<AppointmentEventOutbox> captor = ArgumentCaptor.forClass(AppointmentEventOutbox.class);
        verify(appointmentEventOutboxMapper).insert(captor.capture());

        AppointmentEventOutbox outbox = captor.getValue();
        assertEquals(appointment.getId(), outbox.getAppointmentId());
        assertEquals(AppointmentEventOutboxServiceImpl.EVENT_TYPE_CREATED, outbox.getEventType());
        assertEquals(AppointmentEventOutboxServiceImpl.ROUTING_KEY_CREATED, outbox.getRoutingKey());
        assertEquals(0, outbox.getPublishStatus());
        assertEquals(0, outbox.getRetryCount());
        assertNotNull(outbox.getOccurredAt());
        assertNotNull(outbox.getPayload());
        AppointmentDomainEvent event = extract(outbox);
        assertEquals(appointment.getCancelReason(), event.getCancelReason());
        assertEquals(appointment.getAppointmentDate(), event.getAppointmentDate());
        assertEquals(appointment.getStartTime(), event.getStartTime());
    }

    private AppointmentDomainEvent extract(AppointmentEventOutbox outbox) {
        try {
            return objectMapper.readValue(outbox.getPayload(), AppointmentDomainEvent.class);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private Appointment buildAppointment() {
        Appointment appointment = new Appointment();
        appointment.setId(10L);
        appointment.setPatientId(11L);
        appointment.setDoctorId(12L);
        appointment.setDepartmentId(13L);
        appointment.setSlotId(14L);
        appointment.setSessionId(15L);
        appointment.setAppointmentDate(LocalDate.of(2026, 3, 26));
        appointment.setPeriod("morning");
        appointment.setStartTime(LocalTime.of(9, 0));
        appointment.setEndTime(LocalTime.of(9, 30));
        appointment.setQueueNumber(3);
        appointment.setStatus(0);
        appointment.setCancelReason("none");
        appointment.setCreateTime(LocalDateTime.of(2026, 3, 25, 10, 0));
        return appointment;
    }
}
