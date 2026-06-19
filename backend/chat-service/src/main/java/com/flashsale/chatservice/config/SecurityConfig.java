package com.flashsale.chatservice.config;

import com.flashsale.commonlib.config.WebFluxSecurityConfig;
import com.flashsale.commonlib.filter.JwtTokenDecoderWebFilter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;

@Configuration
@EnableWebFluxSecurity
@Import({WebFluxSecurityConfig.class, JwtTokenDecoderWebFilter.class})
public class SecurityConfig {
}
