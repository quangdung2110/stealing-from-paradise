package com.flashsale.identityservice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserResponse {
    @JsonProperty("user_id")
    private Long userId;
    private String username;
    private String email;
    private String phone;
    @JsonProperty("full_name")
    private String fullName;
    private String role;
    private String status;
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}
