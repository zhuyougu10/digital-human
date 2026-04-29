package com.medical.ai.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateSessionDTO {

    @NotBlank(message = "会话类型不能为空")
    @Pattern(regexp = "TRIAGE|ENCYCLOPEDIA", message = "不支持的会话类型")
    private String sessionType;
}
