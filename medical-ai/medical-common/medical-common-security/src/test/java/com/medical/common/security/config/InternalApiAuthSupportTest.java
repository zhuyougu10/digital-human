package com.medical.common.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalApiAuthSupportTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(InternalApiFeignConfig.class);

    @Test
    void shouldRejectInternalRequestWithoutTrustedHeader() {
        InternalApiAuthProperties properties = new InternalApiAuthProperties();
        properties.setSecret("shared-secret");
        InternalApiAuthInterceptor interceptor = new InternalApiAuthInterceptor(properties);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, response, new Object()));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
    }

    @Test
    void shouldAllowInternalRequestWithTrustedHeader() {
        InternalApiAuthProperties properties = new InternalApiAuthProperties();
        properties.setSecret("shared-secret");
        InternalApiAuthInterceptor interceptor = new InternalApiAuthInterceptor(properties);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(InternalApiAuthConstants.HEADER_NAME, "shared-secret");

        assertDoesNotThrow(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void feignInterceptorShouldPropagateTrustedHeader() {
        InternalApiAuthProperties properties = new InternalApiAuthProperties();
        properties.setSecret("shared-secret");
        InternalApiFeignConfig config = new InternalApiFeignConfig();
        RequestTemplate template = new RequestTemplate();

        config.internalApiRequestInterceptor(properties).apply(template);

        assertEquals("shared-secret", template.headers().get(InternalApiAuthConstants.HEADER_NAME).iterator().next());
    }

    @Test
    void shouldFailContextStartupWhenSecretMissing() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("security.internal-api.secret must be configured");
        });
    }

    @Test
    void shouldRegisterFeignInterceptorWhenSecretConfigured() {
        contextRunner
                .withPropertyValues("security.internal-api.secret=shared-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RequestInterceptor.class);
                });
    }
}
