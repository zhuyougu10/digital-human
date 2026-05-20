package com.medical.ai.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TriageAgentTest {

    @Test
    void triageAgent_shouldDelegateAppointmentFlowToDeterministicService() {
        TriageAgent triageAgent = new TriageAgent();

        assertEquals("TRIAGE", triageAgent.getAgentType());
        assertTrue(triageAgent.getToolNames().isEmpty());
        assertTrue(triageAgent.getSystemPrompt().contains("deterministic appointment flow"));
        assertTrue(triageAgent.getSystemPrompt().contains("collect appointment time"));
    }
}
