package com.flashsale.identityservice.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.identityservice.domain.model.Role;
import com.flashsale.identityservice.domain.model.User;
import com.flashsale.identityservice.domain.repository.RoleRepository;
import com.flashsale.identityservice.domain.repository.UserRepository;
import com.flashsale.identityservice.dto.response.InternalUserInfoResponse;
import com.flashsale.identityservice.dto.response.InternalUserRoleResponse;
import com.flashsale.identityservice.dto.response.UserExistsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
@Slf4j
public class InternalUserController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @GetMapping("/{userId}/role")
    public ResponseEntity<ApiResponse<InternalUserRoleResponse>> getUserRole(@PathVariable Long userId) {
        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("USER_NOT_FOUND", "User not found: " + userId));
        }

        String roleName = roleRepository.findFirstByUserIdOrderByIdAsc(userId)
                .map(Role::getRoleName)
                .orElse("BUYER");

        return ResponseEntity.ok(ApiResponse.success(
                new InternalUserRoleResponse(userId.toString(), roleName, "ACTIVE".equals(user.getStatus()))));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<InternalUserInfoResponse>> getUserInfo(@PathVariable Long userId) {
        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("USER_NOT_FOUND", "User not found: " + userId));
        }

        String roleName = roleRepository.findFirstByUserIdOrderByIdAsc(userId)
                .map(Role::getRoleName)
                .orElse("BUYER");

        return ResponseEntity.ok(ApiResponse.success(
                new InternalUserInfoResponse(
                        user.getId(),
                        user.getUsername(),
                        user.getEmail(),
                        user.getPhone(),
                        roleName,
                        user.getStatus())));
    }

    @GetMapping("/exists")
    public ResponseEntity<ApiResponse<UserExistsResponse>> checkUserExists(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone) {
        String field;
        boolean exists;

        if (username != null) {
            exists = userRepository.findByUsername(username).isPresent();
            field = "username";
        } else if (email != null) {
            exists = userRepository.findByEmail(email).isPresent();
            field = "email";
        } else if (phone != null) {
            exists = userRepository.findByPhone(phone).isPresent();
            field = "phone";
        } else {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("MISSING_PARAM", "Must provide username, email, or phone"));
        }

        return ResponseEntity.ok(ApiResponse.success(new UserExistsResponse(exists, field)));
    }
}
