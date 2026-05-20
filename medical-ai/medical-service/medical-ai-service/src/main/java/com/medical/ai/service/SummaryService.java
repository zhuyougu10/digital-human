package com.medical.ai.service;

import com.medical.ai.domain.vo.ConversationSummaryVO;
import com.medical.ai.domain.entity.ChatMessage;
import java.util.List;

public interface SummaryService {
    void generateSummary(Long sessionId, Long appointmentId);

    ConversationSummaryVO syncTriageSummary(Long sessionId, Long userId, Long appointmentId, List<ChatMessage> messages);

    ConversationSummaryVO getSummaryBySession(Long sessionId, Long userId);

    ConversationSummaryVO getSummaryByAppointment(Long appointmentId, Long userId, List<String> roles);
}
