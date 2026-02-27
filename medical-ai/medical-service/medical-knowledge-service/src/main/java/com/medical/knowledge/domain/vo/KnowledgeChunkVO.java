package com.medical.knowledge.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class KnowledgeChunkVO {
    private Long id;
    private Long kbId;
    private Long docId;
    private Integer chunkIndex;
    private String content;
    private Integer tokenCount;
    private String milvusId;
    private LocalDateTime createTime;
}
