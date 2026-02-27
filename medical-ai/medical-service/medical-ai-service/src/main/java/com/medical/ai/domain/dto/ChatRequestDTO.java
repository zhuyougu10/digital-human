package com.medical.ai.domain.dto;

import lombok.Data;

@Data
public class ChatRequestDTO {
    private Long sessionId;
    private String message;
    private String sessionType;
}
