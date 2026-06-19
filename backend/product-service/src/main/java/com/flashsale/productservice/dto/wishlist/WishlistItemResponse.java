package com.flashsale.productservice.dto.wishlist;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WishlistItemResponse {

    private UUID productId;
    private String productName;
    private String productSlug;
    private String thumbnailUrl;
    /** Lowest price among non-deleted variants; null when product has no variant. */
    private BigDecimal minPrice;
    private String productStatus;
    /** true when the customer can still see/buy it (ACTIVE or OUT_OF_STOCK). */
    private Boolean available;
    private LocalDateTime addedAt;
}
