package com.flashsale.notificationservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Document(collection = "mg_notifications")
@CompoundIndex(name = "idx_user_read", def = "{'user_id': 1, 'is_read': 1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    private String id;

    @Indexed
    @Field("user_id")
    private Long userId;

    /**
     * Notification type from the Type Catalog.
     * Values: ORDER_CREATED, ORDER_SHIPPED, ORDER_DELIVERED, ORDER_CANCELLED,
     *         ORDER_RETURNED, ORDER_PAID, PAYMENT_FAILED, REFUND_REQUESTED,
     *         REFUND_APPROVED, REFUND_REJECTED, FLASH_SALE_STARTING, FLASH_SALE_ENDED,
     *         FS_ITEM_APPROVED, FS_ITEM_REJECTED, PRODUCT_APPROVED, PRODUCT_REJECTED,
     *         TRANSFER_ELIGIBLE, TRANSFER_PAID_OUT, TRANSFER_FAILED,
     *         SELLER_STRIPE_REQUIREMENT, STRIPE_ACCOUNT_SUSPENDED,
     *         STOCK_RESERVATION_EXPIRED, CHAT_MESSAGE, CHAT_TOOL_CALL, CHAT_CONFIRMATION
     */
    private String type;

    private String title;

    private String body;

    /** JSON string with supplementary data: template_id, deeplink, entity IDs, etc. */
    private String metadata;

    @Field("is_read")
    @Builder.Default
    private Boolean isRead = false;

    /** Priority: URGENT / HIGH / NORMAL / LOW */
    @Builder.Default
    private String priority = "NORMAL";

    /** Timestamp when the notification was marked as read (nullable). */
    @Field("read_at")
    private LocalDateTime readAt;

    @Indexed(expireAfterSeconds = 7776000)  // 90-day TTL
    @Field("created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
