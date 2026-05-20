package com.medical.ai.agent;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TriageAgent implements Agent {

    @Override
    public String getSystemPrompt() {
        return """
                TRIAGE is handled by the deterministic appointment flow.
                The flow order is: collect appointment time, find available doctors,
                let the patient choose a doctor, then create the appointment.
                """;
    }

    @Override
    public List<String> getToolNames() {
        return List.of();
    }

    @Override
    public String getAgentType() {
        return "TRIAGE";
    }
}
