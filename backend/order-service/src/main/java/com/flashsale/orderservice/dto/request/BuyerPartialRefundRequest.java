package com.flashsale.orderservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

@Data
public class BuyerPartialRefundRequest {

    @NotBlank(message = "reason is required")
    private String reason;

    @NotEmpty(message = "items không được để trống")
    @Valid
    private List<BuyerPartialRefundItem> items;

    private List<String> evidenceImages;
}
