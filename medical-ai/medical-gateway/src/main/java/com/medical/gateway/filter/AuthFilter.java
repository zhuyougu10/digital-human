package com.medical.gateway.filter;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthFilter {

    @Bean
    public SaReactorFilter saReactorFilter() {
        return new SaReactorFilter()
                .addInclude("/**")
                .addExclude(
                        "/api/user/auth/login",
                        "/api/user/auth/register",
                        "/api/user/auth/wx-login",
                        "/api/*/doc.html",
                        "/api/*/v3/api-docs/**",
                        "/api/*/webjars/**",
                        "/doc.html",
                        "/v3/api-docs/**",
                        "/webjars/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html"
                )
                .setBeforeAuth(obj -> {
                    String method = SaHolder.getRequest().getMethod();
                    // CORS 预检请求不会携带 token，需要放行 OPTIONS，避免浏览器因 401 且无跨域头而拦截实际请求。
                    if ("OPTIONS".equalsIgnoreCase(method)) {
                        SaRouter.stop();
                    }
                })
                .setAuth(obj -> SaRouter.match("/**", StpUtil::checkLogin))
                .setError(e -> "{\"code\":401,\"msg\":\"" + e.getMessage() + "\",\"data\":null}");
    }
}
