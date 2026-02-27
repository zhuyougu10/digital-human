package com.medical.ai.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatSessionVO {
    private Long id;
    private Long userId;
    private String sessionType;
    private String title;
    private String agentType;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
