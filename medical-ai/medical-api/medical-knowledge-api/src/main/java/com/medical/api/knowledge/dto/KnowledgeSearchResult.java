package com.medical.api.knowledge.dto;

import lombok.Data;

@Data
public class KnowledgeSearchResult {
    private String content;
    private Double score;
    private String documentName;
    private Integer chunkIndex;
}
