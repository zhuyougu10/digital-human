package com.medical.gateway.filter;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.medical.gateway.GatewayApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        classes = GatewayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.discovery.register-enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.sentinel.enabled=false"
        }
)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class AuthFilterTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldRejectInternalApiPathBeforeForwarding() {
        webTestClient.get()
                .uri("/api/user/inner/1")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void shouldNotTreatPublicTtsAudioFetchAsInternalApi() {
        HttpStatusCode status = webTestClient.get()
                .uri("/api/ai/chat/tts/demo.mp3")
                .exchange()
                .returnResult(String.class)
                .getStatus();

        assertNotEquals(HttpStatus.FORBIDDEN, status);
        assertNotEquals(HttpStatus.UNAUTHORIZED, status);
    }
}
