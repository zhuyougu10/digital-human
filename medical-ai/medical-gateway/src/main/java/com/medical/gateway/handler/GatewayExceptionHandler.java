package com.medical.gateway.handler;

import cn.dev33.satoken.exception.SaTokenException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    public GatewayExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        int code;
        String msg;
        if (ex instanceof SaTokenException) {
            code = HttpStatus.UNAUTHORIZED.value();
            msg = ex.getMessage() == null ? "Unauthorized" : ex.getMessage();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
        } else if (ex instanceof ResponseStatusException statusEx) {
            code = statusEx.getStatusCode().value();
            msg = statusEx.getReason() == null ? statusEx.getMessage() : statusEx.getReason();
            response.setStatusCode(HttpStatus.valueOf(code));
        } else {
            code = HttpStatus.INTERNAL_SERVER_ERROR.value();
            msg = ex.getMessage() == null ? "Internal Server Error" : ex.getMessage();
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("msg", msg);
        body.put("data", null);

        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            json = "{\"code\":500,\"msg\":\"Internal Server Error\",\"data\":null}";
        }

        return response.writeWith(Mono.just(
                response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8))
        ));
    }
}

@Configuration
class GatewayExceptionHandlerConfig {

    @Bean
    @Order(-1)
    public ErrorWebExceptionHandler gatewayErrorWebExceptionHandler(ObjectMapper objectMapper) {
        return new GatewayExceptionHandler(objectMapper);
    }
}
