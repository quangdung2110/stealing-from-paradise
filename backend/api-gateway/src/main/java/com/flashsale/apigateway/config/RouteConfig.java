package com.flashsale.apigateway.config;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import lombok.extern.slf4j.Slf4j;

/**
 * API Gateway Route Configuration
 *
 * ✅ JWT Authentication is handled by JwtAuthWebFilter (@Component)
 *    No need to inject or configure filters here.
 *
 * ✅ Route paths match /api/v1/** — the EXACT path browsers/nginx send.
 *    nginx forwards /api/v1/... unchanged to the gateway.
 *
 * ✅ stripPrefix(1) is applied on every route.
 *    It removes the first path segment (/api), so:
 *      /api/v1/products → /v1/products → product-service @RequestMapping("/v1")
 *    Without stripPrefix, services would receive /api/v1/... which does NOT
 *    match their @RequestMapping("/v1/...") handlers.
 *
 * ✅ Route ordering matters: specific routes (identity-public) must come before
 *    more general routes (identity-protected) to avoid unintended matching.
 */
@Configuration
@Slf4j
public class RouteConfig {

    @Bean
    public RouteLocator routes(RouteLocatorBuilder b) {
        log.info("[Gateway] Initializing routes...");
        return b.routes()
            // ===== Identity Service =====
            .route("identity-public", r -> r
                .path("/api/v1/auth/**", "/api/v1/users/register", "/api/v1/users/sellers/**")
                .filters(f -> f.stripPrefix(1))
                .uri("lb://identity-service"))

            .route("identity-protected", r -> r
                .path("/api/v1/users/**", "/api/v1/admin/users/**")
                .and().method(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)
                .filters(f -> f.stripPrefix(1))
                .uri("lb://identity-service"))

            // ===== Payment Service /seller/payments — must come BEFORE product /seller/** =====
            .route("seller-payments-pre", r -> r
                .path("/api/v1/seller/payments/**")
                .filters(f -> f.stripPrefix(1))
                .uri("lb://payment-service"))

            // ===== Product Service =====
            .route("product-read", r -> r
                .path("/api/v1/products/**", "/api/v1/categories/**",
                      "/api/v1/seller/products/**", "/api/v1/seller/variants/**", "/api/v1/seller/inventory/**",
                      "/api/v1/inventory/**", "/api/v1/sellers/me/products", "/api/v1/sellers/me/products/**",
                      "/api/v1/admin/products/**", "/api/v1/admin/categories/**")
                .and().method(HttpMethod.GET)
                .filters(f -> f.stripPrefix(1))
                .uri("lb://product-service"))

            .route("product-write", r -> r
                .path("/api/v1/products/**", "/api/v1/categories/**",
                      "/api/v1/seller/products/**", "/api/v1/seller/variants/**", "/api/v1/seller/inventory/**",
                      "/api/v1/inventory/**", "/api/v1/sellers/me/products", "/api/v1/sellers/me/products/**",
                      "/api/v1/admin/products/**", "/api/v1/admin/categories/**")
                .and().method(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)
                .filters(f -> f.stripPrefix(1))
                .uri("lb://product-service"))

            // ===== Cart Service (now part of Product Service — requires JWT) =====
            .route("cart", r -> r
                .path("/api/v1/cart/**")
                .filters(f -> f.stripPrefix(1))
                .uri("lb://product-service"))

            // ===== Wishlist (part of Product Service — requires JWT) =====
            .route("wishlist", r -> r
                .path("/api/v1/wishlist/**")
                .filters(f -> f.stripPrefix(1))
                .uri("lb://product-service"))

            // ===== Order Service (requires JWT) =====
            .route("seller-orders", r -> r
                .path("/api/v1/sellers/**")
                .filters(f -> f.stripPrefix(1))
                .uri("lb://order-service"))

            .route("order", r -> r
                .path("/api/v1/orders/**")
                .filters(f -> f.stripPrefix(1))
                .uri("lb://order-service"))

            // ===== Payment Service =====
            .route("stripe-webhook", r -> r
                .path("/api/v1/stripe/webhooks")
                .filters(f -> f.stripPrefix(1))
                .uri("lb://payment-service"))

            .route("stripe-onboarding", r -> r
                .path("/api/v1/stripe/onboarding/**")
                .filters(f -> f.stripPrefix(1))
                .uri("lb://payment-service"))

            .route("seller-payments", r -> r
                .path("/api/v1/seller/payments/**")
                .filters(f -> f.stripPrefix(1))
                .uri("lb://payment-service"))

            .route("payment", r -> r
                .path("/api/v1/payments/**")
                .filters(f -> f.stripPrefix(1))
                .uri("lb://payment-service"))

            // ===== Refund Service =====
            .route("refund", r -> r
                .path("/api/v1/admin/refunds/**")
                .filters(f -> f.stripPrefix(1))
                .uri("lb://refund-service"))

            // ===== FlashSale Service =====
            .route("fs-read", r -> r
                .path("/api/v1/flash-sales/**")
                .and().method(HttpMethod.GET)
                .filters(f -> f.stripPrefix(1))
                .uri("lb://flashsale-service"))

            .route("fs-buy", r -> r
                .path("/api/v1/flash-sales/*/buy")
                .and().method(HttpMethod.POST)
                .filters(f -> f.stripPrefix(1))
                .uri("lb://flashsale-service"))

            .route("fs-write", r -> r
                .path("/api/v1/flash-sales/**")
                .and().method(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)
                .filters(f -> f.stripPrefix(1))
                .uri("lb://flashsale-service"))

            // ===== Worker Service (requires JWT) =====
            .route("worker", r -> r
                .path("/api/v1/workers/**", "/api/v1/jobs/**")
                .filters(f -> f.stripPrefix(1))
                .uri("lb://worker-service"))

            // ===== Search Service =====
            .route("search", r -> r
                .path("/api/v1/search/**")
                .filters(f -> f.stripPrefix(1))
                .uri("lb://search-service"))

            // ===== Notification Service (requires JWT) =====
            .route("notification", r -> r
                .path("/api/v1/notifications/**")
                .filters(f -> f.stripPrefix(1))
                .uri("lb://notification-service"))

            // ===== Banner =====
            .route("admin-banners", r -> r
                .path("/api/v1/admin/banners/**")
                .filters(f -> f.stripPrefix(1))
                .uri("lb://product-service"))

            // ===== AI Chat Service =====
            .route("ai-chat", r -> r
                .path("/api/ai/**")
                .filters(f -> f.stripPrefix(1))
                .uri("lb://chat-service"))

            .build();
    }
}
