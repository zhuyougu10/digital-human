package com.medical.ai.service.impl;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.ai.agent.Agent;
import com.medical.ai.agent.AgentFactory;
import com.medical.ai.domain.entity.ChatMessage;
import com.medical.ai.domain.entity.ChatSession;
import com.medical.ai.domain.vo.ChatMessageVO;
import com.medical.ai.domain.vo.ConversationSummaryVO;
import com.medical.ai.domain.vo.SseMessageVO;
import com.medical.ai.mapper.ChatMessageMapper;
import com.medical.ai.mapper.ChatSessionMapper;
import com.medical.ai.service.SummaryService;
import com.medical.ai.service.TtsService;
import com.medical.api.appointment.RemoteAppointmentService;
import com.medical.api.appointment.dto.AppointmentDTO;
import com.medical.api.doctor.RemoteScheduleService;
import com.medical.api.doctor.dto.SlotInfoDTO;
import com.medical.common.core.domain.R;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock
    private ChatSessionMapper sessionMapper;

    @Mock
    private ChatMessageMapper messageMapper;

    @Mock
    private AgentFactory agentFactory;

    @Mock
    private OpenAiChatModel chatModel;

    @Mock
    private TtsService ttsService;

    @Mock
    private SummaryService summaryService;

    @Mock
    private RemoteAppointmentService remoteAppointmentService;

    @Mock
    private RemoteScheduleService remoteScheduleService;

    @Mock
    private TriageAppointmentFlowService triageAppointmentFlowService;

    private ChatServiceImpl chatService;

    private static final String APPOINTMENT_SUCCESS_GUARD_TEXT = "只有在 createAppointment 工具明确返回 success=true 且 appointmentId 非空时，才允许回复“预约成功”";

    @BeforeEach
    void setUp() {
        FlowRuleManager.loadRules(Collections.emptyList());
        chatService = new ChatServiceImpl(
            sessionMapper,
            messageMapper,
            agentFactory,
            chatModel,
            ttsService,
            summaryService,
            remoteAppointmentService,
            remoteScheduleService,
            triageAppointmentFlowService,
            new ObjectMapper()
        );
    }

    @Test
    void chat_shouldLetTriageAgentUseModelWithSynchronizedSummary() {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setUserId(38L);
        session.setAgentType("TRIAGE");
        session.setTitle("鏂板璇?");
        when(sessionMapper.selectById(1L)).thenReturn(session);

        ChatMessage userMessage = new ChatMessage();
        userMessage.setSessionId(1L);
        userMessage.setRole("user");
        userMessage.setContent("2026年5月25日上午");
        when(messageMapper.selectList(any())).thenReturn(List.of(userMessage));
        ConversationSummaryVO summary = new ConversationSummaryVO();
        summary.setChiefComplaint("感冒");
        summary.setSymptoms("未提及");
        summary.setDuration("未提及");
        summary.setSeverity("未提及");
        summary.setMedicalHistory("未提及");
        summary.setAiAssessment("已记录主诉，仍需补充信息。");
        when(summaryService.syncTriageSummary(eq(1L), eq(38L), eq(null), any())).thenReturn(summary);
        when(summaryService.getSummaryBySession(1L, 38L)).thenReturn(summary);
        Agent agent = mock(Agent.class);
        when(agentFactory.getAgent("TRIAGE")).thenReturn(agent);
        when(agent.getToolNames()).thenReturn(List.of("searchDoctorBySymptom", "getAvailableSlots", "createAppointment"));
        when(agent.getSystemPrompt()).thenReturn("triage-system");
        when(agent.getAgentType()).thenReturn("TRIAGE");
        when(chatModel.stream(argThat((Prompt prompt) -> {
            if (prompt == null || prompt.getInstructions() == null || prompt.getInstructions().isEmpty()) {
                return false;
            }
            String systemText = prompt.getInstructions().get(0).getText();
            return systemText.contains("当前结构化导诊摘要")
                    && systemText.contains("主诉：感冒")
                    && systemText.contains("AI判断：已记录主诉，仍需补充信息。");
        }))).thenReturn(Flux.just(mockChatResponse("感冒已经记录了，我再确认一下：这些症状持续多久了？")));
        lenient().when(ttsService.synthesize(any())).thenAnswer(invocation -> {
            Thread.sleep(500L);
            return "/ai/chat/tts/triage-flow.mp3";
        });

        List<SseMessageVO> events = chatService.chat(1L, 38L, "2026年5月25日上午")
                .takeUntil(event -> "complete".equals(event.getType()))
                .collectList()
                .block(Duration.ofSeconds(2));

        assertNotNull(events);
        SseMessageVO complete = findFirstEventByType(events, "complete");
        assertNotNull(complete);
        assertEquals("感冒已经记录了，我再确认一下：这些症状持续多久了？", complete.getContent());
        verify(chatModel).stream(any(Prompt.class));
        verify(triageAppointmentFlowService, never()).handle(any(), any(), any(), any());
        verify(summaryService).syncTriageSummary(eq(1L), eq(38L), eq(null), any());
    }

    @Test
    void chat_shouldUseDeterministicTriageFlowAfterSummaryReadyAndTimeProvided() {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setUserId(38L);
        session.setAgentType("TRIAGE");
        session.setTitle("新对话");
        when(sessionMapper.selectById(1L)).thenReturn(session);

        ChatMessage userMessage = new ChatMessage();
        userMessage.setSessionId(1L);
        userMessage.setRole("user");
        userMessage.setContent("我想预约2026年5月25日上午");
        when(messageMapper.selectList(any())).thenReturn(List.of(userMessage));

        ConversationSummaryVO summary = new ConversationSummaryVO();
        summary.setChiefComplaint("感冒");
        summary.setSymptoms("咳嗽");
        summary.setDuration("三天");
        summary.setSeverity("较轻");
        summary.setMedicalHistory("无特殊既往史");
        summary.setAiAssessment("病情信息基本完整，可进入挂号流程。");
        when(summaryService.syncTriageSummary(eq(1L), eq(38L), eq(null), any())).thenReturn(summary);
        when(triageAppointmentFlowService.handle(eq(1L), eq(38L), any(), eq(summary)))
                .thenReturn(new TriageAppointmentFlowService.TriageFlowResult("已找到可预约医生，请回复序号选择。", null, List.of("选1")));
        lenient().when(ttsService.synthesize(any())).thenReturn("/ai/chat/tts/triage-flow.mp3");

        List<SseMessageVO> events = chatService.chat(1L, 38L, "我想预约2026年5月25日上午")
                .takeUntil(event -> "complete".equals(event.getType()))
                .collectList()
                .block(Duration.ofSeconds(2));

        assertNotNull(events);
        SseMessageVO complete = findFirstEventByType(events, "complete");
        assertNotNull(complete);
        assertEquals("已找到可预约医生，请回复序号选择。", complete.getContent());
        assertIterableEquals(List.of("选1"), (List<String>) complete.getMetadata().get("suggestedReplies"));
        verify(chatModel, never()).stream(any(Prompt.class));
        verify(triageAppointmentFlowService).handle(eq(1L), eq(38L), any(), eq(summary));
    }

    @Test
    void chat_shouldEnterStateMachineImmediatelyAfterPatientAcceptsCareDecision() {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setUserId(38L);
        session.setAgentType("TRIAGE");
        session.setTitle("新对话");
        when(sessionMapper.selectById(1L)).thenReturn(session);

        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setSessionId(1L);
        assistantMessage.setRole("assistant");
        assistantMessage.setContent("初步判断：疑似与上呼吸道感染相关。您需要我继续帮您预约医生就诊吗？");
        ChatMessage userMessage = new ChatMessage();
        userMessage.setSessionId(1L);
        userMessage.setRole("user");
        userMessage.setContent("好的，帮我试试线上问诊");
        when(messageMapper.selectList(any())).thenReturn(List.of(assistantMessage, userMessage));

        ConversationSummaryVO summary = new ConversationSummaryVO();
        summary.setChiefComplaint("感冒");
        summary.setSymptoms("咳嗽");
        summary.setDuration("三天");
        summary.setSeverity("较轻");
        summary.setMedicalHistory("未提及");
        summary.setAiAssessment("疑似上呼吸道感染。");
        when(summaryService.syncTriageSummary(eq(1L), eq(38L), eq(null), any())).thenReturn(summary);
        when(triageAppointmentFlowService.handle(eq(1L), eq(38L), any(), eq(summary)))
                .thenReturn(new TriageAppointmentFlowService.TriageFlowResult("好的，我继续帮您预约。请告诉我想预约的日期和时段。", null, List.of("明天上午")));
        lenient().when(ttsService.synthesize(any())).thenReturn("/ai/chat/tts/triage-flow.mp3");

        List<SseMessageVO> events = chatService.chat(1L, 38L, "好的，帮我试试线上问诊")
                .takeUntil(event -> "complete".equals(event.getType()))
                .collectList()
                .block(Duration.ofSeconds(2));

        assertNotNull(events);
        SseMessageVO complete = findFirstEventByType(events, "complete");
        assertNotNull(complete);
        assertTrue(complete.getContent().contains("请告诉我想预约的日期和时段"));
        verify(chatModel, never()).stream(any(Prompt.class));
        verify(triageAppointmentFlowService).handle(eq(1L), eq(38L), any(), eq(summary));
    }

    @Test
    void chat_shouldEnterStateMachineAfterPatientAcceptsAppointmentRegistrationPrompt() {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setUserId(38L);
        session.setAgentType("TRIAGE");
        session.setTitle("新对话");
        when(sessionMapper.selectById(1L)).thenReturn(session);

        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setSessionId(1L);
        assistantMessage.setRole("assistant");
        assistantMessage.setContent("目前体温38.5度属于中度发热，请问您需要我帮您预约挂号吗？");
        ChatMessage userMessage = new ChatMessage();
        userMessage.setSessionId(1L);
        userMessage.setRole("user");
        userMessage.setContent("好的，帮我预约一下");
        when(messageMapper.selectList(any())).thenReturn(List.of(assistantMessage, userMessage));

        ConversationSummaryVO summary = new ConversationSummaryVO();
        summary.setChiefComplaint("发热咳嗽");
        summary.setSymptoms("咳嗽、头晕");
        summary.setDuration("一天");
        summary.setSeverity("中度发热");
        summary.setMedicalHistory("无特殊既往史");
        summary.setAiAssessment("疑似呼吸道感染相关问题。");
        when(summaryService.syncTriageSummary(eq(1L), eq(38L), eq(null), any())).thenReturn(summary);
        when(triageAppointmentFlowService.handle(eq(1L), eq(38L), any(), eq(summary)))
                .thenReturn(new TriageAppointmentFlowService.TriageFlowResult("好的，我继续帮您预约。请告诉我想预约的日期和时段。", null, List.of("明天上午")));
        lenient().when(ttsService.synthesize(any())).thenReturn("/ai/chat/tts/triage-flow.mp3");

        List<SseMessageVO> events = chatService.chat(1L, 38L, "好的，帮我预约一下")
                .takeUntil(event -> "complete".equals(event.getType()))
                .collectList()
                .block(Duration.ofSeconds(2));

        assertNotNull(events);
        SseMessageVO complete = findFirstEventByType(events, "complete");
        assertNotNull(complete);
        assertTrue(complete.getContent().contains("请告诉我想预约的日期和时段"));
        verify(chatModel, never()).stream(any(Prompt.class));
        verify(triageAppointmentFlowService).handle(eq(1L), eq(38L), any(), eq(summary));
    }

    @Test
    void chat_shouldInjectAppointmentSuccessGuardIntoTriageSystemPrompt() {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setUserId(38L);
        session.setAgentType("TRIAGE");
        session.setTitle("新对话");
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(messageMapper.selectList(any())).thenReturn(Collections.emptyList());

        Agent agent = mock(Agent.class);
        when(agentFactory.getAgent("TRIAGE")).thenReturn(agent);
        when(agent.getToolNames()).thenReturn(Collections.emptyList());
        when(agent.getSystemPrompt()).thenReturn("triage-system");
        when(agent.getAgentType()).thenReturn("TRIAGE");

        when(chatModel.stream(argThat((Prompt prompt) -> {
            if (prompt == null || prompt.getInstructions() == null || prompt.getInstructions().isEmpty()) {
                return false;
            }
            String systemText = prompt.getInstructions().get(0).getText();
            return systemText != null
                    && systemText.contains("patientId = 38")
                    && systemText.contains(APPOINTMENT_SUCCESS_GUARD_TEXT);
        }))).thenReturn(Flux.just(mockChatResponse("收到")));

        when(ttsService.synthesize("收到")).thenReturn("/ai/chat/tts/x.mp3");

        List<SseMessageVO> events = chatService.chat(1L, 38L, "我要预约")
                .take(3)
                .collectList()
                .block(Duration.ofSeconds(1));

        assertNotNull(events);
        assertEquals("complete", events.get(1).getType());
    }

    @Test
    void chat_shouldRewriteFakeAppointmentSuccessReplyWhenCreateAppointmentToolWasNotCalled() {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setUserId(38L);
        session.setAgentType("TRIAGE");
        session.setTitle("新对话");
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(messageMapper.selectList(any())).thenReturn(Collections.emptyList());

        Agent agent = mock(Agent.class);
        when(agentFactory.getAgent("TRIAGE")).thenReturn(agent);
        when(agent.getToolNames()).thenReturn(Collections.emptyList());
        when(agent.getSystemPrompt()).thenReturn("triage-system");
        when(agent.getAgentType()).thenReturn("TRIAGE");

        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                mockChatResponse("好的，我已经为您成功创建了预约！\n预约ID：100")
        ));
        when(ttsService.synthesize("抱歉，刚才尚未成功创建预约，请重新确认医生与时间后，我再为您提交预约。"))
                .thenReturn("/ai/chat/tts/guard.mp3");

        List<SseMessageVO> events = chatService.chat(1L, 38L, "帮我预约李伟")
                .take(3)
                .collectList()
                .block(Duration.ofSeconds(1));

        assertNotNull(events);
        SseMessageVO complete = findFirstEventByType(events, "complete");
        assertNotNull(complete);
        assertEquals("抱歉，刚才尚未成功创建预约，请重新确认医生与时间后，我再为您提交预约。", complete.getContent());
    }

    @Test
    void chat_shouldAutoCreateAppointmentWhenModelClaimsSuccessButToolNotCalled() {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setUserId(38L);
        session.setAgentType("TRIAGE");
        session.setTitle("新对话");
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(messageMapper.selectList(any())).thenReturn(Collections.emptyList());

        Agent agent = mock(Agent.class);
        when(agentFactory.getAgent("TRIAGE")).thenReturn(agent);
        when(agent.getToolNames()).thenReturn(Collections.emptyList());
        when(agent.getSystemPrompt()).thenReturn("triage-system");
        when(agent.getAgentType()).thenReturn("TRIAGE");

        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                mockChatResponse("现在为您创建预约。好的，我已经为您成功创建了预约！\n预约ID：100\n医生doctorId:2，slotId为475")
        ));

        SlotInfoDTO slot = new SlotInfoDTO();
        slot.setId(475L);
        slot.setDoctorId(2L);
        slot.setDoctorName("李伟");
        slot.setScheduleDate(LocalDate.now());
        slot.setPeriod("afternoon");
        when(remoteScheduleService.getAvailableSlots(eq(2L), any())).thenReturn(R.ok(List.of(slot)));

        when(remoteAppointmentService.createAppointment(38L, 2L, 475L, 1L)).thenReturn(R.ok(101L));
        lenient().when(ttsService.synthesize(any())).thenReturn("/ai/chat/tts/auto-create.mp3");

        List<SseMessageVO> events = chatService.chat(1L, 38L, "帮我预约")
                .takeUntil(event -> "complete".equals(event.getType()))
                .collectList()
                .block(Duration.ofSeconds(1));

        assertNotNull(events);
        SseMessageVO complete = findFirstEventByType(events, "complete");
        assertNotNull(complete);
        assertEquals("现在为您创建预约。好的，我已经为您成功创建了预约！", complete.getContent());
        assertNoPatientVisibleInternalIdentifiers(complete.getContent());
    }

    @Test
    void chat_shouldAcceptExistingAppointmentWhenToolResultWasNotPersisted() {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setUserId(38L);
        session.setAgentType("TRIAGE");
        session.setTitle("new chat");
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(messageMapper.selectList(any())).thenReturn(Collections.emptyList(), Collections.emptyList());

        Agent agent = mock(Agent.class);
        when(agentFactory.getAgent("TRIAGE")).thenReturn(agent);
        when(agent.getToolNames()).thenReturn(Collections.emptyList());
        when(agent.getSystemPrompt()).thenReturn("triage-system");
        when(agent.getAgentType()).thenReturn("TRIAGE");

        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                mockChatResponse("appointmentId: 999 doctorId:2 slotId:475")
        ));

        SlotInfoDTO slot = new SlotInfoDTO();
        slot.setId(475L);
        slot.setDoctorId(2L);
        slot.setScheduleDate(LocalDate.now());
        slot.setPeriod("morning");
        when(remoteScheduleService.getAvailableSlots(eq(2L), any())).thenReturn(R.ok(List.of(slot)));

        AppointmentDTO existing = new AppointmentDTO();
        existing.setId(101L);
        existing.setPatientId(38L);
        existing.setDoctorId(2L);
        existing.setSlotId(475L);
        when(remoteAppointmentService.getAppointmentByPatientAndSlot(38L, 475L)).thenReturn(R.ok(existing));
        when(remoteAppointmentService.bindSession(101L, 1L)).thenReturn(R.ok());
        when(ttsService.synthesize(any())).thenReturn("/ai/chat/tts/existing-appointment.mp3");

        List<SseMessageVO> events = chatService.chat(1L, 38L, "confirm")
                .take(3)
                .collectList()
                .block(Duration.ofSeconds(1));

        assertNotNull(events);
        SseMessageVO complete = findFirstEventByType(events, "complete");
        assertNotNull(complete);
        assertEquals("预约已成功创建。请按时就诊。", complete.getContent());
        assertNoPatientVisibleInternalIdentifiers(complete.getContent());
        verify(remoteAppointmentService).bindSession(101L, 1L);
        verify(remoteAppointmentService, never()).createAppointment(38L, 2L, 475L, 1L);
    }

    @Test
    void chat_shouldAcceptVerifiedAppointmentIdWhenAssistantReplyOmitsDoctorId() {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setUserId(38L);
        session.setAgentType("TRIAGE");
        session.setTitle("鏂板璇?");
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(messageMapper.selectList(any())).thenReturn(Collections.emptyList());

        Agent agent = mock(Agent.class);
        when(agentFactory.getAgent("TRIAGE")).thenReturn(agent);
        when(agent.getToolNames()).thenReturn(Collections.emptyList());
        when(agent.getSystemPrompt()).thenReturn("triage-system");
        when(agent.getAgentType()).thenReturn("TRIAGE");

        String assistantText = "\u9884\u7ea6\u6210\u529f\uff0c\u9884\u7ea6\u7f16\u53f7\uff1a101";
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(mockChatResponse(assistantText)));

        AppointmentDTO appointment = new AppointmentDTO();
        appointment.setId(101L);
        appointment.setPatientId(38L);
        appointment.setSessionId(1L);
        when(remoteAppointmentService.getAppointmentSnapshot(101L)).thenReturn(R.ok(appointment));
        when(ttsService.synthesize(any())).thenReturn("/ai/chat/tts/verified-appointment.mp3");

        List<SseMessageVO> events = chatService.chat(1L, 38L, "confirm")
                .take(3)
                .collectList()
                .block(Duration.ofSeconds(1));

        assertNotNull(events);
        SseMessageVO complete = findFirstEventByType(events, "complete");
        assertNotNull(complete);
        assertEquals("预约成功", complete.getContent());
        assertNoPatientVisibleInternalIdentifiers(complete.getContent());
        verify(remoteAppointmentService).getAppointmentSnapshot(101L);
        verify(remoteAppointmentService, never()).createAppointment(any(), any(), any(), any());
    }

    @Test
    void chat_shouldVerifyAppointmentIdFromNumericCandidatesWhenLabelVaries() {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setUserId(38L);
        session.setAgentType("TRIAGE");
        session.setTitle("鏂板璇?");
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(messageMapper.selectList(any())).thenReturn(Collections.emptyList());

        Agent agent = mock(Agent.class);
        when(agentFactory.getAgent("TRIAGE")).thenReturn(agent);
        when(agent.getToolNames()).thenReturn(Collections.emptyList());
        when(agent.getSystemPrompt()).thenReturn("triage-system");
        when(agent.getAgentType()).thenReturn("TRIAGE");

        String assistantText = "\u9884\u7ea6\u6210\u529f\uff0c2026\u5e745\u670825\u65e5\u4e0a\u5348\uff0c\u6302\u53f7\u8d390\u5143\uff0c\u9884\u7ea6\u5355\u53f7\uff1a101";
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(mockChatResponse(assistantText)));

        AppointmentDTO appointment = new AppointmentDTO();
        appointment.setId(101L);
        appointment.setPatientId(38L);
        appointment.setSessionId(1L);
        when(remoteAppointmentService.getAppointmentSnapshot(101L)).thenReturn(R.ok(appointment));
        when(ttsService.synthesize(any())).thenReturn("/ai/chat/tts/verified-candidate.mp3");

        List<SseMessageVO> events = chatService.chat(1L, 38L, "confirm")
                .take(3)
                .collectList()
                .block(Duration.ofSeconds(1));

        assertNotNull(events);
        SseMessageVO complete = findFirstEventByType(events, "complete");
        assertNotNull(complete);
        assertEquals("预约成功，2026年5月25日上午，挂号费0元", complete.getContent());
        assertNoPatientVisibleInternalIdentifiers(complete.getContent());
        verify(remoteAppointmentService).getAppointmentSnapshot(101L);
        verify(remoteAppointmentService, never()).createAppointment(any(), any(), any(), any());
    }

    @Test
    void endSession_shouldGenerateTriageSummaryWithAppointmentIdWhenSessionBound() {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setUserId(38L);
        session.setSessionType("TRIAGE");
        when(sessionMapper.selectById(1L)).thenReturn(session);

        AppointmentDTO appointment = new AppointmentDTO();
        appointment.setId(101L);
        appointment.setSessionId(1L);
        when(remoteAppointmentService.getAppointmentBySession(1L)).thenReturn(R.ok(appointment));

        chatService.endSession(1L, 38L);

        verify(summaryService).generateSummary(1L, 101L);
    }

    @Test
    void chat_shouldAutoCreateUsingPeriodChoiceWhenSlotIdNotPresentInAssistantText() {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setUserId(38L);
        session.setAgentType("TRIAGE");
        session.setTitle("新对话");
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(messageMapper.selectList(any())).thenReturn(Collections.emptyList());

        Agent agent = mock(Agent.class);
        when(agentFactory.getAgent("TRIAGE")).thenReturn(agent);
        when(agent.getToolNames()).thenReturn(Collections.emptyList());
        when(agent.getSystemPrompt()).thenReturn("triage-system");
        when(agent.getAgentType()).thenReturn("TRIAGE");

        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                mockChatResponse("好的，我将为您重新预约李娜医生下午的时间段。\n- 医生：李娜医生（医生ID：4，内科，挂号费60元）\n- 预约时间：2026年4月16日 下午\n现在为您创建预约。好的，我已经为您成功创建了预约！")
        ));

        SlotInfoDTO afternoonSlot = new SlotInfoDTO();
        afternoonSlot.setId(418L);
        afternoonSlot.setDoctorId(4L);
        afternoonSlot.setDoctorName("李娜");
        afternoonSlot.setScheduleDate(LocalDate.of(2026, 4, 16));
        afternoonSlot.setPeriod("afternoon");
        afternoonSlot.setStartTime(LocalTime.of(14, 0));
        afternoonSlot.setEndTime(LocalTime.of(18, 0));

        when(remoteScheduleService.getAvailableSlots(4L, "2026-04-16")).thenReturn(R.ok(List.of(afternoonSlot)));
        when(remoteAppointmentService.createAppointment(38L, 4L, 418L, 1L)).thenReturn(R.ok(102L));
        when(ttsService.synthesize(any())).thenReturn("/ai/chat/tts/auto-create-period.mp3");

        List<SseMessageVO> events = chatService.chat(1L, 38L, "下午")
                .take(5)
                .collectList()
                .block(Duration.ofSeconds(1));

        assertNotNull(events);
        SseMessageVO complete = findFirstEventByType(events, "complete");
        assertNotNull(complete);
        assertTrue(complete.getContent().contains("李娜医生"));
        assertTrue(complete.getContent().contains("2026年4月16日 下午"));
        assertTrue(complete.getContent().contains("好的，我已经为您成功创建了预约！"));
        assertNoPatientVisibleInternalIdentifiers(complete.getContent());
    }

    @Test
    void chat_shouldEmitCompleteBeforeTtsEvent() throws Exception {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setUserId(1L);
        session.setAgentType("QA");
        session.setTitle("新对话");
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(messageMapper.selectList(any())).thenReturn(Collections.emptyList());

        Agent agent = mock(Agent.class);
        when(agentFactory.getAgent("QA")).thenReturn(agent);
        when(agent.getToolNames()).thenReturn(Collections.emptyList());
        when(agent.getSystemPrompt()).thenReturn("system");
        when(agent.getAgentType()).thenReturn("QA");

        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
            mockChatResponse("你"),
            mockChatResponse("好")
        ));
        when(ttsService.synthesize("你好")).thenAnswer(invocation -> {
            Thread.sleep(80L);
            return "/ai/chat/tts/tts-file.mp3";
        });

        List<SseMessageVO> events = chatService.chat(1L, 1L, "请问")
            .take(4)
            .collectList()
            .block(Duration.ofMillis(400));

        assertNotNull(events);
        assertEquals(4, events.size());
        assertEquals("token", events.get(0).getType());
        assertEquals("你", events.get(0).getContent());
        assertEquals("token", events.get(1).getType());
        assertEquals("好", events.get(1).getContent());
        assertEquals("complete", events.get(2).getType());
        assertEquals("你好", events.get(2).getContent());
        assertNull(events.get(2).getTtsUrl());
        assertNotNull(events.get(2).getMetadata());
        assertCue(events.get(2).getMetadata(), "knowledge_explanation", "calm", "explain", "QA");
        assertEquals("tts", events.get(3).getType());
        assertNull(events.get(3).getContent());
        assertEquals("/ai/chat/tts/tts-file.mp3", events.get(3).getTtsUrl());
        assertNotNull(events.get(3).getMetadata());
        assertCue(events.get(3).getMetadata(), "knowledge_explanation", "calm", "explain", "QA");

        verify(messageMapper).updateById(argThat(hasTtsUrl("/ai/chat/tts/tts-file.mp3")));
    }

    @Test
    void chat_shouldAttachAvatarCueMetadataToAssistantMessageAndSseEvents() throws Exception {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setUserId(38L);
        session.setAgentType("TRIAGE");
        session.setTitle("新对话");
        when(sessionMapper.selectById(1L)).thenReturn(session);

        ChatMessage successToolMessage = new ChatMessage();
        successToolMessage.setRole("tool");
        successToolMessage.setToolName("createAppointment");
        successToolMessage.setContent("{\"success\":true,\"appointmentId\":101}");
        when(messageMapper.selectList(any())).thenReturn(Collections.emptyList(), List.of(successToolMessage));

        Agent agent = mock(Agent.class);
        when(agentFactory.getAgent("TRIAGE")).thenReturn(agent);
        when(agent.getToolNames()).thenReturn(Collections.emptyList());
        when(agent.getSystemPrompt()).thenReturn("triage-system");
        when(agent.getAgentType()).thenReturn("TRIAGE");

        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                mockChatResponse("好的，我已经为您成功创建了预约！\n预约ID：101")
        ));
        when(ttsService.synthesize("好的，我已经为您成功创建了预约！")).thenReturn("/ai/chat/tts/appointment-success-1.mp3");

        List<SseMessageVO> events = chatService.chat(1L, 38L, "帮我预约")
                .take(4)
                .collectList()
                .block(Duration.ofSeconds(1));

        assertNotNull(events);
        SseMessageVO complete = findFirstEventByType(events, "complete");
        assertNotNull(complete);
        assertNoPatientVisibleInternalIdentifiers(complete.getContent());
        assertCue(complete.getMetadata(), "appointment_success", "relieved", "celebrate", "TRIAGE");

        SseMessageVO firstTts = findFirstEventByType(events, "tts");
        assertNotNull(firstTts);
        assertCue(firstTts.getMetadata(), "appointment_success", "relieved", "celebrate", "TRIAGE");

        ArgumentCaptor<ChatMessage> insertCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageMapper, times(2)).insert(insertCaptor.capture());
        ChatMessage assistantMessage = insertCaptor.getAllValues().stream()
                .filter(message -> "assistant".equals(message.getRole()))
                .findFirst()
                .orElseThrow();

        assertNotNull(assistantMessage.getMetadata());
        assertNoPatientVisibleInternalIdentifiers(assistantMessage.getContent());
        Map<String, Object> metadata = new ObjectMapper().readValue(assistantMessage.getMetadata(), Map.class);
        assertCue(metadata, "appointment_success", "relieved", "celebrate", "TRIAGE");
    }

    @Test
    void chat_shouldGenerateNormalizedSuggestedRepliesForTriageCompleteEventAndPersistedMetadata() throws Exception {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setUserId(38L);
        session.setAgentType("TRIAGE");
        session.setTitle("新对话");
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(messageMapper.selectList(any())).thenReturn(Collections.emptyList());

        Agent agent = mock(Agent.class);
        when(agentFactory.getAgent("TRIAGE")).thenReturn(agent);
        when(agent.getToolNames()).thenReturn(Collections.emptyList());
        when(agent.getSystemPrompt()).thenReturn("triage-system");
        when(agent.getAgentType()).thenReturn("TRIAGE");

        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
            mockChatResponse("症状持续多久了？")
        ));
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse("""
            ["  三天了  ", "伴有   发烧", "", "这是一条一定会超过二十个字符因此必须过滤掉的回复", "没有其他症状", "三天了", "补充一句"]
            """));
        when(ttsService.synthesize(anyString())).thenReturn("/ai/chat/tts/triage-question.mp3");

        List<SseMessageVO> events = chatService.chat(1L, 38L, "我咳嗽")
            .take(5)
            .collectList()
            .block(Duration.ofSeconds(2));

        assertNotNull(events);
        SseMessageVO complete = findFirstEventByType(events, "complete");
        assertNotNull(complete);
        assertSuggestedReplies(complete.getMetadata(), "三天了", "伴有 发烧", "没有其他症状");
        assertCue(complete.getMetadata(), "symptom_collection", "attentive", "listen", "TRIAGE");

        SseMessageVO tokenEvent = findFirstEventByType(events, "token");
        assertNotNull(tokenEvent);
        assertNull(tokenEvent.getMetadata());

        SseMessageVO ttsEvent = findFirstEventByType(events, "tts");
        assertNotNull(ttsEvent);
        assertCue(ttsEvent.getMetadata(), "symptom_collection", "attentive", "listen", "TRIAGE");
        assertNoSuggestedReplies(ttsEvent.getMetadata());

        ArgumentCaptor<ChatMessage> insertCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageMapper, times(2)).insert(insertCaptor.capture());
        ChatMessage assistantMessage = insertCaptor.getAllValues().stream()
            .filter(message -> "assistant".equals(message.getRole()))
            .findFirst()
            .orElseThrow();
        Map<String, Object> metadata = new ObjectMapper().readValue(assistantMessage.getMetadata(), Map.class);
        assertSuggestedReplies(metadata, "三天了", "伴有 发烧", "没有其他症状");
    }

    @Test
    void chat_shouldOnlyGenerateSuggestedRepliesForTriageQuestion() {
        ChatSession qaSession = new ChatSession();
        qaSession.setId(1L);
        qaSession.setUserId(1L);
        qaSession.setAgentType("QA");
        qaSession.setTitle("新对话");
        when(sessionMapper.selectById(1L)).thenReturn(qaSession);
        when(messageMapper.selectList(any())).thenReturn(Collections.emptyList());

        Agent qaAgent = mock(Agent.class);
        when(agentFactory.getAgent("QA")).thenReturn(qaAgent);
        when(qaAgent.getToolNames()).thenReturn(Collections.emptyList());
        when(qaAgent.getSystemPrompt()).thenReturn("qa-system");
        when(qaAgent.getAgentType()).thenReturn("QA");

        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(mockChatResponse("请问还有别的问题吗？")));
        when(ttsService.synthesize("请问还有别的问题吗？")).thenReturn("/ai/chat/tts/qa-question.mp3");

        List<SseMessageVO> qaEvents = chatService.chat(1L, 1L, "咨询")
            .take(3)
            .collectList()
            .block(Duration.ofSeconds(2));

        assertNotNull(qaEvents);
        SseMessageVO qaComplete = findFirstEventByType(qaEvents, "complete");
        assertNotNull(qaComplete);
        assertNoSuggestedReplies(qaComplete.getMetadata());
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void chat_shouldSkipSuggestedRepliesWhenFinalAssistantTextIsNotQuestion() {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setUserId(1L);
        session.setAgentType("TRIAGE");
        session.setTitle("新对话");
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(messageMapper.selectList(any())).thenReturn(Collections.emptyList());

        Agent agent = mock(Agent.class);
        when(agentFactory.getAgent("TRIAGE")).thenReturn(agent);
        when(agent.getToolNames()).thenReturn(Collections.emptyList());
        when(agent.getSystemPrompt()).thenReturn("triage-system");
        when(agent.getAgentType()).thenReturn("TRIAGE");

        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(mockChatResponse("建议先休息，多喝温水。")));
        when(ttsService.synthesize("建议先休息，多喝温水。")).thenReturn("/ai/chat/tts/no-question.mp3");

        List<SseMessageVO> events = chatService.chat(1L, 1L, "头痛")
            .take(3)
            .collectList()
            .block(Duration.ofSeconds(2));

        assertNotNull(events);
        SseMessageVO complete = findFirstEventByType(events, "complete");
        assertNotNull(complete);
        assertNoSuggestedReplies(complete.getMetadata());
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void chat_shouldSkipSuggestedRepliesWhenEarlierSentenceHasQuestionButFinalSentenceIsNotQuestion() {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setUserId(1L);
        session.setAgentType("TRIAGE");
        session.setTitle("新对话");
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(messageMapper.selectList(any())).thenReturn(Collections.emptyList());

        Agent agent = mock(Agent.class);
        when(agentFactory.getAgent("TRIAGE")).thenReturn(agent);
        when(agent.getToolNames()).thenReturn(Collections.emptyList());
        when(agent.getSystemPrompt()).thenReturn("triage-system");
        when(agent.getAgentType()).thenReturn("TRIAGE");

        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
            mockChatResponse("持续多久了？建议先多喝温水。")
        ));
        when(ttsService.synthesize("持续多久了？")).thenReturn("/ai/chat/tts/mixed-1.mp3");
        when(ttsService.synthesize("建议先多喝温水。")).thenReturn("/ai/chat/tts/mixed-2.mp3");

        List<SseMessageVO> events = chatService.chat(1L, 1L, "头痛")
            .take(4)
            .collectList()
            .block(Duration.ofSeconds(2));

        assertNotNull(events);
        SseMessageVO complete = findFirstEventByType(events, "complete");
        assertNotNull(complete);
        assertNoSuggestedReplies(complete.getMetadata());
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void chat_shouldOmitSuggestedRepliesWhenGenerationFails() {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setUserId(1L);
        session.setAgentType("TRIAGE");
        session.setTitle("新对话");
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(messageMapper.selectList(any())).thenReturn(Collections.emptyList());

        Agent agent = mock(Agent.class);
        when(agentFactory.getAgent("TRIAGE")).thenReturn(agent);
        when(agent.getToolNames()).thenReturn(Collections.emptyList());
        when(agent.getSystemPrompt()).thenReturn("triage-system");
        when(agent.getAgentType()).thenReturn("TRIAGE");

        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(mockChatResponse("疼了几天？")));
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("model offline"));
        when(ttsService.synthesize("疼了几天？")).thenReturn("/ai/chat/tts/fallback.mp3");

        List<SseMessageVO> events = chatService.chat(1L, 1L, "腹痛")
            .take(3)
            .collectList()
            .block(Duration.ofSeconds(2));

        assertNotNull(events);
        SseMessageVO complete = findFirstEventByType(events, "complete");
        assertNotNull(complete);
        assertSuggestedReplies(complete.getMetadata(), "今天刚开始", "已经好几天了", "没有其他症状");
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void chat_shouldTruncateMultiQuestionTriageReplyToSingleQuestionAndUseFallbackReplies() {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setUserId(1L);
        session.setAgentType("TRIAGE");
        session.setTitle("新对话");
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(messageMapper.selectList(any())).thenReturn(Collections.emptyList());

        Agent agent = mock(Agent.class);
        when(agentFactory.getAgent("TRIAGE")).thenReturn(agent);
        when(agent.getToolNames()).thenReturn(Collections.emptyList());
        when(agent.getSystemPrompt()).thenReturn("triage-system");
        when(agent.getAgentType()).thenReturn("TRIAGE");

        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(mockChatResponse(
            "好的，头疼确实让人很不舒服。为了能更准确地帮您分析，我想再了解几个细节:1.头疼持续多久了?是今天刚开始，还是已经好几天了?2.头疼的部位在哪里?是整个头都疼，还是偏头痛、后脑勺痛，或者太阳穴附近痛?3.除了头疼，还有其他不舒服吗?比如发烧、恶心呕吐、头晕、视力模糊、脖子僵硬等情况?"
        )));
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse("[]"));
        when(ttsService.synthesize(anyString())).thenReturn("/ai/chat/tts/headache-followup.mp3");

        List<SseMessageVO> events = chatService.chat(1L, 1L, "我头疼")
            .take(12)
            .collectList()
            .block(Duration.ofSeconds(2));

        assertNotNull(events);
        SseMessageVO complete = findFirstEventByType(events, "complete");
        assertNotNull(complete);
        assertEquals("好的，头疼确实让人很不舒服。为了能更准确地帮您分析，我想再了解几个细节:1.头疼持续多久了?", complete.getContent());
        assertSuggestedReplies(complete.getMetadata(), "今天刚开始", "已经好几天了", "没有其他症状");
    }

    @Test
    void chat_shouldSplitSafeSentencesIntoMultipleTtsSegments() {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setUserId(1L);
        session.setAgentType("QA");
        session.setTitle("新对话");
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(messageMapper.selectList(any())).thenReturn(Collections.emptyList());

        Agent agent = mock(Agent.class);
        when(agentFactory.getAgent("QA")).thenReturn(agent);
        when(agent.getToolNames()).thenReturn(Collections.emptyList());
        when(agent.getSystemPrompt()).thenReturn("system");
        when(agent.getAgentType()).thenReturn("QA");

        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
            mockChatResponse("第一句。第二句。")
        ));
        when(ttsService.synthesize("第一句。")).thenReturn("/ai/chat/tts/seg-1.mp3");
        when(ttsService.synthesize("第二句。")).thenReturn("/ai/chat/tts/seg-2.mp3");

        List<SseMessageVO> events = chatService.chat(1L, 1L, "请介绍一下")
            .collectList()
            .block(Duration.ofSeconds(2));

        assertNotNull(events);
        assertTrue(events.size() >= 3);

        SseMessageVO complete = findFirstEventByType(events, "complete");
        assertNotNull(complete);
        assertEquals("第一句。第二句。", complete.getContent());
        assertEquals(2, complete.getTotalSegments());

        List<SseMessageVO> ttsEvents = events.stream()
            .filter(event -> "tts".equals(event.getType()))
            .sorted((left, right) -> Integer.compare(left.getSegmentIndex(), right.getSegmentIndex()))
            .toList();
        assertEquals(2, ttsEvents.size());
        assertEquals(0, ttsEvents.get(0).getSegmentIndex());
        assertEquals("/ai/chat/tts/seg-1.mp3", ttsEvents.get(0).getTtsUrl());
        assertEquals(1, ttsEvents.get(1).getSegmentIndex());
        assertEquals("/ai/chat/tts/seg-2.mp3", ttsEvents.get(1).getTtsUrl());

        verify(messageMapper).updateById(argThat((ChatMessage message) ->
            message != null
                && "第一句。第二句。".equals(message.getContent())
                && List.of("/ai/chat/tts/seg-1.mp3", "/ai/chat/tts/seg-2.mp3").contains(message.getTtsUrl())
        ));
    }

    @Test
    void chat_shouldExcludeTriageDisclaimerFromTtsSegments() {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setUserId(1L);
        session.setAgentType("TRIAGE");
        session.setTitle("新对话");
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(messageMapper.selectList(any())).thenReturn(Collections.emptyList());

        Agent agent = mock(Agent.class);
        when(agentFactory.getAgent("TRIAGE")).thenReturn(agent);
        when(agent.getToolNames()).thenReturn(Collections.emptyList());
        when(agent.getSystemPrompt()).thenReturn("triage-system");
        when(agent.getAgentType()).thenReturn("TRIAGE");

        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
            mockChatResponse("请先多喝温水，注意休息。AI导诊仅供参考，不能替代专业医生诊断。")
        ));
        when(ttsService.synthesize("请先多喝温水，注意休息。")).thenReturn("/ai/chat/tts/advice.mp3");

        List<SseMessageVO> events = chatService.chat(1L, 1L, "我头痛")
            .take(3)
            .collectList()
            .block(Duration.ofSeconds(2));

        assertNotNull(events);

        SseMessageVO complete = findFirstEventByType(events, "complete");
        assertNotNull(complete);
        assertEquals("请先多喝温水，注意休息。AI导诊仅供参考，不能替代专业医生诊断。", complete.getContent());
        assertEquals(1, complete.getTotalSegments());

        List<SseMessageVO> ttsEvents = events.stream()
            .filter(event -> "tts".equals(event.getType()))
            .toList();
        assertEquals(1, ttsEvents.size());
        assertEquals("/ai/chat/tts/advice.mp3", ttsEvents.get(0).getTtsUrl());

        verify(ttsService, never()).synthesize("AI导诊仅供参考，不能替代专业医生诊断。");
        verify(messageMapper, atLeastOnce()).updateById(argThat(hasTtsUrl("/ai/chat/tts/advice.mp3")));
    }

    @Test
    void getSessionMessages_shouldDeserializeAvatarCueMetadata() {
        ChatSession session = new ChatSession();
        session.setId(9L);
        session.setUserId(7L);
        when(sessionMapper.selectById(9L)).thenReturn(session);

        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setId(12L);
        assistantMessage.setSessionId(9L);
        assistantMessage.setRole("assistant");
        assistantMessage.setContent("请描述一下您的症状");
        assistantMessage.setMetadata("{\"avatarCue\":{\"bucket\":\"symptom_collection\",\"expression\":\"attentive\",\"action\":\"listen\",\"tone\":\"supportive\",\"variant\":\"general\"},\"source\":\"TRIAGE\"}");
        when(messageMapper.selectList(any())).thenReturn(List.of(assistantMessage));

        List<ChatMessageVO> result = chatService.getSessionMessages(9L, 7L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertNotNull(result.get(0).getMetadata());
        assertCue(result.get(0).getMetadata(), "symptom_collection", "attentive", "listen", "TRIAGE");
    }

    @Test
    void getSessionMessages_shouldDeserializeSuggestedRepliesFromStoredMetadata() {
        ChatSession session = new ChatSession();
        session.setId(9L);
        session.setUserId(7L);
        when(sessionMapper.selectById(9L)).thenReturn(session);

        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setId(12L);
        assistantMessage.setSessionId(9L);
        assistantMessage.setRole("assistant");
        assistantMessage.setContent("持续多久了？");
        assistantMessage.setMetadata("{" +
            "\"avatarCue\":{\"bucket\":\"symptom_collection\",\"expression\":\"attentive\",\"action\":\"listen\",\"tone\":\"supportive\",\"variant\":\"general\"}," +
            "\"source\":\"TRIAGE\"," +
            "\"suggestedReplies\":[\"三天了\",\"伴有发烧\",\"没有其他症状\"]}");
        when(messageMapper.selectList(any())).thenReturn(List.of(assistantMessage));

        List<ChatMessageVO> result = chatService.getSessionMessages(9L, 7L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertSuggestedReplies(result.get(0).getMetadata(), "三天了", "伴有发烧", "没有其他症状");
    }

    @Test
    void chat_shouldReturnRateLimitErrorWhenSentinelBlocks() throws Exception {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setUserId(1L);
        session.setAgentType("QA");
        session.setTitle("新对话");
        when(sessionMapper.selectById(1L)).thenReturn(session);

        FlowRule rule = new FlowRule(ChatServiceImpl.CHAT_STREAM_RESOURCE);
        rule.setGrade(RuleConstant.FLOW_GRADE_THREAD);
        rule.setCount(1);
        FlowRuleManager.loadRules(List.of(rule));

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> holderFailure = new AtomicReference<>();
        Thread holder = new Thread(() -> {
            try (Entry ignored = SphU.entry(ChatServiceImpl.CHAT_STREAM_RESOURCE)) {
                entered.countDown();
                release.await();
            } catch (Throwable t) {
                holderFailure.set(t);
            }
        });
        holder.start();
        entered.await();

        List<SseMessageVO> events = chatService.chat(1L, 1L, "限流测试")
                .onErrorResume(ex -> {
                    SseMessageVO error = new SseMessageVO();
                    error.setType("error");
                    error.setContent(ex.getMessage());
                    return Flux.just(error);
                })
                .collectList()
                .block(Duration.ofSeconds(1));

        release.countDown();
        holder.join();

        assertNull(holderFailure.get());
        assertNotNull(events);
        assertEquals(1, events.size());
        assertEquals("error", events.get(0).getType());
        assertEquals("请求过于频繁，请稍后再试", events.get(0).getContent());
        verify(messageMapper, never()).insert(argThat((ChatMessage message) -> message != null && "user".equals(message.getRole())));
    }

    private static ChatResponse mockChatResponse(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

    private static ArgumentMatcher<ChatMessage> hasTtsUrl(String expectedTtsUrl) {
        return message -> message != null && expectedTtsUrl.equals(message.getTtsUrl());
    }

    private static SseMessageVO findFirstEventByType(List<SseMessageVO> events, String type) {
        return events.stream()
            .filter(event -> type.equals(event.getType()))
            .findFirst()
            .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static void assertCue(Map<String, Object> metadata,
                                  String expectedBucket,
                                  String expectedExpression,
                                  String expectedAction,
                                  String expectedSource) {
        assertNotNull(metadata);
        assertFalse(metadata.isEmpty());
        assertEquals(expectedSource, metadata.get("source"));
        assertTrue(metadata.get("avatarCue") instanceof Map);
        Map<String, Object> avatarCue = (Map<String, Object>) metadata.get("avatarCue");
        assertEquals(expectedBucket, avatarCue.get("bucket"));
        assertEquals(expectedExpression, avatarCue.get("expression"));
        assertEquals(expectedAction, avatarCue.get("action"));
        assertNotNull(avatarCue.get("tone"));
        assertNotNull(avatarCue.get("variant"));
    }

    @SuppressWarnings("unchecked")
    private static void assertSuggestedReplies(Map<String, Object> metadata, String... expectedReplies) {
        assertNotNull(metadata);
        assertTrue(metadata.get("suggestedReplies") instanceof List);
        List<Object> actualReplies = (List<Object>) metadata.get("suggestedReplies");
        assertIterableEquals(List.of(expectedReplies), actualReplies.stream().map(item -> Objects.toString(item, null)).toList());
    }

    private static void assertNoSuggestedReplies(Map<String, Object> metadata) {
        assertNotNull(metadata);
        assertFalse(metadata.containsKey("suggestedReplies"));
    }

    private static void assertNoPatientVisibleInternalIdentifiers(String content) {
        assertNotNull(content);
        assertFalse(content.contains("patientId"));
        assertFalse(content.contains("doctorId"));
        assertFalse(content.contains("slotId"));
        assertFalse(content.contains("appointmentId"));
        assertFalse(content.contains("患者ID"));
        assertFalse(content.contains("医生ID"));
        assertFalse(content.contains("时间段ID"));
        assertFalse(content.contains("预约ID"));
        assertFalse(content.contains("预约编号"));
        assertFalse(content.contains("预约单号"));
    }
}
