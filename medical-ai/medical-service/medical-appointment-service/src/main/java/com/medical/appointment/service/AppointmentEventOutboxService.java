package com.medical.appointment.service;

import com.medical.appointment.domain.entity.Appointment;

public interface AppointmentEventOutboxService {

    void saveCreatedEvent(Appointment appointment);

    void saveCancelledEvent(Appointment appointment);
}
