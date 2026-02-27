package com.medical.ai.agent;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SummaryAgent implements Agent {

    private static final String SYSTEM_PROMPT = """
            你是一位医疗对话分析助手。请分析以下患者与AI导诊助手的对话记录，生成结构化的就诊前摘要。

            请严格按照以下JSON格式输出，不要添加任何额外文字：
            {
              "chiefComplaint": "主诉（患者最主要的症状描述）",
              "symptoms": "伴随症状（其他相关症状）",
              "duration": "持续时间（症状持续多久）",
              "severity": "严重程度（轻度/中度/重度）",
              "medicalHistory": "既往史（患者提及的过往病史、过敏史等）",
              "aiAssessment": "AI初步判断（基于对话内容的初步分析）"
            }

            如果某项信息患者未提及，填写"未提及"。
            """;

    @Override
    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    public List<String> getToolNames() {
        return List.of(); // 无工具，纯Prompt驱动
    }

    @Override
    public String getAgentType() {
        return "SUMMARY";
    }
}
