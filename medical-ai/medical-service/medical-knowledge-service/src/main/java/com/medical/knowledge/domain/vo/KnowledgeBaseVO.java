package com.medical.knowledge.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class KnowledgeBaseVO {
    private Long id;
    private String name;
    private String description;
    private String collectionName;
    private Integer documentCount;
    private Integer chunkCount;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
