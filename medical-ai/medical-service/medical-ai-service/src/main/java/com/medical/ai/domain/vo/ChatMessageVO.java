package com.medical.ai.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ChatMessageVO {
    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private String toolCallId;
    private String toolName;
    private Map<String, Object> metadata;
    private String ttsUrl;
    private LocalDateTime createTime;
}
