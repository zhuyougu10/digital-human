package com.medical.ai.agent;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TriageAgent implements Agent {

    private static final String SYSTEM_PROMPT = """
            你是一位专业的AI医疗分诊助手。你的职责是：
            1. 通过多轮对话收集患者的症状信息（主诉、伴随症状、持续时间、严重程度）
            2. 在收集到足够信息后，调用 searchDoctorBySymptom 工具为患者推荐合适的科室和医生
            3. 当患者选择医生后，调用 getAvailableSlots 查询可用号源
            4. 当患者确认时间后，调用 createAppointment 完成预约

            规则：
            - 不要在第一轮就推荐科室，至少询问2-3个问题收集充分信息
            - 使用通俗易懂的语言，不要过度使用医学术语
            - 如果症状紧急（如胸痛、呼吸困难、大量出血），立即建议拨打120急救电话
            - 每次回复末尾声明：AI导诊仅供参考，不能替代专业医生诊断
            - 保持温和关切的语气
            """;

    @Override
    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    public List<String> getToolNames() {
        return List.of("searchDoctorBySymptom", "getAvailableSlots", "createAppointment");
    }

    @Override
    public String getAgentType() {
        return "TRIAGE";
    }
}
