package com.flashsale.paymentservice.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "seller_transfers", indexes = {
    @Index(columnList = "order_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerTransfer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "parent_order_id")
    private Long parentOrderId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "transfer_amount", nullable = false)
    private BigDecimal transferAmount;

    @Column(name = "stripe_transfer_id")
    private String stripeTransferId;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "payout_eligible_at")
    private LocalDateTime payoutEligibleAt;

    @Column(name = "platform_commission_amt")
    private BigDecimal platformCommissionAmount;

    @Column(name = "payout_at")
    private LocalDateTime payoutAt;

    @Column(name = "payout_retry_count")
    private Integer payoutRetryCount = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
