package com.flashsale.productservice.dto.wishlist;

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
public class AddWishlistItemRequest {

    @NotNull(message = "productId is required")
    private UUID productId;
}
