package com.flashsale.productservice.dto.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateProductRequest {

    @NotBlank(message = "Product name is required")
    @Size(min = 5, max = 200, message = "Name must be between 5 and 200 characters")
    private String name;

    @Size(max = 10000, message = "Description cannot exceed 10000 characters")
    private String description;

    @NotNull(message = "Category ID is required")
    private UUID categoryId;

    private Map<String, Object> attributes;
}
