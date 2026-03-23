package com.medical.gateway.handler;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import org.springframework.web.server.ServerWebExchange;

public class GatewaySentinelBlockHandler implements BlockRequestHandler {

    private final ObjectMapper objectMapper;

    public GatewaySentinelBlockHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<ServerResponse> handleRequest(ServerWebExchange exchange, Throwable ex) {
        String msg = buildMessage(exchange.getRequest().getPath().value());
        return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(toJson(msg)));
    }

    static Map<String, Object> buildBody(String msg) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 500);
        body.put("msg", msg);
        body.put("data", null);
        body.put("success", Boolean.FALSE);
        return body;
    }

    private String buildMessage(String path) {
        if ("/api/ai/chat/send".equals(path)) {
            return "当前咨询人数较多，请稍后再试";
        }
        if ("/api/knowledge/kb/search".equals(path)) {
            return "知识检索服务繁忙，请稍后重试";
        }
        if ("/api/user/auth/wx-login".equals(path)) {
            return "微信登录服务繁忙，请稍后重试";
        }
        if ("/api/user/auth/login".equals(path)) {
            return "登录请求过于频繁，请稍后再试";
        }
        return "系统繁忙，请稍后重试";
    }

    private String toJson(String msg) {
        try {
            return objectMapper.writeValueAsString(buildBody(msg));
        } catch (JsonProcessingException e) {
            return "{\"code\":500,\"msg\":\"系统繁忙，请稍后重试\",\"data\":null,\"success\":false}";
        }
    }
}
