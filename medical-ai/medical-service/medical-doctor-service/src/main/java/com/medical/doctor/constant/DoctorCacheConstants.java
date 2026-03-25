package com.medical.doctor.constant;

public final class DoctorCacheConstants {

    public static final String DEPARTMENT_LIST_KEY = "department:list:all";
    public static final long DEPARTMENT_LIST_TTL_MINUTES = 30L;

    public static final String DOCTOR_DETAIL_KEY_PREFIX = "doctor:detail:";
    public static final long DOCTOR_DETAIL_TTL_MINUTES = 15L;

    public static final String DOCTOR_LIST_KEY_PREFIX = "doctor:list:";
    public static final String DOCTOR_LIST_VERSION_KEY = "doctor:list:version";
    public static final long DOCTOR_LIST_TTL_MINUTES = 15L;
    public static final long CACHE_TTL_JITTER_SECONDS = 120L;

    public static final String SCHEDULE_SLOTS_KEY_PREFIX = "schedule:slots:";
    public static final long SCHEDULE_SLOTS_TTL_SECONDS = 60L;
    public static final int SCHEDULE_TEMPLATE_INVALIDATE_DAYS = 30;

    private DoctorCacheConstants() {
    }
}
