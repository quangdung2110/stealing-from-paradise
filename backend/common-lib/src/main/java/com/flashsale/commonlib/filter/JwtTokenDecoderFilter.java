package com.flashsale.commonlib.filter;

import com.flashsale.commonlib.security.UserDetailsImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/**
 * JWT Token Decoder Filter — Decodes X-User-* headers into SecurityContext.
 *
 * These headers are set by the API Gateway after validating the Bearer JWT token.
 * This filter populates the SecurityContext BEFORE Spring Security's AuthorizationFilter
 * runs, so @PreAuthorize annotations work correctly.
 *
 * Security measures:
 * - Validates role against allowed values (defense-in-depth)
 * - Validates userId as positive number
 * - Uses role whitelist to prevent privilege escalation
 *
 * NOTE: NOT a @Component. Each service's SecurityConfig creates this as a @Bean
 * and adds it INSIDE the SecurityFilterChain (after SecurityContextHolderFilter)
 * via addFilterAfter. This avoids the OncePerRequestFilter double-run problem
 * caused by the @Component auto-registering it in the global servlet filter chain,
 * where SecurityContextHolderFilter (STATELESS) wipes the context.
 */
@Slf4j
public class JwtTokenDecoderFilter extends OncePerRequestFilter {

    /** Allowed role values - prevents role injection */
    private static final Set<String> ALLOWED_ROLES = Set.of("ADMIN", "SELLER", "BUYER");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String userId = request.getHeader("X-User-Id");
        String email = request.getHeader("X-User-Email");
        String role = request.getHeader("X-User-Role");

        if (userId != null && !userId.isBlank()) {
            try {
                // Validate and parse userId as positive number
                long userIdLong = parseAndValidateUserId(userId);
                if (userIdLong <= 0) {
                    log.warn("[JwtTokenDecoder] Invalid userId (not positive): {}", userId);
                    filterChain.doFilter(request, response);
                    return;
                }

                // Validate role against allowed values (defense-in-depth)
                String validatedRole = validateRole(role);
                if (validatedRole == null) {
                    log.warn("[JwtTokenDecoder] Invalid or missing role: {}, defaulting to empty authority list", role);
                }

                var authorities = (validatedRole != null)
                    ? Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + validatedRole))
                    : Collections.<org.springframework.security.core.GrantedAuthority>emptyList();

                UserDetailsImpl userDetails = UserDetailsImpl.builder()
                    .id(userIdLong)
                    .username(userId)
                    .email(email)
                    .role(validatedRole)
                    .enabled(true)
                    .build();

                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, authorities);

                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("[JwtTokenDecoder] Set SecurityContext - userId: {}, email: {}, role: {}", userId, email, validatedRole);
            } catch (NumberFormatException e) {
                log.warn("[JwtTokenDecoder] Failed to parse userId as number: {}", userId);
            } catch (Exception e) {
                log.warn("[JwtTokenDecoder] Failed to set SecurityContext: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Parse userId and validate it's a positive number
     */
    private long parseAndValidateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is null or blank");
        }
        long parsed = Long.parseLong(userId.trim());
        if (parsed <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        return parsed;
    }

    /**
     * Validate role against allowed values.
     * Returns the uppercase validated role, or null if invalid/missing.
     */
    private String validateRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        String upperRole = role.toUpperCase().trim();
        return ALLOWED_ROLES.contains(upperRole) ? upperRole : null;
    }
}