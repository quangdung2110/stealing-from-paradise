package com.flashsale.productservice.dto.image;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterImageRequest {

    @NotNull(message = "Image ID is required")
    private UUID imageId;

    @NotBlank(message = "URL is required")
    private String url;

    @Min(value = 0, message = "Sort order must be non-negative")
    @Builder.Default
    private Integer sortOrder = 0;
}
