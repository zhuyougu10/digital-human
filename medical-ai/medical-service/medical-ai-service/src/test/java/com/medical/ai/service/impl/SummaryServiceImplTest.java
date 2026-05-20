package com.medical.ai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.ai.agent.AgentFactory;
import com.medical.ai.domain.entity.ChatMessage;
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
import org.mockito.ArgumentCaptor;
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
    void syncTriageSummary_shouldInsertStructuredFieldsAndAlwaysSetAiAssessment() {
        when(summaryMapper.selectOne(any())).thenReturn(null);

        ConversationSummaryVO result = summaryService.syncTriageSummary(
                10L,
                4L,
                null,
                List.of(
                        user("我感冒咳嗽"),
                        user("已经三天了"),
                        user("没有其他症状"),
                        user("症状较轻，不影响日常活动"),
                        user("没有基础病和过敏史")));

        assertNotNull(result);
        assertEquals("感冒", result.getChiefComplaint());
        assertEquals("无其他明显伴随症状", result.getSymptoms());
        assertEquals("三天", result.getDuration());
        assertEquals("较轻", result.getSeverity());
        assertEquals("无特殊既往史", result.getMedicalHistory());
        assertNotNull(result.getAiAssessment());

        ArgumentCaptor<ConversationSummary> captor = ArgumentCaptor.forClass(ConversationSummary.class);
        verify(summaryMapper).insert(captor.capture());
        assertEquals("感冒", captor.getValue().getChiefComplaint());
        assertEquals("无特殊既往史", captor.getValue().getMedicalHistory());
        assertNotNull(captor.getValue().getAiAssessment());
    }

    @Test
    void syncTriageSummary_shouldUpdateMissingFieldsAndBindAppointment() {
        ConversationSummary existing = new ConversationSummary();
        existing.setId(99L);
        existing.setSessionId(10L);
        existing.setUserId(4L);
        existing.setChiefComplaint("感冒");
        existing.setSymptoms("未提及");
        existing.setDuration("未提及");
        existing.setSeverity("未提及");
        existing.setMedicalHistory("未提及");
        existing.setAiAssessment("-");
        when(summaryMapper.selectOne(any())).thenReturn(existing);

        ConversationSummaryVO result = summaryService.syncTriageSummary(
                10L,
                4L,
                82L,
                List.of(
                        user("我感冒咳嗽"),
                        user("已经三天了"),
                        user("没有其他症状"),
                        user("症状较轻，不影响日常活动"),
                        user("没有基础病和过敏史")));

        assertEquals(82L, result.getAppointmentId());
        assertEquals("三天", result.getDuration());
        assertEquals("较轻", result.getSeverity());
        assertNotNull(result.getAiAssessment());
        verify(summaryMapper).updateById(existing);
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

    private ChatMessage user(String content) {
        ChatMessage message = new ChatMessage();
        message.setRole("user");
        message.setContent(content);
        return message;
    }
}
