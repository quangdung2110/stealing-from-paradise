package com.flashsale.productservice.dto.image;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageUploadResponse {

    private String uploadUrl;
    private String presignedUrl;
    private String objectUrl;
    private UUID imageId;
    private LocalDateTime expiresAt;
    private int expiresIn;
}
