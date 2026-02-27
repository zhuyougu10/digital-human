package com.medical.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.ai.agent.Agent;
import com.medical.ai.agent.AgentFactory;
import com.medical.ai.domain.entity.ChatMessage;
import com.medical.ai.domain.entity.ChatSession;
import com.medical.ai.domain.entity.ConversationSummary;
import com.medical.ai.domain.vo.ConversationSummaryVO;
import com.medical.ai.mapper.ChatMessageMapper;
import com.medical.ai.mapper.ChatSessionMapper;
import com.medical.ai.mapper.ConversationSummaryMapper;
import com.medical.ai.service.SummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryServiceImpl implements SummaryService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final ConversationSummaryMapper summaryMapper;
    private final AgentFactory agentFactory;
    private final OpenAiChatModel chatModel;
    private final ObjectMapper objectMapper;

    @Async("summaryExecutor")
    @Override
    public void generateSummary(Long sessionId, Long appointmentId) {
        try {
            ConversationSummary existing = summaryMapper.selectOne(
                new LambdaQueryWrapper<ConversationSummary>()
                    .eq(ConversationSummary::getSessionId, sessionId)
            );
            if (existing != null) {
                log.info("Summary already exists for session {}", sessionId);
                return;
            }

            ChatSession session = sessionMapper.selectById(sessionId);
            if (session == null) {
                return;
            }

            List<ChatMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getSessionId, sessionId)
                    .orderByAsc(ChatMessage::getCreateTime)
            );
            if (messages.isEmpty()) {
                return;
            }

            String conversationText = messages.stream()
                .filter(m -> "user".equals(m.getRole()) || "assistant".equals(m.getRole()))
                .map(m -> m.getRole() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));

            Agent summaryAgent = agentFactory.getAgent("SUMMARY");
            List<Message> chatMessages = new ArrayList<>();
            chatMessages.add(new SystemMessage(summaryAgent.getSystemPrompt()));
            chatMessages.add(new UserMessage(conversationText));

            Prompt prompt = new Prompt(chatMessages);
            String response = chatModel.call(prompt).getResult().getOutput().getContent();
            if (response == null) {
                response = "";
            }

            ConversationSummary summary = new ConversationSummary();
            summary.setSessionId(sessionId);
            summary.setUserId(session.getUserId());
            summary.setAppointmentId(appointmentId);
            summary.setFullSummary(response);

            try {
                JsonNode json = objectMapper.readTree(response);
                summary.setChiefComplaint(getJsonField(json, "chiefComplaint"));
                summary.setSymptoms(getJsonField(json, "symptoms"));
                summary.setDuration(getJsonField(json, "duration"));
                summary.setSeverity(getJsonField(json, "severity"));
                summary.setMedicalHistory(getJsonField(json, "medicalHistory"));
                summary.setAiAssessment(getJsonField(json, "aiAssessment"));
            } catch (Exception e) {
                log.warn("Failed to parse summary JSON, saving raw text: {}", e.getMessage());
            }

            summaryMapper.insert(summary);

            session.setStatus(2);
            sessionMapper.updateById(session);
            log.info("Summary generated for session {}", sessionId);
        } catch (Exception e) {
            log.error("Failed to generate summary for session {}: {}", sessionId, e.getMessage(), e);
        }
    }

    @Override
    public ConversationSummaryVO getSummaryBySession(Long sessionId) {
        ConversationSummary summary = summaryMapper.selectOne(
            new LambdaQueryWrapper<ConversationSummary>()
                .eq(ConversationSummary::getSessionId, sessionId)
        );
        return summary != null ? toVO(summary) : null;
    }

    @Override
    public ConversationSummaryVO getSummaryByAppointment(Long appointmentId) {
        ConversationSummary summary = summaryMapper.selectOne(
            new LambdaQueryWrapper<ConversationSummary>()
                .eq(ConversationSummary::getAppointmentId, appointmentId)
        );
        return summary != null ? toVO(summary) : null;
    }

    private String getJsonField(JsonNode json, String field) {
        JsonNode node = json.get(field);
        return node != null ? node.asText() : null;
    }

    private ConversationSummaryVO toVO(ConversationSummary summary) {
        ConversationSummaryVO vo = new ConversationSummaryVO();
        vo.setId(summary.getId());
        vo.setSessionId(summary.getSessionId());
        vo.setUserId(summary.getUserId());
        vo.setAppointmentId(summary.getAppointmentId());
        vo.setChiefComplaint(summary.getChiefComplaint());
        vo.setSymptoms(summary.getSymptoms());
        vo.setDuration(summary.getDuration());
        vo.setSeverity(summary.getSeverity());
        vo.setMedicalHistory(summary.getMedicalHistory());
        vo.setAiAssessment(summary.getAiAssessment());
        vo.setFullSummary(summary.getFullSummary());
        vo.setCreateTime(summary.getCreateTime());
        return vo;
    }
}

