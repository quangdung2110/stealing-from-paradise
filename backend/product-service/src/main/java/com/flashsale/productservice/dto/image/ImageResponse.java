package com.flashsale.productservice.dto.image;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageResponse {

    private UUID id;
    private UUID productId;
    private UUID variantId;
    private String url;
    private Integer sortOrder;
}
