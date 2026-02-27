package com.medical.api.knowledge.dto;

import lombok.Data;

@Data
public class KnowledgeSearchRequest {
    private Long kbId;
    private String query;
    private Integer topK = 5;
}
