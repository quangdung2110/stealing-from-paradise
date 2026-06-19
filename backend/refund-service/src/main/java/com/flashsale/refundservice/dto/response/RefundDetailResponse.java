package com.flashsale.refundservice.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class RefundDetailResponse {

    private Long refundId;
    private String refundCode;
    private Long orderId;
    private String groupRef;
    private String type;
    private String status;
    private BigDecimal amount;
    private String reason;
    private String initiatedBy;
    private String refundReasonType;
    private List<String> evidenceImages;
    private String adminNote;
    private String rejectReason;
    private String causedBy;
    private String trackingNumber;
    private List<ReturnEvidence> returnEvidence;
    private Long reviewedBy;
    private Instant reviewedAt;
    private String stripeRefundId;
    private List<RefundItemInfo> items;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @Builder
    public static class RefundItemInfo {
        private Long itemId;
        private String productName;
        private String imageSnapshot;
        private Integer quantity;
        private BigDecimal refundAmount;
        private String itemReason;
        private String status;
        private String returnTrackingNumber;
        private Instant returnedAt;
    }

    @Data
    @Builder
    public static class ReturnEvidence {
        private String type;
        private String trackingNumber;
        private Instant recordedAt;
    }
}
