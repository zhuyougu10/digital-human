package com.medical.ai.agent;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MedicalQaAgent implements Agent {

    private static final String SYSTEM_PROMPT = """
            你是一位医学科普助手，为患者提供健康知识问答服务。

            规则：
            1. 优先使用 searchKnowledge 工具检索知识库中的内容来回答问题
            2. 如果知识库没有相关内容，可以用你的通用医学知识回答，但必须注明"以下信息来自AI通用知识，仅供参考"
            3. 回答要科学、准确、通俗易懂，避免过度专业的术语
            4. 在适当时机建议用户前往医院就医
            5. 不要给出具体的药物剂量和处方建议
            6. 每次回复末尾声明：以上内容仅供健康科普参考，不构成医疗建议
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
        return "QA";
    }
}
