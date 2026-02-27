package com.medical.ai.agent.tool;

import lombok.Data;

@Data
public class SearchDoctorRequest {
    private String keywords; // 逗号分隔的症状关键词
}
