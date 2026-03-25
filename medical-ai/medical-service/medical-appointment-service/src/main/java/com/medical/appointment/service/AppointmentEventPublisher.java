package com.medical.appointment.service;

public interface AppointmentEventPublisher {

    String EVENT_ID_HEADER = "appointmentEventId";

    void publishPendingEvents();
}
