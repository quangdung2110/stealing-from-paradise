package com.flashsale.productservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Cart item with composite PK = (customer_id, variant_id).
 * No soft-delete: hard delete on checkout completion or customer action.
 */
@Entity
@Table(name = "cart_items", uniqueConstraints = {
    @UniqueConstraint(name = "uk_cart_items_customer_variant", columnNames = {"customer_id", "variant_id"})
}, indexes = {
    @Index(name = "idx_cart_items_customer", columnList = "customer_id"),
    @Index(name = "idx_cart_items_variant", columnList = "variant_id")
})
@IdClass(CartItem.CartItemId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {

    @Id
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Id
    @Column(name = "variant_id", nullable = false)
    private java.util.UUID variantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", insertable = false, updatable = false)
    private ProductVariant variant;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "price_snapshot", nullable = false, precision = 18, scale = 2)
    private BigDecimal priceSnapshot;

    @Column(name = "variant_name_snapshot", nullable = false)
    private String variantNameSnapshot;

    @Column(name = "variant_image_snapshot")
    private String variantImageSnapshot;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartItemId implements Serializable {
        private Long customerId;
        private java.util.UUID variantId;
    }
}
