package com.medical.ai.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TriageAgentTest {

    @Test
    void triageAgent_shouldUseToolsAndNaturalSummaryDrivenPrompt() {
        TriageAgent triageAgent = new TriageAgent();

        assertEquals("TRIAGE", triageAgent.getAgentType());
        assertEquals(4, triageAgent.getToolNames().size());
        assertTrue(triageAgent.getToolNames().contains("searchKnowledge"));
        assertTrue(triageAgent.getToolNames().contains("searchDoctorBySymptom"));
        assertTrue(triageAgent.getToolNames().contains("getAvailableSlots"));
        assertTrue(triageAgent.getToolNames().contains("createAppointment"));
        assertTrue(triageAgent.getSystemPrompt().contains("当前结构化导诊摘要"));
        assertTrue(triageAgent.getSystemPrompt().contains("检索知识库"));
        assertTrue(triageAgent.getSystemPrompt().contains("像真人一样"));
        assertFalse(triageAgent.getSystemPrompt().contains("deterministic appointment flow"));
    }
}
