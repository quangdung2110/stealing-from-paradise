package com.flashsale.refundservice.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class AdminRefundApproveResponse {

    private Long refundId;
    private String refundCode;
    private String status;
    private String type;
    private BigDecimal amount;
    private String trackingNumber;
    private List<ReturnEvidence> returnEvidence;
    private Long reviewedBy;
    private String adminNote;
    private Instant reviewedAt;
    private String stripeRefundId;

    @Data
    @Builder
    public static class ReturnEvidence {
        private String type;
        private String trackingNumber;
        private Instant recordedAt;
    }
}
