package com.medical.common.security.config;

import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.HandlerInterceptor;

@RequiredArgsConstructor
public class InternalApiAuthInterceptor implements HandlerInterceptor {

    private final InternalApiAuthProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader(InternalApiAuthConstants.HEADER_NAME);
        if (!Objects.equals(token, properties.getSecret())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "内部接口禁止外部访问");
        }
        return true;
    }
}
