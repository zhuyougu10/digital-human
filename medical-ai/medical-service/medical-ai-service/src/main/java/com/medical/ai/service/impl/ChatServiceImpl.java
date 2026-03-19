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
import reactor.core.scheduler.Schedulers;

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
        session.setTitle("\u65b0\u5bf9\u8bdd");

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
            throw new BusinessException("\u4f1a\u8bdd\u4e0d\u5b58\u5728\u6216\u65e0\u6743\u8bbf\u95ee");
        }

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
            .doOnComplete(() -> {
                String fullText = fullResponse.toString();
                ChatMessage assistantMsg = new ChatMessage();
                assistantMsg.setSessionId(sessionId);
                assistantMsg.setRole("assistant");
                assistantMsg.setContent(fullText);
                messageMapper.insert(assistantMsg);

                if ("\u65b0\u5bf9\u8bdd".equals(session.getTitle()) && message != null && !message.isEmpty()) {
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
                    log.warn("TTS \u5408\u6210\u5931\u8d25: {}", e.getMessage());
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
                log.warn("\u6458\u8981\u751f\u6210\u5931\u8d25: {}", e.getMessage());
            }
        }
    }

    @Override
    public void deleteSession(Long sessionId) {
        // 浣跨敤 MyBatis-Plus deleteById锛孈TableLogic 浼氳嚜鍔ㄥ皢鍏惰浆鎹负
        // UPDATE chat_session SET deleted=1 WHERE id=? AND deleted=0
        // 鐩存帴 setDeleted(1)+updateById 浼氬洜 @TableLogic 璺宠繃璇ュ瓧娈佃€屾棤鏁?
        sessionMapper.deleteById(sessionId);
    }

    private List<Message> buildChatMessages(Long sessionId, Agent agent) {
        return buildChatMessages(sessionId, agent, null);
    }

    private List<Message> buildChatMessages(Long sessionId, Agent agent, Long userId) {
        List<Message> messages = new ArrayList<>();
        String systemPrompt = agent.getSystemPrompt();
        if ("TRIAGE".equals(agent.getAgentType()) && userId != null) {
            systemPrompt += "\n\n\u5f53\u524d\u60a3\u8005\u4fe1\u606f\uff1a\n- patientId = " + userId
                + "\n\u5728\u8c03\u7528 createAppointment \u5de5\u5177\u65f6\uff0c\u8bf7\u52a1\u5fc5\u4f7f\u7528\u4e0a\u9762\u7684 patientId\u3002";
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
