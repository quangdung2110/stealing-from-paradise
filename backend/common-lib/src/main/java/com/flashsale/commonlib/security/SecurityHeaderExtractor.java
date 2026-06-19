package com.flashsale.commonlib.security;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.web.server.ServerWebExchange;

/**
 * Security Header Extractor - Extract user info từ gateway headers
 * Sử dụng cho các WebFlux services
 *
 * Quy trình:
 * - API Gateway chỉ validate token, forward token qua X-Access-Token header
 * - Microservices decode token, extract user info vào security headers
 * - Các client có thể trực tiếp sử dụng security headers (X-User-Id, X-User-Role, etc.)
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SecurityHeaderExtractor {

    // Token headers
    public static final String X_ACCESS_TOKEN = "X-Access-Token";

    // Security headers (được set bởi JwtTokenDecoderWebFilter hoặc JwtTokenDecoderFilter)
    public static final String X_USER_ID = "X-User-Id";
    public static final String X_USER_EMAIL = "X-User-Email";
    public static final String X_USER_ROLE = "X-User-Role";
    public static final String X_TOKEN_JTI = "X-Token-Jti";

    /**
     * Extract access token từ headers (được forward từ API Gateway)
     */
    public static String extractAccessToken(ServerWebExchange exchange) {
        return exchange.getRequest().getHeaders().getFirst(X_ACCESS_TOKEN);
    }

    /**
     * Extract userId từ headers (đã được decode)
     */
    public static String extractUserId(ServerWebExchange exchange) {
        return exchange.getRequest().getHeaders().getFirst(X_USER_ID);
    }

    /**
     * Extract email từ headers (đã được decode)
     */
    public static String extractEmail(ServerWebExchange exchange) {
        return exchange.getRequest().getHeaders().getFirst(X_USER_EMAIL);
    }

    /**
     * Extract role từ headers (đã được decode)
     */
    public static String extractRole(ServerWebExchange exchange) {
        return exchange.getRequest().getHeaders().getFirst(X_USER_ROLE);
    }

    /**
     * Extract JTI (token ID) từ headers (đã được decode)
     */
    public static String extractJti(ServerWebExchange exchange) {
        return exchange.getRequest().getHeaders().getFirst(X_TOKEN_JTI);
    }

    /**
     * Check if user is authenticated
     */
    public static boolean isAuthenticated(ServerWebExchange exchange) {
        return extractUserId(exchange) != null;
    }
}

