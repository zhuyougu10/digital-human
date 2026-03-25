package com.medical.appointment.constant;

public final class AppointmentCacheConstants {

    public static final String APPOINTMENT_DEDUP_KEY_PREFIX = "appointment:dedup:";
    public static final long APPOINTMENT_DEDUP_TTL_SECONDS = 30L;

    private AppointmentCacheConstants() {
    }
}
