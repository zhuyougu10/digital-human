package com.medical.ai.agent.tool;

import lombok.Data;

@Data
public class KnowledgeSearchRequest {
    private String query;
    private Integer topK = 5;
}
