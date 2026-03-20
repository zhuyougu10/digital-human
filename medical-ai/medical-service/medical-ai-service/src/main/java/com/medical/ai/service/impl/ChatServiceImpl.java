package com.medical.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.ai.agent.Agent;
import com.medical.ai.agent.AgentFactory;
import com.medical.ai.domain.entity.ChatMessage;
import com.medical.ai.domain.entity.ChatSession;
import com.medical.ai.domain.vo.ChatMessageVO;
import com.medical.ai.domain.vo.ChatSessionVO;
import com.medical.ai.domain.vo.SseMessageVO;
import com.medical.ai.mapper.ChatMessageMapper;
import com.medical.ai.mapper.ChatSessionMapper;
import com.medical.ai.service.ChatService;
import com.medical.ai.service.SummaryService;
import com.medical.ai.service.TtsService;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final int MAX_CONTEXT_MESSAGES = 20;
    private static final String DEFAULT_SESSION_TITLE = "新对话";

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final AgentFactory agentFactory;
    private final OpenAiChatModel chatModel;
    private final TtsService ttsService;
    private final SummaryService summaryService;
    private final ObjectMapper objectMapper;

    @Override
    public ChatSessionVO createSession(Long userId, String sessionType) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setSessionType(sessionType);
        session.setTitle(DEFAULT_SESSION_TITLE);

        String agentType = switch (sessionType) {
            case "TRIAGE" -> "TRIAGE";
            case "ENCYCLOPEDIA" -> "ENCYCLOPEDIA";
            default -> "QA";
        };
        session.setAgentType(agentType);
        session.setStatus(0);
        sessionMapper.insert(session);
        return toSessionVO(session);
    }

    @Override
    public List<ChatSessionVO> listSessions(Long userId) {
        List<ChatSession> sessions = sessionMapper.selectList(
            new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .eq(ChatSession::getDeleted, 0)
                .orderByDesc(ChatSession::getUpdateTime)
        );
        return sessions.stream().map(this::toSessionVO).collect(Collectors.toList());
    }

    @Override
    public List<ChatMessageVO> getSessionMessages(Long sessionId, Long userId) {
        assertSessionOwner(sessionId, userId);
        List<ChatMessage> messages = messageMapper.selectList(
            new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getCreateTime)
        );
        return messages.stream().map(this::toMessageVO).collect(Collectors.toList());
    }

    @Override
    public Flux<SseMessageVO> chat(Long sessionId, Long userId, String message) {
        ChatSession session = assertSessionOwner(sessionId, userId);

        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(message);
        messageMapper.insert(userMsg);

        Agent agent = agentFactory.getAgent(session.getAgentType());
        List<Message> chatMessages = buildChatMessages(sessionId, agent, userId);

        List<String> toolNames = agent.getToolNames();
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder();
        if (toolNames != null && !toolNames.isEmpty()) {
            optionsBuilder.withFunctions(new HashSet<>(toolNames));
        }
        Prompt prompt = new Prompt(chatMessages, optionsBuilder.build());

        StringBuilder fullResponse = new StringBuilder();
        AtomicReference<String> fullTextRef = new AtomicReference<>("");
        AtomicReference<ChatMessage> assistantMessageRef = new AtomicReference<>();

        return chatModel.stream(prompt)
            .publishOn(Schedulers.boundedElastic())
            .map(chatResponse -> {
                String token = "";
                if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
                    token = chatResponse.getResult().getOutput().getContent();
                    if (token == null) {
                        token = "";
                    }
                }
                fullResponse.append(token);
                SseMessageVO vo = new SseMessageVO();
                vo.setType("token");
                vo.setContent(token);
                return vo;
            })
            .doOnError(e -> log.error("Chat stream error for session {}: {}", sessionId, e.getMessage(), e))
            .concatWith(Mono.fromCallable(() -> {
                String fullText = fullResponse.toString();
                fullTextRef.set(fullText);

                // 先保存 assistant 消息，保证完整文本先落库
                ChatMessage assistantMsg = new ChatMessage();
                assistantMsg.setSessionId(sessionId);
                assistantMsg.setRole("assistant");
                assistantMsg.setContent(fullText);
                messageMapper.insert(assistantMsg);
                assistantMessageRef.set(assistantMsg);

                // 保留原有标题更新逻辑
                if (DEFAULT_SESSION_TITLE.equals(session.getTitle()) && message != null && !message.isEmpty()) {
                    session.setTitle(message.length() > 20 ? message.substring(0, 20) + "..." : message);
                    sessionMapper.updateById(session);
                }

                // 先下发 complete，避免被后续 TTS 拖住
                SseMessageVO complete = new SseMessageVO();
                complete.setType("complete");
                complete.setContent(fullText);
                complete.setTtsUrl(null);
                return complete;
            }))
            .concatWith(Mono.defer(() -> Mono.fromCallable(() -> {
                String fullText = fullTextRef.get();
                String ttsUrl = ttsService.synthesize(fullText);
                if (ttsUrl == null || ttsUrl.isBlank()) {
                    SseMessageVO error = new SseMessageVO();
                    error.setType("tts_error");
                    error.setContent("TTS 合成失败");
                    return error;
                }

                ChatMessage assistantMsg = assistantMessageRef.get();
                if (assistantMsg != null) {
                    assistantMsg.setTtsUrl(ttsUrl);
                    messageMapper.updateById(assistantMsg);
                }

                SseMessageVO tts = new SseMessageVO();
                tts.setType("tts");
                tts.setTtsUrl(ttsUrl);
                return tts;
            })
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> {
                    log.error("TTS 合成失败, sessionId={}: {}", sessionId, e.getMessage(), e);
                    SseMessageVO error = new SseMessageVO();
                    error.setType("tts_error");
                    error.setContent("TTS 超时");
                    return Mono.just(error);
                })));
    }

    @Override
    public void endSession(Long sessionId, Long userId) {
        ChatSession session = assertSessionOwner(sessionId, userId);

        session.setStatus(1);
        sessionMapper.updateById(session);
        if ("TRIAGE".equals(session.getSessionType())) {
            try {
                summaryService.generateSummary(sessionId, null);
            } catch (Exception e) {
                log.warn("摘要生成失败: {}", e.getMessage());
            }
        }
    }

    @Override
    public void deleteSession(Long sessionId, Long userId) {
        assertSessionOwner(sessionId, userId);
        sessionMapper.deleteById(sessionId);
    }

    private ChatSession assertSessionOwner(Long sessionId, Long userId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!Objects.equals(session.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return session;
    }

    private List<Message> buildChatMessages(Long sessionId, Agent agent) {
        return buildChatMessages(sessionId, agent, null);
    }

    private List<Message> buildChatMessages(Long sessionId, Agent agent, Long userId) {
        List<Message> messages = new ArrayList<>();
        String systemPrompt = agent.getSystemPrompt();
        if ("TRIAGE".equals(agent.getAgentType()) && userId != null) {
            systemPrompt += "\n\n当前患者信息：\n- patientId = " + userId
                + "\n在调用 createAppointment 工具时，请务必使用上面的 patientId。";
        }
        messages.add(new SystemMessage(systemPrompt));

        List<ChatMessage> history = messageMapper.selectList(
            new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByDesc(ChatMessage::getCreateTime)
                .last("LIMIT " + MAX_CONTEXT_MESSAGES)
        );
        Collections.reverse(history);

        for (ChatMessage msg : history) {
            if ("user".equals(msg.getRole())) {
                messages.add(new UserMessage(msg.getContent()));
            } else if ("assistant".equals(msg.getRole())) {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }
        return messages;
    }

    private ChatSessionVO toSessionVO(ChatSession session) {
        ChatSessionVO vo = new ChatSessionVO();
        vo.setId(session.getId());
        vo.setUserId(session.getUserId());
        vo.setSessionType(session.getSessionType());
        vo.setTitle(session.getTitle());
        vo.setAgentType(session.getAgentType());
        vo.setStatus(session.getStatus());
        vo.setCreateTime(session.getCreateTime());
        vo.setUpdateTime(session.getUpdateTime());
        return vo;
    }

    private ChatMessageVO toMessageVO(ChatMessage msg) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(msg.getId());
        vo.setSessionId(msg.getSessionId());
        vo.setRole(msg.getRole());
        vo.setContent(msg.getContent());
        vo.setToolCallId(msg.getToolCallId());
        vo.setToolName(msg.getToolName());
        vo.setTtsUrl(msg.getTtsUrl());
        vo.setCreateTime(msg.getCreateTime());

        if (msg.getMetadata() != null && !msg.getMetadata().isEmpty()) {
            try {
                vo.setMetadata(objectMapper.readValue(msg.getMetadata(), new TypeReference<Map<String, Object>>() {
                }));
            } catch (Exception e) {
                log.warn("Failed to parse metadata JSON: {}", e.getMessage());
            }
        }
        return vo;
    }
}
