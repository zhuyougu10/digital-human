package com.medical.ai.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ChatRequestDTO {

    @NotNull(message = "会话ID不能为空")
    private Long sessionId;

    @NotBlank(message = "消息内容不能为空")
    private String message;

    @Pattern(regexp = "TRIAGE|ENCYCLOPEDIA", message = "不支持的会话类型")
    private String sessionType;
}
