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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final int MAX_CONTEXT_MESSAGES = 20;

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
        session.setTitle("新对话");

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
    public List<ChatMessageVO> getSessionMessages(Long sessionId) {
        List<ChatMessage> messages = messageMapper.selectList(
            new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getCreateTime)
        );
        return messages.stream().map(this::toMessageVO).collect(Collectors.toList());
    }

    @Override
    public Flux<SseMessageVO> chat(Long sessionId, Long userId, String message) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || !Objects.equals(session.getUserId(), userId)) {
            throw new BusinessException("会话不存在或无权访问");
        }

        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(message);
        messageMapper.insert(userMsg);

        Agent agent = agentFactory.getAgent(session.getAgentType());
        List<Message> chatMessages = buildChatMessages(sessionId, agent);

        List<String> toolNames = agent.getToolNames();
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder();
        if (toolNames != null && !toolNames.isEmpty()) {
            optionsBuilder.withFunctions(new HashSet<>(toolNames));
        }
        Prompt prompt = new Prompt(chatMessages, optionsBuilder.build());

        StringBuilder fullResponse = new StringBuilder();

        return chatModel.stream(prompt)
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
            .doOnComplete(() -> {
                String fullText = fullResponse.toString();
                ChatMessage assistantMsg = new ChatMessage();
                assistantMsg.setSessionId(sessionId);
                assistantMsg.setRole("assistant");
                assistantMsg.setContent(fullText);
                messageMapper.insert(assistantMsg);

                if ("新对话".equals(session.getTitle()) && message != null && !message.isEmpty()) {
                    session.setTitle(message.length() > 20 ? message.substring(0, 20) + "..." : message);
                    sessionMapper.updateById(session);
                }

                try {
                    String ttsUrl = ttsService.synthesize(fullText);
                    if (ttsUrl != null) {
                        assistantMsg.setTtsUrl(ttsUrl);
                        messageMapper.updateById(assistantMsg);
                    }
                } catch (Exception e) {
                    log.warn("TTS 合成失败: {}", e.getMessage());
                }
            })
            .doOnError(e -> log.error("Chat stream error for session {}: {}", sessionId, e.getMessage(), e))
            .concatWith(Mono.fromCallable(() -> {
                SseMessageVO complete = new SseMessageVO();
                complete.setType("complete");
                complete.setContent(fullResponse.toString());
                return complete;
            }));
    }

    @Override
    public void endSession(Long sessionId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return;
        }

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
    public void deleteSession(Long sessionId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return;
        }
        session.setDeleted(1);
        sessionMapper.updateById(session);
    }

    private List<Message> buildChatMessages(Long sessionId, Agent agent) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(agent.getSystemPrompt()));

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

