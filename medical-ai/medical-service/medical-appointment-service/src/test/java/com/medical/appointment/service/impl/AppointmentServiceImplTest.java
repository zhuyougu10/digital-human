package com.medical.appointment.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.medical.api.doctor.RemoteDoctorService;
import com.medical.api.doctor.RemoteScheduleService;
import com.medical.api.doctor.dto.DoctorInfoDTO;
import com.medical.api.doctor.dto.SlotInfoDTO;
import com.medical.api.user.RemoteUserService;
import com.medical.appointment.constant.AppointmentCacheConstants;
import com.medical.appointment.domain.dto.CreateAppointmentDTO;
import com.medical.appointment.domain.entity.Appointment;
import com.medical.appointment.mapper.AppointmentMapper;
import com.medical.appointment.service.AppointmentEventOutboxService;
import com.medical.common.core.domain.R;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import com.medical.common.redis.util.RedisUtil;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AppointmentServiceImplTest {

    private AppointmentServiceImpl appointmentService;
    private AppointmentMapper appointmentMapper;
    private RemoteDoctorService remoteDoctorService;
    private RemoteScheduleService remoteScheduleService;
    private AppointmentEventOutboxService appointmentEventOutboxService;
    private RedisUtil redisUtil;

    @BeforeEach
    void setUp() {
        ParamFlowRuleManager.loadRules(List.of());
        appointmentMapper = mock(AppointmentMapper.class);
        remoteDoctorService = mock(RemoteDoctorService.class);
        remoteScheduleService = mock(RemoteScheduleService.class);
        appointmentEventOutboxService = mock(AppointmentEventOutboxService.class);
        redisUtil = mock(RedisUtil.class);
        appointmentService = new AppointmentServiceImpl(
                appointmentMapper,
                remoteDoctorService,
                remoteScheduleService,
                mock(RemoteUserService.class),
                appointmentEventOutboxService,
                redisUtil);
    }

    @Test
    void createAppointment_shouldFastFailWhenSlotHotspotRuleTriggers() throws Throwable {
        ParamFlowRule rule = new ParamFlowRule(AppointmentServiceImpl.CREATE_RESOURCE);
        rule.setParamIdx(0);
        rule.setCount(1);
        ParamFlowRuleManager.loadRules(List.of(rule));

        SphU.entry(AppointmentServiceImpl.CREATE_RESOURCE, EntryType.IN, 1, 99L).exit();

        CreateAppointmentDTO dto = new CreateAppointmentDTO();
        dto.setPatientId(1L);
        dto.setDoctorId(2L);
        dto.setDepartmentId(3L);
        dto.setSlotId(99L);

        BusinessException ex = assertThrows(BusinessException.class, () -> appointmentService.createAppointment(dto));

        assertEquals(ErrorCode.FAIL.getCode(), ex.getCode());
        assertEquals(AppointmentServiceImpl.CREATE_BUSY_MESSAGE, ex.getMessage());
        verify(appointmentEventOutboxService, never()).saveCreatedEvent(any());
    }

    @Test
    void createAppointment_shouldWriteOutboxEventAfterInsertSucceeds() {
        CreateAppointmentDTO dto = new CreateAppointmentDTO();
        dto.setPatientId(1L);
        dto.setDoctorId(2L);
        dto.setDepartmentId(3L);
        dto.setSlotId(4L);
        dto.setSessionId(5L);

        DoctorInfoDTO doctorInfo = new DoctorInfoDTO();
        SlotInfoDTO slotInfo = new SlotInfoDTO();
        slotInfo.setId(4L);
        slotInfo.setScheduleDate(LocalDate.of(2026, 3, 27));
        slotInfo.setPeriod("morning");
        slotInfo.setStartTime(LocalTime.of(9, 0));
        slotInfo.setEndTime(LocalTime.of(9, 30));

        when(redisUtil.setIfAbsent("appointment:dedup:1:4", 1, AppointmentCacheConstants.APPOINTMENT_DEDUP_TTL_SECONDS,
                TimeUnit.SECONDS)).thenReturn(Boolean.TRUE);
        when(appointmentMapper.selectCount(any())).thenReturn(0L, 0L);
        when(remoteDoctorService.getDoctorById(2L)).thenReturn(R.ok(doctorInfo));
        when(remoteScheduleService.getAvailableSlots(eq(2L), anyString())).thenReturn(R.ok(List.of(slotInfo)));
        when(remoteScheduleService.bookSlot(4L)).thenReturn(R.ok(Boolean.TRUE));
        when(appointmentMapper.insert(any(Appointment.class))).thenAnswer(invocation -> {
            Appointment appointment = invocation.getArgument(0);
            appointment.setId(100L);
            return 1;
        });

        Long appointmentId = appointmentService.createAppointment(dto);

        assertEquals(100L, appointmentId);
        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentEventOutboxService).saveCreatedEvent(captor.capture());
        verify(redisUtil, never()).delete("appointment:dedup:1:4");
        assertEquals(100L, captor.getValue().getId());
        assertEquals(4L, captor.getValue().getSlotId());
    }

    @Test
    void createAppointment_shouldRejectDuplicateSubmitWhenDedupKeyAlreadyExists() {
        CreateAppointmentDTO dto = new CreateAppointmentDTO();
        dto.setPatientId(1L);
        dto.setDoctorId(2L);
        dto.setDepartmentId(3L);
        dto.setSlotId(4L);

        when(redisUtil.setIfAbsent("appointment:dedup:1:4", 1, AppointmentCacheConstants.APPOINTMENT_DEDUP_TTL_SECONDS,
                TimeUnit.SECONDS)).thenReturn(Boolean.FALSE);

        BusinessException ex = assertThrows(BusinessException.class, () -> appointmentService.createAppointment(dto));

        assertEquals(ErrorCode.FAIL.getCode(), ex.getCode());
        assertEquals("请勿重复提交预约请求", ex.getMessage());
        verify(appointmentMapper, never()).selectCount(any());
        verify(appointmentEventOutboxService, never()).saveCreatedEvent(any());
    }

    @Test
    void createAppointment_shouldDeleteDedupKeyWhenCreateFails() {
        CreateAppointmentDTO dto = new CreateAppointmentDTO();
        dto.setPatientId(1L);
        dto.setDoctorId(2L);
        dto.setDepartmentId(3L);
        dto.setSlotId(4L);

        DoctorInfoDTO doctorInfo = new DoctorInfoDTO();
        SlotInfoDTO slotInfo = new SlotInfoDTO();
        slotInfo.setId(4L);
        slotInfo.setScheduleDate(LocalDate.of(2026, 3, 27));

        when(redisUtil.setIfAbsent("appointment:dedup:1:4", 1, AppointmentCacheConstants.APPOINTMENT_DEDUP_TTL_SECONDS,
                TimeUnit.SECONDS)).thenReturn(Boolean.TRUE);
        when(appointmentMapper.selectCount(any())).thenReturn(0L);
        when(remoteDoctorService.getDoctorById(2L)).thenReturn(R.ok(doctorInfo));
        when(remoteScheduleService.getAvailableSlots(eq(2L), anyString())).thenReturn(R.ok(List.of(slotInfo)));
        when(remoteScheduleService.bookSlot(4L)).thenReturn(R.ok(Boolean.FALSE));

        assertThrows(BusinessException.class, () -> appointmentService.createAppointment(dto));

        verify(redisUtil).delete("appointment:dedup:1:4");
        verify(appointmentEventOutboxService, never()).saveCreatedEvent(any());
    }

    @Test
    void createAppointment_shouldDeleteDedupKeyWhenDatabaseDuplicateExistsAfterLock() {
        CreateAppointmentDTO dto = new CreateAppointmentDTO();
        dto.setPatientId(1L);
        dto.setDoctorId(2L);
        dto.setDepartmentId(3L);
        dto.setSlotId(4L);

        when(redisUtil.setIfAbsent("appointment:dedup:1:4", 1, AppointmentCacheConstants.APPOINTMENT_DEDUP_TTL_SECONDS,
                TimeUnit.SECONDS)).thenReturn(Boolean.TRUE);
        when(appointmentMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> appointmentService.createAppointment(dto));

        assertEquals(ErrorCode.APPOINTMENT_ALREADY_EXISTS.getCode(), ex.getCode());
        verify(redisUtil).delete("appointment:dedup:1:4");
        verify(remoteDoctorService, never()).getDoctorById(any());
        verify(appointmentEventOutboxService, never()).saveCreatedEvent(any());
    }

    @Test
    void createAppointment_shouldNotWriteOutboxEventWhenValidationFails() {
        CreateAppointmentDTO dto = new CreateAppointmentDTO();
        dto.setPatientId(1L);

        assertThrows(BusinessException.class, () -> appointmentService.createAppointment(dto));

        verify(appointmentEventOutboxService, never()).saveCreatedEvent(any());
    }

    @Test
    void createAppointment_shouldUseThirtySecondDedupTtl() {
        CreateAppointmentDTO dto = new CreateAppointmentDTO();
        dto.setPatientId(1L);
        dto.setDoctorId(2L);
        dto.setDepartmentId(3L);
        dto.setSlotId(4L);

        when(redisUtil.setIfAbsent("appointment:dedup:1:4", 1, AppointmentCacheConstants.APPOINTMENT_DEDUP_TTL_SECONDS,
                TimeUnit.SECONDS)).thenReturn(Boolean.FALSE);

        assertThrows(BusinessException.class, () -> appointmentService.createAppointment(dto));

        verify(redisUtil).setIfAbsent("appointment:dedup:1:4", 1,
                AppointmentCacheConstants.APPOINTMENT_DEDUP_TTL_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void cancelAppointment_shouldWriteOutboxEventAfterStatusUpdateSucceeds() {
        Appointment appointment = new Appointment();
        appointment.setId(200L);
        appointment.setPatientId(1L);
        appointment.setDoctorId(2L);
        appointment.setDepartmentId(3L);
        appointment.setSlotId(4L);
        appointment.setStatus(0);

        when(appointmentMapper.selectById(200L)).thenReturn(appointment);
        when(remoteScheduleService.cancelSlot(4L)).thenReturn(R.ok(Boolean.TRUE));

        appointmentService.cancelAppointment(200L, 1L);

        verify(appointmentMapper).updateById(appointment);
        verify(appointmentEventOutboxService).saveCancelledEvent(appointment);
    }

    @Test
    void getDoctorAppointments_shouldResolveDoctorProfileIdFromUserId() {
        Appointment appointment = new Appointment();
        appointment.setId(300L);
        appointment.setDoctorId(2L);
        appointment.setPatientId(38L);
        appointment.setAppointmentDate(LocalDate.of(2026, 4, 11));
        appointment.setStartTime(LocalTime.of(8, 0));
        appointment.setEndTime(LocalTime.of(12, 0));
        appointment.setStatus(0);

        DoctorInfoDTO currentDoctor = new DoctorInfoDTO();
        currentDoctor.setId(2L);
        currentDoctor.setUserId(15L);
        currentDoctor.setName("黄凯");

        when(remoteDoctorService.getDoctorByUserId(15L)).thenReturn(R.ok(currentDoctor));
        when(appointmentMapper.selectList(any())).thenReturn(List.of(appointment));
        when(remoteDoctorService.getDoctorById(2L)).thenReturn(R.ok(currentDoctor));

        var result = appointmentService.getDoctorAppointments(15L, LocalDate.of(2026, 4, 11));

        assertEquals(1, result.size());
        assertEquals(300L, result.get(0).getId());
        assertEquals("黄凯", result.get(0).getDoctorName());
    }

    @Test
    void getDoctorAppointments_shouldThrowWhenDoctorProfileDoesNotExist() {
        when(remoteDoctorService.getDoctorByUserId(99L)).thenReturn(R.ok(null));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> appointmentService.getDoctorAppointments(99L, LocalDate.of(2026, 4, 11)));

        assertEquals(ErrorCode.DOCTOR_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void getAppointmentDetail_shouldAllowDoctorUserWhoseProfileOwnsAppointment() {
        Appointment appointment = new Appointment();
        appointment.setId(301L);
        appointment.setDoctorId(2L);
        appointment.setPatientId(38L);
        appointment.setAppointmentDate(LocalDate.of(2026, 4, 11));
        appointment.setStartTime(LocalTime.of(8, 0));
        appointment.setEndTime(LocalTime.of(12, 0));
        appointment.setStatus(0);

        DoctorInfoDTO doctorInfo = new DoctorInfoDTO();
        doctorInfo.setId(2L);
        doctorInfo.setUserId(15L);
        doctorInfo.setName("黄凯");

        when(appointmentMapper.selectById(301L)).thenReturn(appointment);
        when(remoteDoctorService.getDoctorById(2L)).thenReturn(R.ok(doctorInfo));

        assertEquals(301L, appointmentService.getAppointmentDetail(301L, 15L).getId());
    }
}
