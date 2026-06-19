package com.flashsale.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class PresignedUrlResponse {

    private String url;
    private String fileName;
    private String contentType;
    private Instant expiresAt;
}
