package com.flashsale.commonlib.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class InternalAuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                           HttpServletResponse response, Object handler) throws Exception {
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"success\":false,\"errorCode\":\"AUTH_001\",\"message\":\"Không có thông tin xác thực\"}");
            return false;
        }
        return true;
    }
}

