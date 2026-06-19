package com.flashsale.notificationservice.config;

import com.flashsale.commonlib.config.WebFluxSecurityConfig;
import com.flashsale.commonlib.filter.JwtTokenDecoderWebFilter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;

/**
 * Notification Service Security Configuration
 *
 * Uses WebFluxSecurityConfig from common-lib which:
 * - Disables CSRF (stateless JWT)
 * - Security headers enabled (X-Frame-Options, X-Content-Type-Options, HSTS, Referrer-Policy)
 * - Permits all exchanges (authorization via @PreAuthorize)
 */
@Configuration
@EnableWebFluxSecurity
@Import({WebFluxSecurityConfig.class, JwtTokenDecoderWebFilter.class})
public class SecurityConfig {
    // SecurityWebFilterChain bean provided by WebFluxSecurityConfig from common-lib
}


