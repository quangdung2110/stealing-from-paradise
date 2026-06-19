package com.flashsale.productservice.dto.cart;

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
public class ReservationResponse {

    private UUID reservationId;
    private UUID variantId;
    private Integer quantity;
    private LocalDateTime expiresAt;
    private String status;
}
