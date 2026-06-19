package com.flashsale.identityservice.service;

import com.flashsale.commonlib.dto.AuthResponse;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.security.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.identityservice.domain.model.User;
import com.flashsale.identityservice.domain.repository.UserRepository;
import com.flashsale.identityservice.domain.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final TokenBlacklistService tokenBlacklistService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${jwt.expiration:86400}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-expiration:604800}")
    private long refreshTokenExpiration;

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Transactional
    public User registerUser(String username, String email, String phone, String password, String fullName) {
        log.info("Registering user: {}", username);

        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Username already exists: " + username);
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already exists: " + email);
        }
        if (phone != null && !phone.isBlank() && userRepository.findByPhone(phone).isPresent()) {
            throw new RuntimeException("Phone already exists: " + phone);
        }

        String hashedPassword = passwordEncoder.encode(password);

        User user = User.builder()
                .username(username)
                .email(email)
                .phone(phone)
                .password(hashedPassword)
                .fullName(fullName)
                .status("ACTIVE")
                .build();

        user = userRepository.save(user);

        com.flashsale.identityservice.domain.model.Role role = com.flashsale.identityservice.domain.model.Role.builder()
                .userId(user.getId())
                .roleName("BUYER")
                .build();
        roleRepository.save(role);

        return user;
    }

    @Transactional
    public User registerUserWithRole(String username, String email, String phone, String password, String fullName, String roleParam) {
        log.info("Registering user: {} with role: {}", username, roleParam);

        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Username already exists: " + username);
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already exists: " + email);
        }
        if (phone != null && !phone.isBlank() && userRepository.findByPhone(phone).isPresent()) {
            throw new RuntimeException("Phone already exists: " + phone);
        }

        String hashedPassword = passwordEncoder.encode(password);
        String assignedRole = (roleParam != null && !roleParam.isEmpty()) ? roleParam : "BUYER";

        User user = User.builder()
                .username(username)
                .email(email)
                .phone(phone)
                .password(hashedPassword)
                .fullName(fullName)
                .status("ACTIVE")
                .build();

        user = userRepository.save(user);

        com.flashsale.identityservice.domain.model.Role role = com.flashsale.identityservice.domain.model.Role.builder()
                .userId(user.getId())
                .roleName(assignedRole)
                .build();
        roleRepository.save(role);

        if ("SELLER".equalsIgnoreCase(assignedRole)) {
            publishSellerRegistered(user);
        }

        return user;
    }

    private void publishSellerRegistered(User user) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_type", KafkaTopics.SELLER_REGISTERED);
        payload.put("user_id", user.getId());
        payload.put("username", user.getUsername());
        payload.put("email", user.getEmail());
        payload.put("phone", user.getPhone());
        payload.put("full_name", user.getFullName());
        payload.put("registered_at", Instant.now().toString());
        try {
            kafkaTemplate.send(KafkaTopics.SELLER_REGISTERED,
                    String.valueOf(user.getId()),
                    objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("Failed to publish seller.registered for userId={}: {}", user.getId(), e.getMessage());
        }
    }

    public AuthResponse authenticateUser(String credential, String password, String domain) {
        log.info("Attempting to authenticate user: {} from domain: {}", credential, domain);

        User user = userRepository.findByUsername(credential)
                .or(() -> userRepository.findByEmail(credential))
                .or(() -> userRepository.findByPhone(credential))
                .orElseThrow(() -> new RuntimeException("Invalid username or password"));

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new RuntimeException("Account is " + user.getStatus());
        }

        if (!validatePassword(password, user.getPassword())) {
            throw new RuntimeException("Invalid username or password");
        }

        String dbRole = roleRepository.findFirstByUserIdOrderByIdAsc(user.getId())
                .map(role -> role.getRoleName())
                .orElse("BUYER");

        String roleName = determineRoleFromDomain(domain, dbRole);

        String accessToken = jwtUtils.generateAccessToken(
                user.getId().toString(),
                user.getEmail(),
                roleName
        );

        String refreshToken = jwtUtils.generateRefreshToken(user.getId().toString());

        log.info("User authenticated successfully: {} with role: {}", user.getUsername(), roleName);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiration)
                .refreshExpiresIn(refreshTokenExpiration)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private String determineRoleFromDomain(String domain, String userRole) {
        if (domain == null || domain.isEmpty()) {
            return (userRole != null && !userRole.isEmpty()) ? userRole : "BUYER";
        }

        String domainLower = domain.toLowerCase();
        String dbRole = (userRole != null && !userRole.isEmpty()) ? userRole.toUpperCase() : "BUYER";

        if (domainLower.contains("admin")) {
            if ("ADMIN".equals(dbRole)) {
                return "ADMIN";
            }
            return "BUYER";
        }

        if (domainLower.contains("seller")) {
            if ("SELLER".equals(dbRole) || "ADMIN".equals(dbRole)) {
                return "SELLER";
            }
            return "BUYER";
        }

        if (domainLower.contains("customer") || domainLower.contains("app")) {
            if (domainLower.contains("customer")) {
                return "BUYER";
            }
            return dbRole;
        }

        return dbRole;
    }

    public boolean validatePassword(String rawPassword, String hashedPassword) {
        return passwordEncoder.matches(rawPassword, hashedPassword);
    }

    public AuthResponse refreshAccessToken(String refreshToken) {
        log.info("Attempting to refresh access token");

        if (!jwtUtils.isTokenValid(refreshToken)) {
            throw new RuntimeException("Invalid or expired refresh token");
        }

        String userId = jwtUtils.extractUserId(refreshToken);
        if (userId == null) {
            throw new RuntimeException("Could not extract user ID from refresh token");
        }

        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new RuntimeException("User not found"));

        String roleName = roleRepository.findFirstByUserIdOrderByIdAsc(user.getId())
                .map(role -> role.getRoleName())
                .orElse("BUYER");

        String newAccessToken = jwtUtils.generateAccessToken(
                user.getId().toString(),
                user.getEmail(),
                roleName
        );

        String newRefreshToken = jwtUtils.generateRefreshToken(user.getId().toString());

        log.info("Access token refreshed for user: {}", user.getUsername());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiration)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .build();
    }

    public void logout(String token) {
        if (token != null && !token.isEmpty()) {
            tokenBlacklistService.blacklistToken(token);
            String userId = jwtUtils.extractUserId(token);
            log.info("User logged out - userId: {}", userId);
        }
    }

    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
