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
import com.medical.appointment.domain.dto.CreateAppointmentDTO;
import com.medical.appointment.domain.entity.Appointment;
import com.medical.appointment.mapper.AppointmentMapper;
import com.medical.appointment.service.AppointmentEventOutboxService;
import com.medical.common.core.domain.R;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AppointmentServiceImplTest {

    private AppointmentServiceImpl appointmentService;
    private AppointmentMapper appointmentMapper;
    private RemoteDoctorService remoteDoctorService;
    private RemoteScheduleService remoteScheduleService;
    private AppointmentEventOutboxService appointmentEventOutboxService;

    @BeforeEach
    void setUp() {
        ParamFlowRuleManager.loadRules(List.of());
        appointmentMapper = mock(AppointmentMapper.class);
        remoteDoctorService = mock(RemoteDoctorService.class);
        remoteScheduleService = mock(RemoteScheduleService.class);
        appointmentEventOutboxService = mock(AppointmentEventOutboxService.class);
        appointmentService = new AppointmentServiceImpl(
                appointmentMapper,
                remoteDoctorService,
                remoteScheduleService,
                mock(RemoteUserService.class),
                appointmentEventOutboxService);
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
        assertEquals(100L, captor.getValue().getId());
        assertEquals(4L, captor.getValue().getSlotId());
    }

    @Test
    void createAppointment_shouldNotWriteOutboxEventWhenValidationFails() {
        CreateAppointmentDTO dto = new CreateAppointmentDTO();
        dto.setPatientId(1L);

        assertThrows(BusinessException.class, () -> appointmentService.createAppointment(dto));

        verify(appointmentEventOutboxService, never()).saveCreatedEvent(any());
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
}
