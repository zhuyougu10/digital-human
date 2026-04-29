package com.medical.ai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.ai.agent.AgentFactory;
import com.medical.ai.domain.entity.ConversationSummary;
import com.medical.ai.domain.vo.ConversationSummaryVO;
import com.medical.ai.mapper.ChatMessageMapper;
import com.medical.ai.mapper.ChatSessionMapper;
import com.medical.ai.mapper.ConversationSummaryMapper;
import com.medical.api.appointment.RemoteAppointmentService;
import com.medical.api.appointment.dto.AppointmentDTO;
import com.medical.api.doctor.RemoteDoctorService;
import com.medical.api.doctor.dto.DoctorInfoDTO;
import com.medical.common.core.constant.UserConstants;
import com.medical.common.core.domain.R;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.openai.OpenAiChatModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SummaryServiceImplTest {

    @Mock
    private ChatSessionMapper sessionMapper;
    @Mock
    private ChatMessageMapper messageMapper;
    @Mock
    private ConversationSummaryMapper summaryMapper;
    @Mock
    private AgentFactory agentFactory;
    @Mock
    private OpenAiChatModel chatModel;
    @Mock
    private RemoteAppointmentService remoteAppointmentService;
    @Mock
    private RemoteDoctorService remoteDoctorService;

    private SummaryServiceImpl summaryService;

    @BeforeEach
    void setUp() {
        summaryService = new SummaryServiceImpl(
                sessionMapper,
                messageMapper,
                summaryMapper,
                agentFactory,
                chatModel,
                new ObjectMapper(),
                remoteAppointmentService,
                remoteDoctorService
        );
    }

    @Test
    void getSummaryByAppointment_allowsPatientOwner() {
        AppointmentDTO appointment = new AppointmentDTO();
        appointment.setId(10L);
        appointment.setPatientId(1L);
        appointment.setDoctorId(20L);

        ConversationSummary summary = new ConversationSummary();
        summary.setId(99L);
        summary.setAppointmentId(10L);

        when(remoteAppointmentService.getAppointmentSnapshot(10L)).thenReturn(R.ok(appointment));
        when(summaryMapper.selectOne(any())).thenReturn(summary);

        ConversationSummaryVO result = summaryService.getSummaryByAppointment(10L, 1L, List.of(UserConstants.ROLE_PATIENT));

        assertNotNull(result);
        assertEquals(99L, result.getId());
        verify(remoteDoctorService, never()).getDoctorByUserId(any());
    }

    @Test
    void getSummaryByAppointment_allowsRelatedDoctor() {
        AppointmentDTO appointment = new AppointmentDTO();
        appointment.setId(10L);
        appointment.setPatientId(1L);
        appointment.setDoctorId(20L);

        DoctorInfoDTO doctor = new DoctorInfoDTO();
        doctor.setId(20L);
        doctor.setUserId(2L);

        ConversationSummary summary = new ConversationSummary();
        summary.setId(101L);
        summary.setAppointmentId(10L);

        when(remoteAppointmentService.getAppointmentSnapshot(10L)).thenReturn(R.ok(appointment));
        when(remoteDoctorService.getDoctorByUserId(2L)).thenReturn(R.ok(doctor));
        when(summaryMapper.selectOne(any())).thenReturn(summary);

        ConversationSummaryVO result = summaryService.getSummaryByAppointment(10L, 2L, List.of(UserConstants.ROLE_DOCTOR));

        assertNotNull(result);
        assertEquals(101L, result.getId());
    }

    @Test
    void getSummaryByAppointment_allowsAdmin() {
        AppointmentDTO appointment = new AppointmentDTO();
        appointment.setId(10L);
        appointment.setPatientId(1L);
        appointment.setDoctorId(20L);

        when(remoteAppointmentService.getAppointmentSnapshot(10L)).thenReturn(R.ok(appointment));
        when(summaryMapper.selectOne(any())).thenReturn(null);

        ConversationSummaryVO result = summaryService.getSummaryByAppointment(10L, 88L, List.of(UserConstants.ROLE_ADMIN));

        assertNull(result);
        verify(remoteDoctorService, never()).getDoctorByUserId(any());
    }

    @Test
    void getSummaryByAppointment_deniesUnrelatedUser() {
        AppointmentDTO appointment = new AppointmentDTO();
        appointment.setId(10L);
        appointment.setPatientId(1L);
        appointment.setDoctorId(20L);

        when(remoteAppointmentService.getAppointmentSnapshot(10L)).thenReturn(R.ok(appointment));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> summaryService.getSummaryByAppointment(10L, 99L, List.of(UserConstants.ROLE_PATIENT)));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
        verify(summaryMapper, never()).selectOne(any());
    }

    @Test
    void getSummaryByAppointment_deniesUnrelatedDoctor() {
        AppointmentDTO appointment = new AppointmentDTO();
        appointment.setId(10L);
        appointment.setPatientId(1L);
        appointment.setDoctorId(20L);

        DoctorInfoDTO doctor = new DoctorInfoDTO();
        doctor.setId(99L);
        doctor.setUserId(2L);

        when(remoteAppointmentService.getAppointmentSnapshot(10L)).thenReturn(R.ok(appointment));
        when(remoteDoctorService.getDoctorByUserId(2L)).thenReturn(R.ok(doctor));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> summaryService.getSummaryByAppointment(10L, 2L, List.of(UserConstants.ROLE_DOCTOR)));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
        verify(summaryMapper, never()).selectOne(any());
    }

    @Test
    void getSummaryByAppointment_notFoundWhenAppointmentMissing() {
        when(remoteAppointmentService.getAppointmentSnapshot(10L))
                .thenReturn(R.fail(ErrorCode.APPOINTMENT_NOT_FOUND.getCode(), ErrorCode.APPOINTMENT_NOT_FOUND.getMsg()));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> summaryService.getSummaryByAppointment(10L, 1L, List.of(UserConstants.ROLE_ADMIN)));

        assertEquals(ErrorCode.APPOINTMENT_NOT_FOUND.getCode(), exception.getCode());
    }
}
