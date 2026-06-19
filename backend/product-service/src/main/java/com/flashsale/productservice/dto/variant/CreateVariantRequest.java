package com.flashsale.productservice.dto.variant;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
public class CreateVariantRequest {

    @NotBlank(message = "Variant code is required")
    @Pattern(regexp = "^[a-zA-Z0-9-]+$", message = "Variant code must be alphanumeric with dashes only")
    @Size(min = 3, max = 50, message = "Variant code must be between 3 and 50 characters")
    private String variantCode;

    private String variantName;

    private Map<String, Object> variantAttributes;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    private BigDecimal price;

    @DecimalMin(value = "0.01", message = "Original price must be greater than 0")
    private BigDecimal originalPrice;

    @Builder.Default
    private Integer stockQuantity = 0;

    private String imageUrl;
}
