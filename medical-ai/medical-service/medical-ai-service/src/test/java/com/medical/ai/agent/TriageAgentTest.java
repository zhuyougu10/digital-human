package com.medical.ai.agent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TriageAgentTest {

    private final TriageAgent triageAgent = new TriageAgent();

    @Test
    void getSystemPrompt_shouldIncludeServerTimeAndAllowedDepartments() {
        String prompt = triageAgent.getSystemPrompt();

        assertTrue(prompt.contains("当前服务器时间："));
        assertTrue(prompt.contains("时区："));
        assertTrue(prompt.contains("内科、外科、神经内科、儿科、妇产科、眼科、耳鼻喉科、皮肤科、中医科、口腔科"));
        assertTrue(prompt.contains("严禁推荐系统中不存在的科室名称"));
        assertTrue(prompt.contains("必须以上面的服务器时间为准"));
    }
}
