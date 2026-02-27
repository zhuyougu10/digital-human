package com.medical.ai.service;

import com.medical.ai.domain.vo.ConversationSummaryVO;

public interface SummaryService {
    void generateSummary(Long sessionId, Long appointmentId);

    ConversationSummaryVO getSummaryBySession(Long sessionId);

    ConversationSummaryVO getSummaryByAppointment(Long appointmentId);
}

