package com.flashsale.identityservice.dto.response;

public record InternalUserInfoResponse(
        Long userId,
        String username,
        String email,
        String phone,
        String role,
        String status
) {}
