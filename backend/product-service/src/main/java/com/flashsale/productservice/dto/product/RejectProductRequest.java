package com.flashsale.productservice.dto.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RejectProductRequest {

    @NotBlank(message = "Rejection reason is required")
    @Size(min = 10, message = "Rejection reason must be at least 10 characters")
    private String reason;
}
