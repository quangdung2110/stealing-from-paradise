package com.flashsale.identityservice.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.dto.PageResponse;
import com.flashsale.identityservice.domain.model.User;
import com.flashsale.identityservice.domain.repository.RoleRepository;
import com.flashsale.identityservice.domain.repository.UserRepository;
import com.flashsale.identityservice.dto.request.LockRequest;
import com.flashsale.identityservice.dto.request.UnlockRequest;
import com.flashsale.identityservice.dto.response.AdminUserResponse;
import com.flashsale.identityservice.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserService userService;

    // ── User Listing ────────────────────────────────────────────────────────

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<AdminUserResponse>>> listUsers(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<AdminUserResponse> result = userService.listUsers(status, search, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminUserResponse>> getUserDetail(
            @PathVariable Long userId) {
        AdminUserResponse response = userService.getAdminUserDetail(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ── Account Locking ─────────────────────────────────────────────────────

    @PostMapping("/users/{userId}/lock")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> lockUser(
            @PathVariable Long userId,
            @RequestBody LockRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if ("LOCKED".equals(user.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("ALREADY_LOCKED", "User is already locked"));
        }

        user.setStatus("LOCKED");
        userRepository.save(user);

        log.info("Admin locked user {}: reason={}", userId, request.getReason());
        return ResponseEntity.ok(ApiResponse.success(null, "User locked"));
    }

    @PostMapping("/users/{userId}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> unlockUser(
            @PathVariable Long userId,
            @RequestBody UnlockRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setStatus("ACTIVE");
        userRepository.save(user);

        log.info("Admin unlocked user {}: reason={}", userId, request.getReason());
        return ResponseEntity.ok(ApiResponse.success(null, "User unlocked"));
    }
}
