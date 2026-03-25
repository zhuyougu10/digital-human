package com.medical.appointment.job;

import com.medical.appointment.service.AppointmentEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "medical.mq", name = "enabled", havingValue = "true")
public class AppointmentOutboxPublishJob {

    private final AppointmentEventPublisher appointmentEventPublisher;

    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        appointmentEventPublisher.publishPendingEvents();
    }
}
