package com.flashsale.flashsaleservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFlashSaleItemRequest {
    @NotBlank
    private String skuCode;

    @NotNull
    private BigDecimal flashPrice;

    @NotNull
    @Min(1)
    private Integer flashStock;

    @Min(1)
    private Integer limitPerUser;
}
