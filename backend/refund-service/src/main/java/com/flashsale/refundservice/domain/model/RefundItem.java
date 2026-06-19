package com.flashsale.refundservice.domain.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "refund_items", schema = "refund")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "refund_id", nullable = false)
    private Long refundId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "refund_amount")
    private BigDecimal refundAmount;

    @Column(name = "item_reason", columnDefinition = "TEXT")
    private String itemReason;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "return_tracking_number")
    private String returnTrackingNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "return_evidence_images", columnDefinition = "jsonb")
    private List<String> returnEvidenceImages;

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    @Column(name = "product_name", length = 500)
    private String productName;

    @Column(name = "image_snapshot", length = 1000)
    private String imageSnapshot;
}
