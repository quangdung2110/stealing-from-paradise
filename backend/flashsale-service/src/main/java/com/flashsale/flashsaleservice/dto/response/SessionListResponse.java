package com.flashsale.flashsaleservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionListResponse {
    private long serverTime;
    private List<SessionResponse> sessions;
}
