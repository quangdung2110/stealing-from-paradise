package com.flashsale.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * API Gateway Security Configuration
 *
 * This config adds CORS support so browser preflight OPTIONS requests pass through.
 * Spring Cloud Gateway's globalcors config (in application.yml) handles the CorsWebFilter.
 *
 * ✅ Security headers enabled (X-Frame-Options, X-Content-Type-Options, HSTS, etc.)
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(org.springframework.security.config.web.server.ServerHttpSecurity http) {
        http
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers
                .frameOptions(frame -> frame
                    .mode(org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter.Mode.DENY)
                )
                .contentTypeOptions(contentType -> {})
                .xssProtection(ServerHttpSecurity.HeaderSpec.XssProtectionSpec::disable)
                .hsts(hsts -> hsts
                    .includeSubdomains(true)
                    .maxAge(java.time.Duration.ofDays(365))
                )
                .referrerPolicy(referrer -> referrer
                    .policy(org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                )
                .permissionsPolicy(permissions -> permissions
                    .policy("geolocation=(), microphone=(), camera=(), payment=()")
                )
            )
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers(HttpMethod.OPTIONS).permitAll()
                .anyExchange().permitAll()
            )
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            .cors(cors -> {});

        return http.build();
    }

}