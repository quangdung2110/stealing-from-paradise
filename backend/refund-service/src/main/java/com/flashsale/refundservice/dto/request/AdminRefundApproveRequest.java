package com.flashsale.refundservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminRefundApproveRequest {

    @NotBlank(message = "adminNote is required")
    @Size(min = 1, max = 1000, message = "adminNote must be 1–1000 characters")
    private String adminNote;

    private String causedBy;   // SELLER | BUYER

    private String trackingNumber;   // optional free-form tracking code
}
