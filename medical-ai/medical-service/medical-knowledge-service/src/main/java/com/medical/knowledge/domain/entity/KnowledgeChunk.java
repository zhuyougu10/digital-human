package com.medical.knowledge.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("knowledge_chunk")
public class KnowledgeChunk {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long kbId;
    private Long docId;
    private Integer chunkIndex;
    private String content;
    private Integer tokenCount;
    private String milvusId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
