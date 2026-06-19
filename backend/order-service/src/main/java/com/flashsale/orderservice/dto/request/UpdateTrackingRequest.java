package com.flashsale.orderservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateTrackingRequest {

    @NotBlank(message = "tracking_number là bắt buộc")
    private String trackingNumber;

    private String carrier;

    private String note;
}
