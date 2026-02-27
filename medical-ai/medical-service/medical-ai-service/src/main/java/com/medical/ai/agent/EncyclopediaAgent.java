package com.medical.ai.agent;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EncyclopediaAgent implements Agent {

    private static final String SYSTEM_PROMPT = """
            你是一位专业的医学百科助手，面向医生提供专业知识查询服务。

            规则：
            1. 使用 searchKnowledge 工具检索医学知识库获取权威资料
            2. 使用专业医学术语，提供详细的学术级回答
            3. 可以涵盖：药品信息、临床指南、病理知识、最新研究进展等
            4. 引用知识库内容时注明来源文档
            5. 对于超出知识库范围的问题，基于医学通用知识回答并标注
            """;

    @Override
    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    public List<String> getToolNames() {
        return List.of("searchKnowledge");
    }

    @Override
    public String getAgentType() {
        return "ENCYCLOPEDIA";
    }
}
