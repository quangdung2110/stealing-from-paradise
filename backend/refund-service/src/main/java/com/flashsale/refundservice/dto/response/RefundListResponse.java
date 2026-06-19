package com.flashsale.refundservice.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class RefundListResponse {

    private Long refundId;
    private String refundCode;
    private Long orderId;
    private String groupRef;
    private String type;
    private String status;
    private BigDecimal amount;
    private String initiatedBy;
    private String refundReasonType;
    private String adminNote;
    private String rejectReason;
    private Long reviewedBy;
    private Instant reviewedAt;
    private String refundRef;
    private Instant createdAt;
}
