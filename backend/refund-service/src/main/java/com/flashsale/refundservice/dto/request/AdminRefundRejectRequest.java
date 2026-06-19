package com.flashsale.refundservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminRefundRejectRequest {

    @NotBlank(message = "rejectReason is required")
    private String rejectReason;

    private Boolean fraudEvidence = false;
}
