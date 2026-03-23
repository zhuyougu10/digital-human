package com.medical.appointment.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.medical.api.doctor.RemoteDoctorService;
import com.medical.api.doctor.RemoteScheduleService;
import com.medical.api.user.RemoteUserService;
import com.medical.appointment.domain.dto.CreateAppointmentDTO;
import com.medical.appointment.mapper.AppointmentMapper;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppointmentServiceImplTest {

    private AppointmentServiceImpl appointmentService;

    @BeforeEach
    void setUp() {
        ParamFlowRuleManager.loadRules(List.of());
        appointmentService = new AppointmentServiceImpl(
                mock(AppointmentMapper.class),
                mock(RemoteDoctorService.class),
                mock(RemoteScheduleService.class),
                mock(RemoteUserService.class));
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
    }
}
