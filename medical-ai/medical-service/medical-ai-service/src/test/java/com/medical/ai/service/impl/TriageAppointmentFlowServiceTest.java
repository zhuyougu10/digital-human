package com.medical.ai.service.impl;

import com.medical.ai.domain.entity.ChatMessage;
import com.medical.ai.domain.vo.ConversationSummaryVO;
import com.medical.api.appointment.RemoteAppointmentService;
import com.medical.api.doctor.RemoteDoctorService;
import com.medical.api.doctor.RemoteScheduleService;
import com.medical.api.doctor.dto.DoctorInfoDTO;
import com.medical.api.doctor.dto.SlotInfoDTO;
import com.medical.api.knowledge.RemoteKnowledgeService;
import com.medical.api.knowledge.dto.KnowledgeSearchRequest;
import com.medical.api.knowledge.dto.KnowledgeSearchResult;
import com.medical.common.core.domain.R;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;

@ExtendWith(MockitoExtension.class)
class TriageAppointmentFlowServiceTest {

    @Mock
    private RemoteDoctorService remoteDoctorService;

    @Mock
    private RemoteScheduleService remoteScheduleService;

    @Mock
    private RemoteAppointmentService remoteAppointmentService;

    @Mock
    private RemoteKnowledgeService remoteKnowledgeService;

    private TriageAppointmentFlowService flowService;

    @BeforeEach
    void setUp() {
        flowService = new TriageAppointmentFlowService(
                remoteDoctorService,
                remoteScheduleService,
                remoteAppointmentService,
                remoteKnowledgeService);
    }

    @Test
    void handle_shouldAskDurationBeforeAppointmentFlow() {
        TriageAppointmentFlowService.TriageFlowResult result = flowService.handle(
                10L,
                4L,
                List.of(user("我感冒咳嗽，想看医生")),
                summary("感冒", "未提及", "未提及", "未提及", "未提及"));

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
                        user("已经三天了")),
                summary("感冒", "未提及", "三天", "未提及", "未提及"));

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
                        user("没有其他症状")),
                summary("感冒", "无其他明显伴随症状", "三天", "未提及", "未提及"));

        assertTrue(result.reply().contains("严重程度"));
        assertTrue(result.suggestedReplies().contains("症状较轻"));
        verify(remoteDoctorService, never()).searchBySymptom(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void handle_shouldSearchKnowledgeAndAskCareDecisionAfterEnoughTriageInfo() {
        when(remoteKnowledgeService.search(org.mockito.ArgumentMatchers.any(KnowledgeSearchRequest.class)))
                .thenReturn(R.ok(List.of(knowledge("普通感冒常见咳嗽、鼻塞、流涕，可伴低热，通常与上呼吸道感染相关。"))));

        TriageAppointmentFlowService.TriageFlowResult result = flowService.handle(
                10L,
                4L,
                List.of(
                        user("我感冒咳嗽，想看医生"),
                        user("已经三天了"),
                        user("没有其他症状"),
                        user("症状较轻，不影响日常活动"),
                        user("没有基础病和过敏史")),
                summary("感冒", "无其他明显伴随症状", "三天", "较轻", "无特殊既往史"));

        assertTrue(result.reply().contains("知识库资料"));
        assertTrue(result.reply().contains("初步疑似诊断方向"));
        assertTrue(result.reply().contains("疑似"));
        assertTrue(result.reply().contains("需要我继续帮您预约医生就诊吗"));
        assertTrue(result.suggestedReplies().contains("需要就医"));
        verify(remoteDoctorService, never()).searchBySymptom(org.mockito.ArgumentMatchers.anyString());
        verify(remoteScheduleService, never()).getAvailableSlots(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
        verify(remoteKnowledgeService).search(org.mockito.ArgumentMatchers.any(KnowledgeSearchRequest.class));
    }

    @Test
    void handle_shouldNotRepeatCareDecisionWhenAssistantAlreadyGaveInitialDiagnosis() {
        TriageAppointmentFlowService.TriageFlowResult result = flowService.handle(
                10L,
                4L,
                List.of(
                        user("我头疼发烧，嗓子疼，还有点流鼻涕"),
                        user("今天上午刚开始"),
                        user("体温38.5度"),
                        user("没有基础病和过敏史"),
                        assistant("根据您的情况，今天上午开始发烧38.5度，伴有头疼、嗓子疼、流鼻涕，初步看这些症状比较像是上呼吸道感染，也就是我们常说的感冒或流感。您需要去医院看看吗？"),
                        user("需要就医")),
                summaryWithAssessment(
                        "发热、咽痛、流涕",
                        "头疼、嗓子疼、流鼻涕",
                        "今天上午",
                        "中等不适",
                        "没有基础病和过敏史",
                        "疑似上呼吸道感染或感冒相关问题。"));

        assertTrue(result.reply().contains("请告诉我想预约的日期和时段"));
        assertTrue(!result.reply().contains("初步疑似诊断方向"));
        assertTrue(!result.reply().contains("中耳炎"));
        verify(remoteKnowledgeService, never()).search(org.mockito.ArgumentMatchers.any(KnowledgeSearchRequest.class));
        verify(remoteDoctorService, never()).searchBySymptom(org.mockito.ArgumentMatchers.anyString());
        verify(remoteScheduleService, never()).getAvailableSlots(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void handle_shouldNotTreatSymptomDurationTodayMorningAsAppointmentTime() {
        TriageAppointmentFlowService.TriageFlowResult result = flowService.handle(
                10L,
                4L,
                List.of(
                        user("我头疼发烧，嗓子疼，还有点流鼻涕"),
                        user("今天上午刚开始"),
                        user("体温38.5度"),
                        user("没有基础病和过敏史"),
                        assistant("初步看这些症状比较像是上呼吸道感染。您需要去医院看看吗？"),
                        user("需要就医")),
                summaryWithAssessment(
                        "发热、咽痛、流涕",
                        "头疼、嗓子疼、流鼻涕",
                        "今天上午",
                        "中等不适",
                        "没有基础病和过敏史",
                        "疑似上呼吸道感染或感冒相关问题。"));

        assertTrue(result.reply().contains("请告诉我想预约的日期和时段"));
        verify(remoteDoctorService, never()).searchBySymptom(org.mockito.ArgumentMatchers.anyString());
        verify(remoteScheduleService, never()).getAvailableSlots(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void handle_shouldNotShowBookingReadinessAsInitialDiagnosis() {
        when(remoteKnowledgeService.search(org.mockito.ArgumentMatchers.any(KnowledgeSearchRequest.class)))
                .thenReturn(R.ok(List.of()));

        TriageAppointmentFlowService.TriageFlowResult result = flowService.handle(
                10L,
                4L,
                List.of(
                        user("我有点头疼，喉咙有点疼，还有点发热"),
                        user("大概两天了"),
                        user("不算太严重，还能忍受"),
                        user("没有基础病")),
                summaryWithAssessment(
                        "头疼",
                        "喉咙痛、发热",
                        "两天",
                        "较轻",
                        "无特殊既往史",
                        "病情信息基本完整，可进入预约挂号流程；如出现明显加重或急症表现，应及时线下就医。"));

        assertTrue(result.reply().contains("初步疑似诊断方向"));
        assertTrue(result.reply().contains("疑似与"));
        assertTrue(result.reply().contains("上呼吸道感染"));
        assertTrue(!result.reply().contains("病情信息基本完整"));
        assertTrue(!result.reply().contains("预约挂号流程"));
    }

    @Test
    void handle_shouldStillAskSeverityWhenFirstMessageContainsSymptomDurationAndAssociatedInfo() {
        TriageAppointmentFlowService.TriageFlowResult result = flowService.handle(
                10L,
                4L,
                List.of(user("我咳嗽发热三天了，没有其他症状，想预约2026年5月25日上午")),
                summary("咳嗽", "发热", "三天", "未提及", "未提及"));

        assertTrue(result.reply().contains("严重程度"));
        verify(remoteDoctorService, never()).searchBySymptom(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void handle_shouldAskMedicalHistoryWhenStructuredSummaryStillMissingIt() {
        TriageAppointmentFlowService.TriageFlowResult result = flowService.handle(
                10L,
                4L,
                List.of(
                        user("我感冒咳嗽，想看医生"),
                        user("已经三天了"),
                        user("没有其他症状"),
                        user("症状较轻，不影响日常活动")),
                summary("感冒", "无其他明显伴随症状", "三天", "较轻", "未提及"));

        assertTrue(result.reply().contains("既往史"));
        assertTrue(result.suggestedReplies().contains("没有基础病和过敏史"));
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
                        user("没有基础病和过敏史"),
                        assistant("初步判断：疑似与上呼吸道感染相关。您需要我继续帮您预约医生就诊吗？"),
                        user("需要就医，想预约2026年5月25日上午")),
                summary("感冒", "无其他明显伴随症状", "三天", "较轻", "无特殊既往史"));

        assertTrue(result.reply().contains("请回复序号或医生姓名选择"));
        assertTrue(result.reply().contains("李四"));
        assertTrue(result.reply().contains("内科"));
        assertTrue(result.reply().contains("副主任医师"));
        assertTrue(!result.reply().contains("doctorId"));
        assertTrue(!result.reply().contains("slotId"));
        assertTrue(result.suggestedReplies().contains("选1"));
        verify(remoteDoctorService).searchBySymptom("感冒");
    }

    @Test
    void handle_shouldNotRecommendDermatologyWhenMedicalHistoryMentionsNoAllergy() {
        DoctorInfoDTO dermatologyDoctor = doctor(3L, "皮肤科医生", "皮肤科");
        DoctorInfoDTO entDoctor = doctor(4L, "耳鼻喉医生", "耳鼻喉科");
        SlotInfoDTO entSlot = slot(464L, 4L, "耳鼻喉医生", "morning");
        when(remoteDoctorService.searchBySymptom(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(R.ok(List.of(dermatologyDoctor, entDoctor)));
        when(remoteScheduleService.getAvailableSlots(4L, "2026-05-25")).thenReturn(R.ok(List.of(entSlot)));

        TriageAppointmentFlowService.TriageFlowResult result = flowService.handle(
                10L,
                4L,
                List.of(
                        user("我头疼，喉咙也有点疼"),
                        user("已经好几天了，具体记不清了"),
                        user("挺严重的，影响睡觉了"),
                        user("没有基础病和过敏史"),
                        assistant("目前信息基本完整。您需要我继续帮您预约医生就诊吗？"),
                        user("需要就医"),
                        user("2026-05-25 上午")),
                summary("头疼、喉咙痛", "头疼、喉咙痛", "好几天", "较严重", "没有基础病和过敏史"),
                true);

        assertTrue(result.reply().contains("耳鼻喉医生"));
        assertTrue(result.reply().contains("耳鼻喉科"));
        assertTrue(!result.reply().contains("皮肤科"));
        ArgumentCaptor<String> keywordCaptor = ArgumentCaptor.forClass(String.class);
        verify(remoteDoctorService, atLeastOnce()).searchBySymptom(keywordCaptor.capture());
        assertTrue(keywordCaptor.getAllValues().stream().noneMatch(keyword -> keyword.contains("过敏")));
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
                        user("没有基础病和过敏史"),
                        assistant("初步判断：疑似与上呼吸道感染相关。您需要我继续帮您预约医生就诊吗？"),
                        user("需要就医，想预约2026年5月25日上午"),
                        assistant("1. 李四（内科，副主任医师，挂号费=0.00元）"),
                        user("选1"),
                        assistant("医生：李四\n就诊时间：2026-05-25 上午 08:00-12:00\n请回复“确认预约”"),
                        user("确认预约")),
                summary("感冒", "无其他明显伴随症状", "三天", "较轻", "无特殊既往史"));

        assertEquals(82L, result.appointmentId());
        assertTrue(result.reply().contains("预约已成功创建"));
        assertTrue(!result.reply().contains("预约编号"));
        assertTrue(!result.reply().contains("82"));
        assertTrue(!result.reply().contains("doctorId"));
        assertTrue(!result.reply().contains("slotId"));
    }

    @Test
    void handle_shouldReturnFailureReplyWhenAppointmentCreationFails() {
        DoctorInfoDTO doctor = doctor(2L, "李四");
        SlotInfoDTO slot = slot(462L, 2L, "李四", "morning");
        when(remoteDoctorService.getDoctorByName("李四")).thenReturn(R.ok(doctor));
        when(remoteDoctorService.getDoctorById(2L)).thenReturn(R.ok(doctor));
        when(remoteScheduleService.getAvailableSlots(2L, "2026-05-25")).thenReturn(R.ok(List.of(slot)));
        when(remoteAppointmentService.getAppointmentByPatientAndSlot(4L, 462L)).thenReturn(R.ok(null));
        when(remoteAppointmentService.createAppointment(4L, 2L, 462L, 10L)).thenReturn(R.fail("预约创建失败，请稍后重试"));

        TriageAppointmentFlowService.TriageFlowResult result = flowService.handle(
                10L,
                4L,
                List.of(
                        user("我感冒咳嗽"),
                        user("已经三天了"),
                        user("没有其他症状"),
                        user("症状较轻，不影响日常活动"),
                        user("没有基础病和过敏史"),
                        assistant("初步判断：疑似与上呼吸道感染相关。您需要我继续帮您预约医生就诊吗？"),
                        user("需要就医，想预约2026年5月25日上午"),
                        assistant("1. 李四（内科，副主任医师，挂号费=0.00元）"),
                        user("选1"),
                        assistant("医生：李四\n就诊时间：2026-05-25 上午 08:00-12:00\n请回复“确认预约”"),
                        user("确认预约")),
                summary("感冒", "无其他明显伴随症状", "三天", "较轻", "无特殊既往史"));

        assertNull(result.appointmentId());
        assertTrue(result.reply().contains("没有成功提交预约"));
        assertTrue(result.suggestedReplies().contains("换个时间"));
    }

    @Test
    void handle_shouldStopAppointmentFlowWhenPatientDeclinesCare() {
        TriageAppointmentFlowService.TriageFlowResult result = flowService.handle(
                10L,
                4L,
                List.of(
                        user("我感冒咳嗽"),
                        user("已经三天了"),
                        user("没有其他症状"),
                        user("症状较轻，不影响日常活动"),
                        user("没有基础病和过敏史"),
                        assistant("初步判断：疑似与上呼吸道感染相关。您需要我继续帮您预约医生就诊吗？"),
                        user("暂时先观察")),
                summary("感冒", "无其他明显伴随症状", "三天", "较轻", "无特殊既往史"));

        assertTrue(result.reply().contains("先观察病情变化"));
        assertTrue(result.suggestedReplies().contains("我想预约"));
        verify(remoteDoctorService, never()).searchBySymptom(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void handle_shouldAskAppointmentTimeWhenPatientAcceptsOnlineConsultation() {
        TriageAppointmentFlowService.TriageFlowResult result = flowService.handle(
                10L,
                4L,
                List.of(
                        user("我感冒咳嗽"),
                        user("已经三天了"),
                        user("没有其他症状"),
                        user("症状较轻，不影响日常活动"),
                        user("没有基础病和过敏史"),
                        assistant("初步判断：疑似与上呼吸道感染相关。您需要我继续帮您预约医生就诊吗？"),
                        user("好的，帮我试试线上问诊")),
                summary("感冒", "无其他明显伴随症状", "三天", "较轻", "无特殊既往史"));

        assertTrue(result.reply().contains("请告诉我想预约的日期和时段"));
        assertTrue(result.suggestedReplies().contains("明天上午"));
        verify(remoteDoctorService, never()).searchBySymptom(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void handle_shouldAskAppointmentTimeWhenPatientAcceptsRegistrationPrompt() {
        TriageAppointmentFlowService.TriageFlowResult result = flowService.handle(
                10L,
                4L,
                List.of(
                        user("我发烧咳嗽"),
                        user("一天了"),
                        user("还有头晕"),
                        user("体温38.5度，有点难受"),
                        user("没有基础病和过敏史"),
                        assistant("目前体温38.5度属于中度发热，请问您需要我帮您预约挂号吗？"),
                        user("好的，帮我预约一下")),
                summary("发热咳嗽", "咳嗽、头晕", "一天", "中度发热", "无特殊既往史"));

        assertTrue(result.reply().contains("请告诉我想预约的日期和时段"));
        assertTrue(result.suggestedReplies().contains("明天上午"));
        verify(remoteDoctorService, never()).searchBySymptom(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void handle_shouldAskForPeriodWhenPatientCorrectsDateOnly() {
        TriageAppointmentFlowService.TriageFlowResult result = flowService.handle(
                10L,
                4L,
                List.of(
                        user("我头疼发烧，嗓子疼，还有点流鼻涕"),
                        user("今天上午刚开始"),
                        user("体温38.5度"),
                        user("没有基础病和过敏史"),
                        assistant("初步看这些症状比较像是上呼吸道感染。您需要去医院看看吗？"),
                        user("需要就医"),
                        assistant("好的，我继续帮您预约。请告诉我想预约的日期和时段。"),
                        user("下周三")),
                summaryWithAssessment(
                        "发热、咽痛、流涕",
                        "头疼、嗓子疼、流鼻涕",
                        "今天上午",
                        "中等不适",
                        "没有基础病和过敏史",
                        "疑似上呼吸道感染或感冒相关问题。"),
                true);

        assertTrue(result.reply().contains("请告诉我想预约的日期和时段"));
        verify(remoteDoctorService, never()).searchBySymptom(org.mockito.ArgumentMatchers.anyString());
        verify(remoteScheduleService, never()).getAvailableSlots(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void handle_shouldUseLatestCorrectedWeekdayTimeInsteadOfEarlierTodayMorningText() {
        LocalDate nextWednesday = nextWeekday(LocalDate.now(), 3);
        DoctorInfoDTO doctor = doctor(2L, "李四");
        SlotInfoDTO slot = slot(462L, 2L, "李四", "afternoon", nextWednesday);
        when(remoteDoctorService.searchBySymptom(org.mockito.ArgumentMatchers.anyString())).thenReturn(R.ok(List.of(doctor)));
        when(remoteScheduleService.getAvailableSlots(2L, nextWednesday.toString())).thenReturn(R.ok(List.of(slot)));

        TriageAppointmentFlowService.TriageFlowResult result = flowService.handle(
                10L,
                4L,
                List.of(
                        user("我头疼发烧，嗓子疼，还有点流鼻涕"),
                        user("今天上午刚开始"),
                        user("体温38.5度"),
                        user("没有基础病和过敏史"),
                        assistant("初步看这些症状比较像是上呼吸道感染。您需要去医院看看吗？"),
                        user("需要就医"),
                        assistant("好的，我继续帮您预约。请告诉我想预约的日期和时段。"),
                        user("下周三下午")),
                summaryWithAssessment(
                        "发热、咽痛、流涕",
                        "头疼、嗓子疼、流鼻涕",
                        "今天上午",
                        "中等不适",
                        "没有基础病和过敏史",
                        "疑似上呼吸道感染或感冒相关问题。"),
                true);

        assertTrue(result.reply().contains(nextWednesday + " 下午"));
        assertTrue(!result.reply().contains(LocalDate.now() + " 上午"));
        verify(remoteScheduleService).getAvailableSlots(2L, nextWednesday.toString());
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

    private ConversationSummaryVO summary(String chiefComplaint,
                                          String symptoms,
                                          String duration,
                                          String severity,
                                          String medicalHistory) {
        return summaryWithAssessment(
                chiefComplaint,
                symptoms,
                duration,
                severity,
                medicalHistory,
                "已记录主诉，继续补充结构化病情信息。");
    }

    private ConversationSummaryVO summaryWithAssessment(String chiefComplaint,
                                                        String symptoms,
                                                        String duration,
                                                        String severity,
                                                        String medicalHistory,
                                                        String aiAssessment) {
        ConversationSummaryVO summary = new ConversationSummaryVO();
        summary.setChiefComplaint(chiefComplaint);
        summary.setSymptoms(symptoms);
        summary.setDuration(duration);
        summary.setSeverity(severity);
        summary.setMedicalHistory(medicalHistory);
        summary.setAiAssessment(aiAssessment);
        return summary;
    }

    private KnowledgeSearchResult knowledge(String content) {
        KnowledgeSearchResult result = new KnowledgeSearchResult();
        result.setContent(content);
        result.setScore(0.88);
        result.setDocumentName("common-cold.md");
        result.setChunkIndex(1);
        return result;
    }

    private DoctorInfoDTO doctor(Long id, String name) {
        return doctor(id, name, "内科");
    }

    private DoctorInfoDTO doctor(Long id, String name, String departmentNames) {
        DoctorInfoDTO doctor = new DoctorInfoDTO();
        doctor.setId(id);
        doctor.setName(name);
        doctor.setTitle("副主任医师");
        doctor.setDepartmentNames(departmentNames);
        doctor.setSpecialties("感冒、咳嗽");
        doctor.setConsultationFee(BigDecimal.ZERO);
        return doctor;
    }

    private SlotInfoDTO slot(Long id, Long doctorId, String doctorName, String period) {
        return slot(id, doctorId, doctorName, period, LocalDate.of(2026, 5, 25));
    }

    private SlotInfoDTO slot(Long id, Long doctorId, String doctorName, String period, LocalDate date) {
        SlotInfoDTO slot = new SlotInfoDTO();
        slot.setId(id);
        slot.setDoctorId(doctorId);
        slot.setDoctorName(doctorName);
        slot.setScheduleDate(date);
        slot.setPeriod(period);
        slot.setStartTime("morning".equals(period) ? LocalTime.of(8, 0) : LocalTime.of(14, 0));
        slot.setEndTime("morning".equals(period) ? LocalTime.of(12, 0) : LocalTime.of(18, 0));
        slot.setTotalSlots(20);
        slot.setBookedSlots(0);
        slot.setAvailableSlots(20);
        return slot;
    }

    private LocalDate nextWeekday(LocalDate today, int targetDayOfWeek) {
        int daysToAdd = targetDayOfWeek - today.getDayOfWeek().getValue();
        daysToAdd += 7;
        return today.plusDays(daysToAdd);
    }
}
