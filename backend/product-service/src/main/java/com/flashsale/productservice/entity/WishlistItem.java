package com.flashsale.productservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Wishlist item with composite PK = (customer_id, product_id).
 * One row per customer per product; hard delete on removal (no soft-delete),
 * mirroring the CartItem design.
 */
@Entity
@Table(name = "wishlist_items", indexes = {
    @Index(name = "idx_wishlist_items_customer", columnList = "customer_id")
})
@IdClass(WishlistItem.WishlistItemId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WishlistItem {

    @Id
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Id
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", insertable = false, updatable = false)
    private Product product;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WishlistItemId implements Serializable {
        private Long customerId;
        private UUID productId;
    }
}
