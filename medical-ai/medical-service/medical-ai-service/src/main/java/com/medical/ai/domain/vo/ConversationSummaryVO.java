package com.medical.ai.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationSummaryVO {
    private Long id;
    private Long sessionId;
    private Long userId;
    private Long appointmentId;
    private String chiefComplaint;
    private String symptoms;
    private String duration;
    private String severity;
    private String medicalHistory;
    private String aiAssessment;
    private String fullSummary;
    private LocalDateTime createTime;
}
