package com.flashsale.orderservice.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_items", indexes = {
    @Index(columnList = "order_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "sku_code", nullable = false, length = 100)
    private String skuCode;

    @Column(name = "variant_id", length = 100)
    private String variantId;

    @Column(name = "name_snapshot", length = 500)
    private String nameSnapshot;       // product name

    @Column(name = "image_snapshot", length = 1000)
    private String imageSnapshot;

    @Column(name = "price_snapshot")
    private BigDecimal priceSnapshot;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "refunded_quantity")
    private Integer refundedQuantity = 0;

    @Column(name = "fs_item_id")
    private Long fsItemId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
