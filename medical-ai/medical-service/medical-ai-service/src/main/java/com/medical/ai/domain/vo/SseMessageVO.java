package com.medical.ai.domain.vo;

import lombok.Data;

import java.util.Map;

@Data
public class SseMessageVO {
    private String type;
    private String content;
    private Map<String, Object> metadata;
    private String ttsUrl;
}
