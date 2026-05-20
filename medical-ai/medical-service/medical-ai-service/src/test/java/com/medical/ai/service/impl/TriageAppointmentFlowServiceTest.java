package com.medical.ai.service.impl;

import com.medical.ai.domain.entity.ChatMessage;
import com.medical.api.appointment.RemoteAppointmentService;
import com.medical.api.doctor.RemoteDoctorService;
import com.medical.api.doctor.RemoteScheduleService;
import com.medical.api.doctor.dto.DoctorInfoDTO;
import com.medical.api.doctor.dto.SlotInfoDTO;
import com.medical.common.core.domain.R;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TriageAppointmentFlowServiceTest {

    @Mock
    private RemoteDoctorService remoteDoctorService;

    @Mock
    private RemoteScheduleService remoteScheduleService;

    @Mock
    private RemoteAppointmentService remoteAppointmentService;

    private TriageAppointmentFlowService flowService;

    @BeforeEach
    void setUp() {
        flowService = new TriageAppointmentFlowService(
                remoteDoctorService,
                remoteScheduleService,
                remoteAppointmentService);
    }

    @Test
    void handle_shouldAskDurationBeforeAppointmentFlow() {
        TriageAppointmentFlowService.TriageFlowResult result = flowService.handle(
                10L,
                4L,
                List.of(user("我感冒咳嗽，想看医生")));

        assertTrue(result.reply().contains("持续多久"));
        assertTrue(result.suggestedReplies().contains("已经三天了"));
        verify(remoteDoctorService, never()).searchBySymptom(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void handle_shouldAskAssociatedInfoBeforeAppointmentFlow() {
        TriageAppointmentFlowService.TriageFlowResult result = flowService.handle(
                10L,
                4L,
                List.of(
                        user("我感冒咳嗽，想看医生"),
                        user("已经三天了")));

        assertTrue(result.reply().contains("伴随情况"));
        assertTrue(result.suggestedReplies().contains("没有其他症状"));
        verify(remoteDoctorService, never()).searchBySymptom(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void handle_shouldAskSeverityBeforeAppointmentFlow() {
        TriageAppointmentFlowService.TriageFlowResult result = flowService.handle(
                10L,
                4L,
                List.of(
                        user("我感冒咳嗽，想看医生"),
                        user("已经三天了"),
                        user("没有其他症状")));

        assertTrue(result.reply().contains("严重程度"));
        assertTrue(result.suggestedReplies().contains("症状较轻"));
        verify(remoteDoctorService, never()).searchBySymptom(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void handle_shouldAskAppointmentTimeAfterEnoughTriageInfo() {
        TriageAppointmentFlowService.TriageFlowResult result = flowService.handle(
                10L,
                4L,
                List.of(
                        user("我感冒咳嗽，想看医生"),
                        user("已经三天了"),
                        user("没有其他症状"),
                        user("症状较轻，不影响日常活动")));

        assertTrue(result.reply().contains("病情信息已经基本够用了"));
        assertTrue(result.suggestedReplies().contains("2026年5月25日上午"));
        verify(remoteDoctorService, never()).searchBySymptom(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void handle_shouldStillAskSeverityWhenFirstMessageContainsSymptomDurationAndAssociatedInfo() {
        TriageAppointmentFlowService.TriageFlowResult result = flowService.handle(
                10L,
                4L,
                List.of(user("我咳嗽发热三天了，没有其他症状，想预约2026年5月25日上午")));

        assertTrue(result.reply().contains("严重程度"));
        verify(remoteDoctorService, never()).searchBySymptom(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void handle_shouldFindDoctorsAfterTimeIsProvided() {
        DoctorInfoDTO doctor = doctor(2L, "李四");
        SlotInfoDTO slot = slot(462L, 2L, "李四", "morning");
        when(remoteDoctorService.searchBySymptom(org.mockito.ArgumentMatchers.anyString())).thenReturn(R.ok(List.of(doctor)));
        when(remoteScheduleService.getAvailableSlots(2L, "2026-05-25")).thenReturn(R.ok(List.of(slot)));

        TriageAppointmentFlowService.TriageFlowResult result = flowService.handle(
                10L,
                4L,
                List.of(
                        user("我感冒咳嗽"),
                        user("已经三天了"),
                        user("没有其他症状"),
                        user("症状较轻，不影响日常活动"),
                        user("想预约2026年5月25日上午")));

        assertTrue(result.reply().contains("请回复序号或医生姓名选择"));
        assertTrue(result.reply().contains("李四"));
        assertTrue(result.reply().contains("内科"));
        assertTrue(result.reply().contains("副主任医师"));
        assertTrue(!result.reply().contains("doctorId"));
        assertTrue(!result.reply().contains("slotId"));
        assertTrue(result.suggestedReplies().contains("选1"));
        verify(remoteDoctorService).searchBySymptom("咳嗽");
    }

    @Test
    void handle_shouldCreateAppointmentAfterDoctorSelectionAndConfirmation() {
        DoctorInfoDTO doctor = doctor(2L, "李四");
        SlotInfoDTO slot = slot(462L, 2L, "李四", "morning");
        when(remoteDoctorService.getDoctorByName("李四")).thenReturn(R.ok(doctor));
        when(remoteDoctorService.getDoctorById(2L)).thenReturn(R.ok(doctor));
        when(remoteScheduleService.getAvailableSlots(2L, "2026-05-25")).thenReturn(R.ok(List.of(slot)));
        when(remoteAppointmentService.getAppointmentByPatientAndSlot(4L, 462L)).thenReturn(R.ok(null));
        when(remoteAppointmentService.createAppointment(4L, 2L, 462L, 10L)).thenReturn(R.ok(82L));

        TriageAppointmentFlowService.TriageFlowResult result = flowService.handle(
                10L,
                4L,
                List.of(
                        user("我感冒咳嗽"),
                        user("已经三天了"),
                        user("没有其他症状"),
                        user("症状较轻，不影响日常活动"),
                        user("想预约2026年5月25日上午"),
                        assistant("1. 李四（内科，副主任医师，挂号费=0.00元）"),
                        user("选1"),
                        assistant("医生：李四\n就诊时间：2026-05-25 上午 08:00-12:00\n请回复“确认预约”"),
                        user("确认预约")));

        assertEquals(82L, result.appointmentId());
        assertTrue(result.reply().contains("预约已成功创建"));
        assertTrue(result.reply().contains("预约编号：82"));
        assertTrue(!result.reply().contains("doctorId"));
        assertTrue(!result.reply().contains("slotId"));
    }

    private ChatMessage user(String content) {
        ChatMessage message = new ChatMessage();
        message.setRole("user");
        message.setContent(content);
        return message;
    }

    private ChatMessage assistant(String content) {
        ChatMessage message = new ChatMessage();
        message.setRole("assistant");
        message.setContent(content);
        return message;
    }

    private DoctorInfoDTO doctor(Long id, String name) {
        DoctorInfoDTO doctor = new DoctorInfoDTO();
        doctor.setId(id);
        doctor.setName(name);
        doctor.setTitle("副主任医师");
        doctor.setDepartmentNames("内科");
        doctor.setSpecialties("感冒、咳嗽");
        doctor.setConsultationFee(BigDecimal.ZERO);
        return doctor;
    }

    private SlotInfoDTO slot(Long id, Long doctorId, String doctorName, String period) {
        SlotInfoDTO slot = new SlotInfoDTO();
        slot.setId(id);
        slot.setDoctorId(doctorId);
        slot.setDoctorName(doctorName);
        slot.setScheduleDate(LocalDate.of(2026, 5, 25));
        slot.setPeriod(period);
        slot.setStartTime("morning".equals(period) ? LocalTime.of(8, 0) : LocalTime.of(14, 0));
        slot.setEndTime("morning".equals(period) ? LocalTime.of(12, 0) : LocalTime.of(18, 0));
        slot.setTotalSlots(20);
        slot.setBookedSlots(0);
        slot.setAvailableSlots(20);
        return slot;
    }
}
