package com.flashsale.commonlib.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter;
import org.springframework.web.reactive.DispatcherHandler;

/**
 * Common Security Configuration for WebFlux Services
 *
 * ✅ Stateless reactive setup (JWT-based)
 * ✅ Security headers enabled for protection against common attacks
 * ✅ Enable method-level security (@PreAuthorize, @PostAuthorize, etc.)
 *
 * Usage: Add @EnableWebFluxSecurity to service config
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(DispatcherHandler.class)
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@Slf4j
public class WebFluxSecurityConfig {

    /**
     * Security web filter chain for WebFlux services
     * - CSRF disabled (stateless JWT)
     * - Security headers enabled
     * - Reactive stateless sessions
     */
    @Bean
    @ConditionalOnMissingBean(name = "springSecurityFilterChain")
    public SecurityWebFilterChain springSecurityFilterChain(
            org.springframework.security.config.web.server.ServerHttpSecurity http) {
        log.info("🚀 [WebFlux] Configuring security web filter chain with security headers");

        http
            .csrf(org.springframework.security.config.web.server.ServerHttpSecurity.CsrfSpec::disable)
            .headers(headers -> headers
                .frameOptions(frame -> frame
                    .mode(XFrameOptionsServerHttpHeadersWriter.Mode.DENY)
                )
                .contentTypeOptions(contentType -> {})
                .hsts(hsts -> hsts
                    .includeSubdomains(true)
                    .maxAge(java.time.Duration.ofDays(365))
                )
                .referrerPolicy(referrer -> referrer
                    .policy(ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                )
                .permissionsPolicy(permissions -> permissions
                    .policy("geolocation=(), microphone=(), camera=(), payment=()")
                )
            )
            .authorizeExchange(exchange -> exchange.anyExchange().permitAll());

        return http.build();
    }
}
