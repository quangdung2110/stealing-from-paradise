package com.flashsale.productservice.dto.variant;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateVariantRequest {

    private String variantName;

    private Map<String, Object> variantAttributes;

    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    private BigDecimal price;

    @DecimalMin(value = "0.01", message = "Original price must be greater than 0")
    private BigDecimal originalPrice;

    private Integer stockQuantity;

    @Size(max = 50, message = "Status must be at most 50 characters")
    private String status;

    private String imageUrl;

    private Integer version;
}
