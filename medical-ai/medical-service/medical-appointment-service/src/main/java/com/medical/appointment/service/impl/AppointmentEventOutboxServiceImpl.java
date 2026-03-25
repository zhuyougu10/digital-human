package com.medical.appointment.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.appointment.domain.entity.Appointment;
import com.medical.appointment.domain.entity.AppointmentEventOutbox;
import com.medical.appointment.domain.vo.AppointmentDomainEvent;
import com.medical.appointment.mapper.AppointmentEventOutboxMapper;
import com.medical.appointment.service.AppointmentEventOutboxService;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentEventOutboxServiceImpl implements AppointmentEventOutboxService {

    static final String EVENT_TYPE_CREATED = "APPOINTMENT_CREATED";
    static final String EVENT_TYPE_CANCELLED = "APPOINTMENT_CANCELLED";
    static final String ROUTING_KEY_CREATED = "appointment.created";
    static final String ROUTING_KEY_CANCELLED = "appointment.cancelled";
    private static final int PUBLISH_STATUS_PENDING = 0;
    private static final int INITIAL_RETRY_COUNT = 0;

    private final AppointmentEventOutboxMapper appointmentEventOutboxMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void saveCreatedEvent(Appointment appointment) {
        saveEvent(appointment, EVENT_TYPE_CREATED, ROUTING_KEY_CREATED);
    }

    @Override
    public void saveCancelledEvent(Appointment appointment) {
        saveEvent(appointment, EVENT_TYPE_CANCELLED, ROUTING_KEY_CANCELLED);
    }

    private void saveEvent(Appointment appointment, String eventType, String routingKey) {
        LocalDateTime occurredAt = LocalDateTime.now();
        AppointmentEventOutbox outbox = new AppointmentEventOutbox();
        outbox.setAppointmentId(appointment.getId());
        outbox.setEventType(eventType);
        outbox.setRoutingKey(routingKey);
        outbox.setPayload(serialize(buildEvent(appointment, eventType, occurredAt)));
        outbox.setPublishStatus(PUBLISH_STATUS_PENDING);
        outbox.setRetryCount(INITIAL_RETRY_COUNT);
        outbox.setOccurredAt(occurredAt);
        appointmentEventOutboxMapper.insert(outbox);
    }

    private AppointmentDomainEvent buildEvent(Appointment appointment, String eventType, LocalDateTime occurredAt) {
        AppointmentDomainEvent event = new AppointmentDomainEvent();
        event.setAppointmentId(appointment.getId());
        event.setPatientId(appointment.getPatientId());
        event.setDoctorId(appointment.getDoctorId());
        event.setDepartmentId(appointment.getDepartmentId());
        event.setSlotId(appointment.getSlotId());
        event.setSessionId(appointment.getSessionId());
        event.setAppointmentDate(appointment.getAppointmentDate());
        event.setPeriod(appointment.getPeriod());
        event.setStartTime(appointment.getStartTime());
        event.setEndTime(appointment.getEndTime());
        event.setQueueNumber(appointment.getQueueNumber());
        event.setStatus(appointment.getStatus());
        event.setCancelReason(appointment.getCancelReason());
        event.setEventType(eventType);
        event.setOccurredAt(occurredAt);
        return event;
    }

    private String serialize(AppointmentDomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize appointment domain event, appointmentId={}", event.getAppointmentId(), e);
            throw new BusinessException(ErrorCode.FAIL, "预约事件序列化失败");
        }
    }
}
