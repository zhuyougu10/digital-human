package com.medical.knowledge.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChunkManualDTO {
    private Long kbId;
    private String title;
    @NotBlank(message = "知识内容不能为空")
    private String content;
}
