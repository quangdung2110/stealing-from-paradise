package com.flashsale.productservice.dto.inventory;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdjustStockRequest {

    @NotNull(message = "Delta is required")
    private Integer delta;

    private Integer version;

    private AdjustmentReason reason;

    public enum AdjustmentReason {
        MANUAL,
        FLASH_SALE_RESERVE,
        FLASH_SALE_RELEASE
    }
}
