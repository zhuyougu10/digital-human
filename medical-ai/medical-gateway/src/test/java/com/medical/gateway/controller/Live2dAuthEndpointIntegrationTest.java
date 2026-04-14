package com.medical.gateway.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.sentinel.enabled=false"
        }
)
@AutoConfigureWebTestClient
class Live2dAuthEndpointIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldReturnUnauthorizedStatusWhenTokenMissing() {
        webTestClient.get()
                .uri("/internal/live2d/auth-check")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
