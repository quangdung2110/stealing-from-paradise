package com.flashsale.identityservice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUrlResponse {
    @JsonProperty("upload_url")
    private String uploadUrl;
    @JsonProperty("object_key")
    private String objectKey;
    @JsonProperty("cdn_url")
    private String cdnUrl;
    @JsonProperty("expires_in")
    private Integer expiresIn;
}
