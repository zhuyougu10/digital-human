package com.medical.ai.service;

import com.medical.ai.domain.vo.ConversationSummaryVO;
import java.util.List;

public interface SummaryService {
    void generateSummary(Long sessionId, Long appointmentId);

    ConversationSummaryVO getSummaryBySession(Long sessionId, Long userId);

    ConversationSummaryVO getSummaryByAppointment(Long appointmentId, Long userId, List<String> roles);
}
