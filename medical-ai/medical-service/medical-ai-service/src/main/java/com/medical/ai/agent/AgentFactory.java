package com.medical.ai.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentFactory {

    private final TriageAgent triageAgent;
    private final MedicalQaAgent medicalQaAgent;
    private final SummaryAgent summaryAgent;
    private final EncyclopediaAgent encyclopediaAgent;

    public Agent getAgent(String agentType) {
        return switch (agentType) {
            case "TRIAGE" -> triageAgent;
            case "QA" -> medicalQaAgent;
            case "SUMMARY" -> summaryAgent;
            case "ENCYCLOPEDIA" -> encyclopediaAgent;
            default -> medicalQaAgent;
        };
    }
}
