package com.medical.knowledge.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.medical.knowledge.service.impl.EmbeddingServiceImpl;
import com.medical.knowledge.service.impl.KnowledgeBaseServiceImpl;
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
        FlowRule searchRule = new FlowRule(KnowledgeBaseServiceImpl.SEARCH_RESOURCE);
        searchRule.setCount(10);

        return List.of(searchRule);
    }

    List<DegradeRule> degradeRules() {
        DegradeRule searchRtRule = new DegradeRule(KnowledgeBaseServiceImpl.SEARCH_RESOURCE);
        searchRtRule.setGrade(RuleConstant.DEGRADE_GRADE_RT);
        searchRtRule.setCount(5000);
        searchRtRule.setMinRequestAmount(5);
        searchRtRule.setTimeWindow(30);

        DegradeRule embedRtRule = new DegradeRule(EmbeddingServiceImpl.EMBED_RESOURCE);
        embedRtRule.setGrade(RuleConstant.DEGRADE_GRADE_RT);
        embedRtRule.setCount(3000);
        embedRtRule.setMinRequestAmount(5);
        embedRtRule.setTimeWindow(30);

        DegradeRule embedExceptionRule = new DegradeRule(EmbeddingServiceImpl.EMBED_RESOURCE);
        embedExceptionRule.setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO);
        embedExceptionRule.setCount(0.3d);
        embedExceptionRule.setMinRequestAmount(5);
        embedExceptionRule.setTimeWindow(30);

        return List.of(searchRtRule, embedRtRule, embedExceptionRule);
    }
}
