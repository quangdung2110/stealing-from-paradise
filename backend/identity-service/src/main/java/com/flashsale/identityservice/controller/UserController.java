package com.flashsale.identityservice.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.identityservice.dto.request.AddressCreateRequest;
import com.flashsale.identityservice.dto.request.AddressUpdateRequest;
import com.flashsale.identityservice.dto.request.ChangePasswordRequest;
import com.flashsale.identityservice.dto.request.NotificationPreferencesUpdateRequest;
import com.flashsale.identityservice.dto.request.UserProfileUpdateRequest;
import com.flashsale.identityservice.dto.response.AddressResponse;
import com.flashsale.identityservice.dto.response.NotificationPreferencesResponse;
import com.flashsale.identityservice.dto.response.PresignedUrlResponse;
import com.flashsale.identityservice.dto.response.SellerPublicResponse;
import com.flashsale.identityservice.dto.response.UserProfileResponse;
import com.flashsale.identityservice.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    @Value("${minio.public-url:http://localhost:9000}")
    private String minioPublicUrl;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUser(
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserProfile(user.getId())));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateCurrentUser(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestBody UserProfileUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.updateUserProfile(user.getId(), request), "Profile updated"));
    }

    @GetMapping("/me/avatar/presigned-url")
    public ResponseEntity<ApiResponse<PresignedUrlResponse>> getAvatarPresignedUrl(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestParam String contentType) {
        String objectKey = user.getId() + "/" + java.util.UUID.randomUUID() + getExtension(contentType);
        String cdnUrl = "https://cdn.marketplace.vn/avatars/" + objectKey;

        PresignedUrlResponse response = PresignedUrlResponse.builder()
                .uploadUrl(buildPresignedPutUrl(objectKey, contentType))
                .objectKey(objectKey)
                .cdnUrl(cdnUrl)
                .expiresIn(900)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/me/addresses")
    public ResponseEntity<ApiResponse<java.util.List<AddressResponse>>> getAddresses(
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserAddresses(user.getId())));
    }

    @PostMapping("/me/addresses")
    public ResponseEntity<ApiResponse<AddressResponse>> addAddress(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestBody AddressCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(userService.addAddress(user.getId(), request), "Address added"));
    }

    @PutMapping("/me/addresses/{addressId}")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(
            @AuthenticationPrincipal UserDetailsImpl user,
            @PathVariable Long addressId,
            @RequestBody AddressUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.updateAddress(user.getId(), addressId, request), "Address updated"));
    }

    @DeleteMapping("/me/addresses/{addressId}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @AuthenticationPrincipal UserDetailsImpl user,
            @PathVariable Long addressId) {
        userService.deleteAddress(user.getId(), addressId);
        return ResponseEntity.ok(ApiResponse.success(null, "Address deleted"));
    }


    @PostMapping("/me/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestBody ChangePasswordRequest request) {
        userService.changePassword(user.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(null, "Password changed successfully"));
    }

    @PostMapping("/me/roles/seller")
    public ResponseEntity<ApiResponse<Void>> registerAsSeller(
                @AuthenticationPrincipal UserDetailsImpl user) {
            userService.registerAsSeller(user.getId());
            return ResponseEntity.ok(ApiResponse.success(null, "Registered as seller"));
        }

    @GetMapping("/me/notification-preferences")
    public ResponseEntity<ApiResponse<NotificationPreferencesResponse>> getNotificationPreferences(
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.getNotificationPreferences(user.getId())));
    }

    @PutMapping("/me/notification-preferences")
    public ResponseEntity<ApiResponse<NotificationPreferencesResponse>> updateNotificationPreferences(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestBody NotificationPreferencesUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.updateNotificationPreferences(user.getId(), request), "Preferences updated"));
    }

    @GetMapping("/sellers/{sellerId}")
    public ResponseEntity<ApiResponse<SellerPublicResponse>> getSellerPublic(
            @PathVariable Long sellerId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getSellerPublicInfo(sellerId)));
    }

    @PutMapping("/me/avatar")
    public ResponseEntity<ApiResponse<Void>> updateAvatar(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestBody java.util.Map<String, String> body) {
        userService.updateAvatarUrl(user.getId(), body.get("avatarUrl"));
        return ResponseEntity.ok(ApiResponse.success(null, "Avatar updated"));
    }

    private String getExtension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private String buildPresignedPutUrl(String objectKey, String contentType) {
        return minioPublicUrl + "/user-avatars/" + objectKey + "?X-Amz-Algorithm=AWS4-HMAC-SHA256";
    }
}
