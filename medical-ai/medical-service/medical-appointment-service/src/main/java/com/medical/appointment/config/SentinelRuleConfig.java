package com.medical.appointment.config;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.medical.appointment.service.impl.AppointmentServiceImpl;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SentinelRuleConfig {

    @PostConstruct
    public void initRules() {
        FlowRuleManager.loadRules(flowRules());
        ParamFlowRuleManager.loadRules(paramFlowRules());
    }

    List<FlowRule> flowRules() {
        FlowRule createRule = new FlowRule(AppointmentServiceImpl.CREATE_RESOURCE);
        createRule.setCount(10);
        return List.of(createRule);
    }

    List<ParamFlowRule> paramFlowRules() {
        ParamFlowRule slotRule = new ParamFlowRule(AppointmentServiceImpl.CREATE_RESOURCE);
        slotRule.setParamIdx(0);
        slotRule.setCount(2);
        return List.of(slotRule);
    }
}
