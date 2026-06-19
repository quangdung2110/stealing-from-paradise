package com.flashsale.orderservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CancelOrderRequest {

    @NotBlank(message = "reason là bắt buộc")
    private String reason;

    private String note;
}
