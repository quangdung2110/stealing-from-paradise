package com.flashsale.flashsaleservice.config;

import com.flashsale.commonlib.config.WebFluxSecurityConfig;
import com.flashsale.commonlib.filter.JwtTokenDecoderWebFilter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;

/**
 * Flash Sale Service Security Configuration
 *
 * Uses WebFluxSecurityConfig from common-lib which:
 * - Disables CSRF (stateless JWT)
 * - Security headers enabled (X-Frame-Options, X-Content-Type-Options, HSTS, Referrer-Policy)
 * - Permits all exchanges (authorization via @PreAuthorize)
 *
 * @EnableReactiveMethodSecurity is required so the controller's
 * @PreAuthorize annotations resolve against ReactiveSecurityContextHolder
 * (populated by JwtTokenDecoderWebFilter from the gateway X-User-* headers).
 * Without it @PreAuthorize falls back to the servlet SecurityContextHolder,
 * which is always empty in WebFlux and raises AuthenticationCredentialsNotFoundException.
 */
@Configuration
@EnableWebFluxSecurity
@Import({WebFluxSecurityConfig.class, JwtTokenDecoderWebFilter.class})
public class SecurityConfig {
    // SecurityWebFilterChain bean provided by WebFluxSecurityConfig from common-lib
}


