package com.flashsale.apigateway.filter;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import com.flashsale.commonlib.security.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;

/**
 * DEPRECATED: Sử dụng JwtAuthWebFilter thay vì GatewayFilterFactory
 * JwtAuthWebFilter cung cấp authentication ở cấp độ WebFilter (reactive) cho cả API Gateway
 * @deprecated Use JwtAuthWebFilter instead
 */
@Deprecated(since = "1.0.0", forRemoval = true)
// @Component  ← DISABLED: Use JwtAuthWebFilter instead
@Slf4j
public class JwtAuthGatewayFilterFactory extends AbstractGatewayFilterFactory<JwtAuthGatewayFilterFactory.Config> {
    private final JwtUtils jwtUtils;
    public JwtAuthGatewayFilterFactory(JwtUtils jwtUtils) {
        super(Config.class);
        this.jwtUtils = jwtUtils;
    }
    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                if (config.isRequireAuth()) {
                    return onError(exchange, "AUTH_001", "Missing Authorization header", HttpStatus.UNAUTHORIZED);
                }
                return chain.filter(exchange);
            }
            String token = authHeader.substring(7);
            try {
                if (!jwtUtils.isTokenValid(token)) {
                    return onError(exchange, "AUTH_002", "Invalid or expired token", HttpStatus.UNAUTHORIZED);
                }
                String userId = jwtUtils.extractUserId(token);
                String email  = jwtUtils.extractEmail(token);
                String role   = jwtUtils.extractRole(token);
                String jti    = jwtUtils.extractJti(token);
                var mutated = exchange.getRequest().mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Role", role)
                    .header("X-User-Email", email)
                    .header("X-Token-Jti", jti)
                    .build();
                return chain.filter(exchange.mutate().request(mutated).build());
            } catch (Exception e) {
                return onError(exchange, "AUTH_003", "Invalid token", HttpStatus.UNAUTHORIZED);
            }
        };
    }
    private Mono<Void> onError(org.springframework.web.server.ServerWebExchange exchange, String code, String message, HttpStatus status) {
        log.warn("[JwtAuthFilter] {} — {}", code, message);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
            "{\"success\":false,\"errorCode\":\"%s\",\"message\":\"%s\",\"timestamp\":%d}",
            code, message, System.currentTimeMillis());
        var buf = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buf));
    }
    public static class Config {
        private boolean requireAuth = true;
        public boolean isRequireAuth() {
            return requireAuth;
        }
        public void setRequireAuth(boolean requireAuth) {
            this.requireAuth = requireAuth;
        }
    }
}