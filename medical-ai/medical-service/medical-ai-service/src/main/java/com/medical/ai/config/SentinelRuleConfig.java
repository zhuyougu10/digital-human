package com.medical.ai.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.medical.ai.service.impl.ChatServiceImpl;
import com.medical.ai.service.impl.TtsServiceImpl;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SentinelRuleConfig {

    @PostConstruct
    public void initRules() {
        FlowRuleManager.loadRules(flowRules());
        DegradeRuleManager.loadRules(degradeRules());
    }

    List<FlowRule> flowRules() {
        FlowRule chatStreamThreadRule = new FlowRule(ChatServiceImpl.CHAT_STREAM_RESOURCE);
        chatStreamThreadRule.setGrade(RuleConstant.FLOW_GRADE_THREAD);
        chatStreamThreadRule.setCount(15);

        return List.of(chatStreamThreadRule);
    }

    List<DegradeRule> degradeRules() {
        DegradeRule chatStreamRtRule = new DegradeRule(ChatServiceImpl.CHAT_STREAM_RESOURCE);
        chatStreamRtRule.setGrade(RuleConstant.DEGRADE_GRADE_RT);
        chatStreamRtRule.setCount(8000);
        chatStreamRtRule.setMinRequestAmount(5);
        chatStreamRtRule.setTimeWindow(30);

        DegradeRule ttsRtRule = new DegradeRule(TtsServiceImpl.TTS_RESOURCE);
        ttsRtRule.setGrade(RuleConstant.DEGRADE_GRADE_RT);
        ttsRtRule.setCount(5000);
        ttsRtRule.setMinRequestAmount(5);
        ttsRtRule.setTimeWindow(30);

        DegradeRule ttsExceptionRule = new DegradeRule(TtsServiceImpl.TTS_RESOURCE);
        ttsExceptionRule.setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO);
        ttsExceptionRule.setCount(0.4d);
        ttsExceptionRule.setMinRequestAmount(5);
        ttsExceptionRule.setTimeWindow(30);

        return List.of(chatStreamRtRule, ttsRtRule, ttsExceptionRule);
    }
}
