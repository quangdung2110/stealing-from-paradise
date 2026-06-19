package com.flashsale.productservice.dto.inventory;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestockRequest {

    @Positive(message = "Quantity must be greater than 0")
    private Integer quantity;
}
