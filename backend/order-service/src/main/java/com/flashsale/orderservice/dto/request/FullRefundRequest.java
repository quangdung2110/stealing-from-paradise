package com.flashsale.orderservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@Data
public class FullRefundRequest {

    @NotBlank(message = "reason is required")
    private String reason;

    private List<String> evidenceImages;
}
