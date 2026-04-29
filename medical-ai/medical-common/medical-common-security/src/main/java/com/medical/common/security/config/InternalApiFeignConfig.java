package com.medical.common.security.config;

import feign.RequestInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(RequestInterceptor.class)
@EnableConfigurationProperties(InternalApiAuthProperties.class)
public class InternalApiFeignConfig {

    @Bean
    public RequestInterceptor internalApiRequestInterceptor(InternalApiAuthProperties properties) {
        return requestTemplate -> requestTemplate.header(InternalApiAuthConstants.HEADER_NAME, properties.getSecret());
    }
}
