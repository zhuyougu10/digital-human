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
import com.medical.ai.domain.vo.SseMessageVO;
import com.medical.ai.mapper.ChatMessageMapper;
import com.medical.ai.mapper.ChatSessionMapper;
import com.medical.ai.service.SummaryService;
import com.medical.ai.service.TtsService;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
            new ObjectMapper()
        );
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
        assertEquals("tts", events.get(3).getType());
        assertNull(events.get(3).getContent());
        assertEquals("/ai/chat/tts/tts-file.mp3", events.get(3).getTtsUrl());

        verify(messageMapper).updateById(argThat(hasTtsUrl("/ai/chat/tts/tts-file.mp3")));
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
}
