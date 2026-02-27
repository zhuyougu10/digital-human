# 08 - Spring Cloud Gateway 网关

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现 Gateway 鉴权过滤器、白名单路由、SSE 长连接支持、请求日志。

**Architecture:** Gateway 基于 Sa-Token Reactor 实现统一鉴权。白名单路由（登录/注册）不鉴权。SSE 路由需要特殊超时配置。

**Tech Stack:** Spring Cloud Gateway, Sa-Token Reactor, Redis

**前置依赖:** `01-project-init.md` + `02-common-modules.md` 完成

---

## Task 1: Sa-Token Gateway 鉴权过滤器

**Files:**
- Create: `com/medical/gateway/filter/AuthFilter.java`

```java
package com.medical.gateway.filter;

import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthFilter {

    @Bean
    public SaReactorFilter getSaReactorFilter() {
        return new SaReactorFilter()
                .addInclude("/**")
                .addExclude(
                        "/api/user/auth/login",
                        "/api/user/auth/register",
                        "/api/user/auth/wx-login",
                        "/api/*/doc.html",
                        "/api/*/v3/api-docs/**",
                        "/api/*/webjars/**"
                )
                .setAuth(obj -> {
                    SaRouter.match("/**", StpUtil::checkLogin);
                })
                .setError(e -> {
                    return "{\"code\":401,\"msg\":\"未登录或登录已过期\",\"data\":null}";
                });
    }
}
```

---

## Task 2: SSE 路由超时配置

在 application.yml 中针对 ai-service 的 SSE 路由设置较长超时：

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: ai-service-sse
          uri: lb://medical-ai-service
          predicates:
            - Path=/api/ai/chat/send
          filters:
            - StripPrefix=2
          metadata:
            response-timeout: 120000  # SSE 120秒超时
            connect-timeout: 5000
```

---

## Task 3: 全局请求日志过滤器

**Files:**
- Create: `com/medical/gateway/filter/RequestLogFilter.java`

简单记录 method + path + 耗时，方便调试。

---

## Task 4: 全局异常处理

**Files:**
- Create: `com/medical/gateway/handler/GatewayExceptionHandler.java`

处理路由不存在、服务不可用等异常，返回统一 JSON 格式。

---

## Task 5: 编译验证 + Commit

```bash
git add .
git commit -m "feat(gateway): implement Sa-Token auth filter, SSE timeout, request logging"
```

---

## 检查清单

- [ ] Sa-Token Reactor 鉴权过滤器
- [ ] 白名单路由（登录/注册/文档）
- [ ] SSE 路由超时 120s
- [ ] 请求日志
- [ ] Gateway 全局异常 JSON 响应
- [ ] 编译通过
