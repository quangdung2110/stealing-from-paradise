package com.flashsale.identityservice.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.dto.AuthResponse;
import com.flashsale.commonlib.dto.LoginRequest;
import com.flashsale.commonlib.dto.RegisterRequest;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.commonlib.security.JwtUtils;
import com.flashsale.identityservice.domain.model.User;
import com.flashsale.identityservice.domain.repository.RoleRepository;
import com.flashsale.identityservice.service.AuthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Authentication Controller
 * Handles login, logout, registration, and token refresh
 */
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final RoleRepository roleRepository;
    private final JwtUtils jwtUtils;

    @Value("${jwt.expiration:86400}")
    private long accessTokenExpiration;

    /**
     * Login endpoint
     * Extracts domain from request to determine role automatically
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @RequestBody LoginRequest loginRequest,
            HttpServletRequest request) {
        log.info("Login attempt for user: {}", loginRequest.getCredential());
        try {
            // Extract domain from request (e.g., "seller.localhost", "admin.localhost", etc.)
            String domain = request.getServerName();

            AuthResponse authResponse = authService.authenticateUser(loginRequest.getCredential(), loginRequest.getPassword(), domain);
            return ResponseEntity.ok(ApiResponse.success(authResponse, "Login successful"));
        } catch (Exception e) {
            log.warn("Login failed: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("AUTH_001", e.getMessage()));
        }
    }

    /**
     * Register endpoint
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@RequestBody RegisterRequest registerRequest) {
        log.info("Registration attempt for username: {}", registerRequest.getUsername());
        try {
            // Validate input
            if (registerRequest.getUsername() == null || registerRequest.getUsername().isEmpty()) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("REG_001", "Username is required"));
            }
            if (registerRequest.getEmail() == null || registerRequest.getEmail().isEmpty()) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("REG_002", "Email is required"));
            }
            if (registerRequest.getPassword() == null || registerRequest.getPassword().isEmpty()) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("REG_003", "Password is required"));
            }

            User newUser = authService.registerUser(
                    registerRequest.getUsername(),
                    registerRequest.getEmail(),
                    registerRequest.getPhone(),
                    registerRequest.getPassword(),
                    registerRequest.getFullName()
            );

            // Fetch role from roles table using user ID
            String roleName = roleRepository.findFirstByUserIdOrderByIdAsc(newUser.getId())
                    .map(role -> role.getRoleName())
                    .orElse("BUYER"); // Default to BUYER if no role found

            // Generate tokens for the newly registered user
            String accessToken = jwtUtils.generateAccessToken(newUser.getId().toString(), newUser.getEmail(), "BUYER");
            String refreshToken = jwtUtils.generateRefreshToken(newUser.getId().toString());

            // Build auth response with tokens
            AuthResponse authResponse = AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(accessTokenExpiration)
                    .refreshExpiresIn(604800L)
                    .userId(newUser.getId())
                    .username(newUser.getUsername())
                    .email(newUser.getEmail())
                    .phone(newUser.getPhone())
                    .fullName(newUser.getFullName())
                    .status(newUser.getStatus())
                    .build();

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success(authResponse, "Registration successful"));
        } catch (Exception e) {
            log.warn("Registration failed: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("REG_003", e.getMessage()));
        }
    }

    /**
     * Seller Registration endpoint
     * Same as /register but automatically assigns SELLER role via roles table
     */
    @PostMapping("/register/seller")
    public ResponseEntity<ApiResponse<AuthResponse>> registerSeller(@RequestBody RegisterRequest registerRequest) {
        log.info("Seller registration attempt for username: {}", registerRequest.getUsername());
        try {
            // Validate input
            if (registerRequest.getUsername() == null || registerRequest.getUsername().isEmpty()) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("REG_001", "Username is required"));
            }
            if (registerRequest.getEmail() == null || registerRequest.getEmail().isEmpty()) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("REG_002", "Email is required"));
            }
            if (registerRequest.getPassword() == null || registerRequest.getPassword().isEmpty()) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("REG_003", "Password is required"));
            }

            User newUser = authService.registerUserWithRole(
                    registerRequest.getUsername(),
                    registerRequest.getEmail(),
                    registerRequest.getPhone(),
                    registerRequest.getPassword(),
                    registerRequest.getFullName(),
                    "SELLER"  // Automatically assign SELLER role
            );

            // Get role from roles table
            String roleName = roleRepository.findFirstByUserIdOrderByIdAsc(newUser.getId())
                    .map(role -> role.getRoleName())
                    .orElse("SELLER");

            // Generate tokens for the newly registered seller
            String accessToken = jwtUtils.generateAccessToken(newUser.getId().toString(), newUser.getEmail(), "SELLER");
            String refreshToken = jwtUtils.generateRefreshToken(newUser.getId().toString());

            // Generate response
            AuthResponse authResponse = AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(accessTokenExpiration)
                    .refreshExpiresIn(604800L)
                    .userId(newUser.getId())
                    .username(newUser.getUsername())
                    .email(newUser.getEmail())
                    .phone(newUser.getPhone())
                    .fullName(newUser.getFullName())
                    .status(newUser.getStatus())
                    .build();

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success(authResponse, "Seller registration successful"));
        } catch (Exception e) {
            log.warn("Seller registration failed: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("REG_003", e.getMessage()));
        }
    }

    /**
     * Refresh access token using refresh token
     * Can provide refresh token via:
     * 1. Authorization header: "Bearer <refreshToken>"
     * 2. X-Refresh-Token header
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Refresh-Token", required = false) String refreshTokenHeader) {
        log.info("Token refresh requested");
        try {
            // Try to get refresh token from X-Refresh-Token header first, then Authorization header
            String refreshToken = refreshTokenHeader;
            if (refreshToken == null || refreshToken.isBlank()) {
                refreshToken = extractTokenFromHeader(authHeader);
            }

            if (refreshToken == null) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("AUTH_002", "Refresh token not provided"));
            }

            AuthResponse authResponse = authService.refreshAccessToken(refreshToken);
            return ResponseEntity.ok(ApiResponse.success(authResponse, "Token refreshed"));
        } catch (Exception e) {
            log.warn("Token refresh failed: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("AUTH_003", e.getMessage()));
        }
    }

    /**
     * Logout endpoint
     * Requires authentication - @PreAuthorize checks if user is authenticated
     * API Gateway sets SecurityContext via JwtTokenDecoderFilter
     * @AuthenticationPrincipal injects the authenticated UserDetailsImpl
     */
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestHeader(value = "X-Access-Token", required = false) String accessToken) {
        log.info("Logout requested for userId: {}", user.getId());
        try {
            // Blacklist the access token if provided
            if (accessToken != null && !accessToken.isBlank()) {
                authService.logout(accessToken);
            }

            return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
        } catch (Exception e) {
            log.warn("Logout failed: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("AUTH_004", "Logout failed"));
        }
    }

    /**
     * Extract Bearer token from Authorization header
     */
    private String extractTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
