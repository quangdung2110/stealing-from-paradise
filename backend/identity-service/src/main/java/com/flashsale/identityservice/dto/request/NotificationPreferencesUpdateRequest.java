package com.flashsale.identityservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.Map;

@Data
public class NotificationPreferencesUpdateRequest {
    @NotNull
    private Map<String, Boolean> preferences;
}
