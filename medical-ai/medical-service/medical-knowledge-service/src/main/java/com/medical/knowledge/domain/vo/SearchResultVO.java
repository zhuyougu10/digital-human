package com.medical.knowledge.domain.vo;

import lombok.Data;

@Data
public class SearchResultVO {
    private String content;
    private Double score;
    private String docName;
    private Integer chunkIndex;
}
