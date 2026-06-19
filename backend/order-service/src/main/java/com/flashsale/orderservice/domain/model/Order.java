package com.flashsale.orderservice.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "orders", indexes = {
    @Index(columnList = "customer_id"),
    @Index(columnList = "seller_id"),
    @Index(columnList = "parent_order_id"),
    @Index(columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_order_id", nullable = false)
    private Long parentOrderId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "seller_name")
    private String sellerName;

    @Column(name = "order_code", unique = true, nullable = false)
    private String orderCode;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "total_amt", nullable = false)
    private BigDecimal totalAmt;

    @Column(name = "final_amt", nullable = false)
    private BigDecimal finalAmt;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "cancelled_by")
    private String cancelledBy;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    @Column(name = "is_flash_sale")
    private Boolean isFlashSale = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shipping_address", columnDefinition = "jsonb")
    private String shippingAddress;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "shipping_deadline")
    private LocalDateTime shippingDeadline;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Version
    private Integer version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items;

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
