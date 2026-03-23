package com.medical.gateway.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GatewaySentinelBlockHandlerTest {

    @Test
    void shouldBuildRStyleBusyBody() {
        Map<String, Object> body = GatewaySentinelBlockHandler.buildBody("系统繁忙，请稍后重试");

        assertEquals(500, body.get("code"));
        assertEquals("系统繁忙，请稍后重试", body.get("msg"));
        assertNull(body.get("data"));
        assertEquals(Boolean.FALSE, body.get("success"));
    }
}
