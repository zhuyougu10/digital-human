package com.medical.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.exception.SentinelGatewayBlockExceptionHandler;
import com.medical.gateway.handler.GatewaySentinelBlockHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.result.view.ViewResolver;

@Configuration
public class SentinelGatewayConfig {

    public static final String RESOURCE_AUTH_LOGIN = "gw:auth:login";
    public static final String RESOURCE_AUTH_WX_LOGIN = "gw:auth:wxLogin";
    public static final String RESOURCE_AI_CHAT_SEND = "gw:ai:chatSend";
    public static final String RESOURCE_KNOWLEDGE_SEARCH = "gw:knowledge:search";

    @PostConstruct
    public void initGatewayRules() {
        GatewayApiDefinitionManager.loadApiDefinitions(gatewayApiDefinitions());
        GatewayRuleManager.loadRules(gatewayFlowRules());
    }

    @Bean
    public GlobalFilter sentinelGatewayFilter() {
        return new SentinelGatewayFilter();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SentinelGatewayBlockExceptionHandler sentinelGatewayBlockExceptionHandler(
            ObjectProvider<List<ViewResolver>> viewResolversProvider,
            ServerCodecConfigurer serverCodecConfigurer,
            ObjectMapper objectMapper) {
        GatewayCallbackManager.setBlockHandler(new GatewaySentinelBlockHandler(objectMapper));
        return new SentinelGatewayBlockExceptionHandler(
                viewResolversProvider.getIfAvailable(List::of),
                serverCodecConfigurer);
    }

    Set<ApiDefinition> gatewayApiDefinitions() {
        Set<ApiDefinition> definitions = new HashSet<>();
        definitions.add(new ApiDefinition(RESOURCE_AUTH_LOGIN)
                .setPredicateItems(Set.of(new ApiPathPredicateItem().setPattern("/api/user/auth/login"))));
        definitions.add(new ApiDefinition(RESOURCE_AUTH_WX_LOGIN)
                .setPredicateItems(Set.of(new ApiPathPredicateItem().setPattern("/api/user/auth/wx-login"))));
        definitions.add(new ApiDefinition(RESOURCE_AI_CHAT_SEND)
                .setPredicateItems(Set.of(new ApiPathPredicateItem().setPattern("/api/ai/chat/send"))));
        definitions.add(new ApiDefinition(RESOURCE_KNOWLEDGE_SEARCH)
                .setPredicateItems(Set.of(new ApiPathPredicateItem().setPattern("/api/knowledge/kb/search"))));
        return definitions;
    }

    Set<GatewayFlowRule> gatewayFlowRules() {
        return new HashSet<>(Arrays.asList(
                new GatewayFlowRule(RESOURCE_AUTH_LOGIN).setCount(15).setIntervalSec(1),
                new GatewayFlowRule(RESOURCE_AUTH_WX_LOGIN).setCount(8).setIntervalSec(1),
                new GatewayFlowRule(RESOURCE_AI_CHAT_SEND).setCount(5).setIntervalSec(1),
                new GatewayFlowRule(RESOURCE_KNOWLEDGE_SEARCH).setCount(10).setIntervalSec(1)
        ));
    }
}
