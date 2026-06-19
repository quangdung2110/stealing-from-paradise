package com.flashsale.productservice.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryResponse {

    private UUID variantId;
    private String variantCode;
    private Integer stockTotal;
    private Integer stockLocked;
    private Integer stockAvailable;
    private Integer stockFlashReserved;
}
