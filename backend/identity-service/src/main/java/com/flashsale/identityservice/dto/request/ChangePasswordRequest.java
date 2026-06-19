package com.flashsale.identityservice.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {
    @JsonProperty("old_password")
    private String currentPassword;
    @JsonProperty("new_password")
    private String newPassword;
}