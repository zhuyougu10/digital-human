package com.medical.knowledge.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.medical.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_document")
public class KnowledgeDocument extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long kbId;
    private String fileName;
    private String filePath;
    private String fileType;
    private Long fileSize;
    private Integer chunkCount;
    private Integer parseStatus;
    private String errorMsg;
}
