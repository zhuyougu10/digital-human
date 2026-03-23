package com.medical.doctor.config;

import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.medical.doctor.service.impl.ScheduleServiceImpl;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SentinelRuleConfig {

    @PostConstruct
    public void initRules() {
        ParamFlowRuleManager.loadRules(paramFlowRules());
    }

    List<ParamFlowRule> paramFlowRules() {
        ParamFlowRule scheduleRule = new ParamFlowRule(ScheduleServiceImpl.SLOTS_RESOURCE);
        scheduleRule.setParamIdx(0);
        scheduleRule.setCount(3);
        return List.of(scheduleRule);
    }
}
