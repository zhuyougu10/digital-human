package com.medical.gateway.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SentinelGatewayConfigTest {

    private final SentinelGatewayConfig config = new SentinelGatewayConfig();

    @Test
    void shouldDefineProtectedGatewayApis() {
        Set<ApiDefinition> definitions = config.gatewayApiDefinitions();

        assertEquals(4, definitions.size());
        Map<String, ApiDefinition> definitionsByName = definitions.stream()
                .collect(Collectors.toMap(ApiDefinition::getApiName, definition -> definition));

        assertTrue(definitionsByName.containsKey(SentinelGatewayConfig.RESOURCE_AUTH_LOGIN));
        assertTrue(definitionsByName.containsKey(SentinelGatewayConfig.RESOURCE_AUTH_WX_LOGIN));
        assertTrue(definitionsByName.containsKey(SentinelGatewayConfig.RESOURCE_AI_CHAT_SEND));
        assertTrue(definitionsByName.containsKey(SentinelGatewayConfig.RESOURCE_KNOWLEDGE_SEARCH));

        Set<ApiPathPredicateItem> loginPredicates = definitionsByName.get(SentinelGatewayConfig.RESOURCE_AUTH_LOGIN)
                .getPredicateItems()
                .stream()
                .map(ApiPathPredicateItem.class::cast)
                .collect(Collectors.toSet());
        assertEquals(1, loginPredicates.size());
        ApiPathPredicateItem loginPath = loginPredicates.iterator().next();
        assertEquals("/api/user/auth/login", loginPath.getPattern());
    }

    @Test
    void shouldDefineGatewayFlowRulesForProtectedApis() {
        Set<GatewayFlowRule> rules = config.gatewayFlowRules();

        assertEquals(4, rules.size());
        Map<String, GatewayFlowRule> rulesByResource = rules.stream()
                .collect(Collectors.toMap(GatewayFlowRule::getResource, rule -> rule));

        assertEquals(15.0d, rulesByResource.get(SentinelGatewayConfig.RESOURCE_AUTH_LOGIN).getCount());
        assertEquals(8.0d, rulesByResource.get(SentinelGatewayConfig.RESOURCE_AUTH_WX_LOGIN).getCount());
        assertEquals(5.0d, rulesByResource.get(SentinelGatewayConfig.RESOURCE_AI_CHAT_SEND).getCount());
        assertEquals(10.0d, rulesByResource.get(SentinelGatewayConfig.RESOURCE_KNOWLEDGE_SEARCH).getCount());
    }
}
