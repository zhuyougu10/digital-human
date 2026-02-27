package com.medical.ai.agent.tool;

import lombok.Data;

@Data
public class GetSlotsRequest {
    private Long doctorId;
    private String date; // yyyy-MM-dd
}
