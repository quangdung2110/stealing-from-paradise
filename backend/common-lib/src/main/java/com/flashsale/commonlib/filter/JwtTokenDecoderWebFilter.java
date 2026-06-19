package com.flashsale.commonlib.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import com.flashsale.commonlib.security.UserDetailsImpl;
import reactor.core.publisher.Mono;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

/**
 * JWT Token Decoder Web Filter - Đặt decoded user info vào SecurityContext tại các WebFlux microservices
 *
 * ✅ Được dùng ở các WebFlux service (notification-service, etc.)
 * ✅ Chỉ hoạt động khi API Gateway forward decoded user info qua headers
 *
 * Quy trình:
 * 1. Kiểm tra security headers từ API Gateway (X-User-Id, X-User-Email, X-User-Role, X-Token-Jti)
 * 2. Tạo UserDetails từ headers
 * 3. Đặt vào Reactive SecurityContext
 * 4. Business logic có thể dùng @PreAuthorize, ReactiveSecurityContextHolder, v.v.
 *
 * @since 1.0.0
 * @author Microservice Team
 */
@Component
@Slf4j
public class JwtTokenDecoderWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Đọc decoded user info từ headers (đã được decode ở API Gateway)
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        String email = exchange.getRequest().getHeaders().getFirst("X-User-Email");
        String role = exchange.getRequest().getHeaders().getFirst("X-User-Role");
        String jti = exchange.getRequest().getHeaders().getFirst("X-Token-Jti");

        // Nếu không có user info, tiếp tục mà không set SecurityContext
        if (userId == null || userId.isBlank()) {
            return chain.filter(exchange);
        }

        try {
            // Tạo UserDetails từ headers
            UserDetailsImpl userDetails = UserDetailsImpl.builder()
                .id(Long.parseLong(userId))
                .username(userId)
                .email(email)
                .role(role)
                .enabled(true)
                .build();

            // Tạo authentication token
            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

            log.debug("[JwtTokenDecoder] Set SecurityContext - userId: {}, email: {}, role: {}", userId, email, role);

            // Đặt vào ReactiveSecurityContext và tiếp tục
            return chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));

        } catch (Exception e) {
            log.warn("[JwtTokenDecoder] Failed to set SecurityContext: {}", e.getMessage());
            // Nếu lỗi, vẫn tiếp tục
            return chain.filter(exchange);
        }
    }
}





