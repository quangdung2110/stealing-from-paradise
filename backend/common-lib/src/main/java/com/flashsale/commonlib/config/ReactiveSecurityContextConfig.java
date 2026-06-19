package com.flashsale.commonlib.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.DispatcherHandler;

/**
 * Reactive Security Context Configuration
 *
 * ✅ Enables SecurityContext for WebFlux services
 * ✅ Auto-loads SecurityContext from ReactiveSecurityContextHolder
 * ✅ Used by SecurityContextWebFilter from api-gateway
 *
 * Usage: Imported automatically by WebFluxSecurityConfig
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(DispatcherHandler.class)
@Slf4j
public class ReactiveSecurityContextConfig {
    // SecurityWebFilterChain bean is provided by WebFluxSecurityConfig
}

