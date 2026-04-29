package com.medical.common.security.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(InternalApiAuthProperties.class)
public class SaTokenConfig implements WebMvcConfigurer {

    private final InternalApiAuthInterceptor internalApiAuthInterceptor;

    public SaTokenConfig(InternalApiAuthProperties internalApiAuthProperties) {
        this.internalApiAuthInterceptor = new InternalApiAuthInterceptor(internalApiAuthProperties);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Sa-Token 拦截器，打开注解式鉴权
        registry.addInterceptor(new SaInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",
                        "/auth/wx-login",
                        "/auth/register",
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/actuator/**"
                );

        registry.addInterceptor(internalApiAuthInterceptor)
                .addPathPatterns("/**/inner/**");
    }
}
