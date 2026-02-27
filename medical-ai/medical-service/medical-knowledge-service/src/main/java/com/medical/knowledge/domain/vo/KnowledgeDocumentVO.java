package com.medical.knowledge.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class KnowledgeDocumentVO {
    private Long id;
    private Long kbId;
    private String fileName;
    private String filePath;
    private String fileType;
    private Long fileSize;
    private Integer chunkCount;
    private Integer parseStatus;
    private String errorMsg;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
