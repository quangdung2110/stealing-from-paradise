package com.flashsale.identityservice.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class NotificationPreferencesResponse {
    private Map<String, Boolean> preferences;
}
