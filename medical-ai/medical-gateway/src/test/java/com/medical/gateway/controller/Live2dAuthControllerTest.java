package com.medical.gateway.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class Live2dAuthControllerTest {

    private final Live2dAuthController controller = new Live2dAuthController();

    @Test
    void shouldReturnNoContentForAuthorizedAuthProbe() {
        assertEquals(HttpStatus.NO_CONTENT, controller.authCheck().getStatusCode());
    }
}
