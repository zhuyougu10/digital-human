package com.medical.ai.agent;

import java.util.List;

public interface Agent {
    String getSystemPrompt();
    List<String> getToolNames();
    String getAgentType();
}
