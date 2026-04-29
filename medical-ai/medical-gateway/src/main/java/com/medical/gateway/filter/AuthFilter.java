package com.medical.gateway.filter;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthFilter {

    private static final String INTERNAL_API_PATH_REGEX = "^/api/[^/]+/inner(?:/.*)?$";
    private static final String PUBLIC_TTS_AUDIO_PATH_REGEX = "^/api/ai/chat/tts/[^/]+$";

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
                    String path = SaHolder.getRequest().getRequestPath();
                    // CORS 预检请求不会携带 token，需要放行 OPTIONS，避免浏览器因 401 且无跨域头而拦截实际请求。
                    if ("OPTIONS".equalsIgnoreCase(method)) {
                        SaRouter.stop();
                    }
                    if (path != null && path.matches(INTERNAL_API_PATH_REGEX)) {
                        throw new InternalApiAccessDeniedException("内部接口禁止通过网关访问");
                    }
                })
                .setAuth(obj -> SaRouter.match("/**", r -> {
                    String method = SaHolder.getRequest().getMethod();
                    String path = SaHolder.getRequest().getRequestPath();
                    if ("GET".equalsIgnoreCase(method) && path != null && path.matches(PUBLIC_TTS_AUDIO_PATH_REGEX)) {
                        return;
                    }
                    StpUtil.checkLogin();
                }))
                .setError(e -> {
                    if (e instanceof InternalApiAccessDeniedException) {
                        SaHolder.getResponse().setStatus(403);
                        return "{\"code\":403,\"msg\":\"" + e.getMessage() + "\",\"data\":null}";
                    }
                    SaHolder.getResponse().setStatus(401);
                    return "{\"code\":401,\"msg\":\"" + e.getMessage() + "\",\"data\":null}";
                });
    }
}
