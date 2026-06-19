package com.flashsale.commonlib.event.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Kafka event: seller cần hoàn tất yêu cầu Stripe (requirements).
 * Publish khi Stripe gửi webhook account.updated với requirements thay đổi.
 * Consumer: notification-service gửi notification cho seller.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SellerStripeRequirementPayload extends BaseKafkaEvent {

    private Long   sellerId;
    private String stripeAccountId;
    private String requirementType;   // "verification_needed" | "payouts_blocked" | "updates_needed"
    private String requirementReason; // Lý do cụ thể từ Stripe (requirements.disabled_reason)
    private String accountLinkUrl;    // URL Stripe Account Link để seller hoàn tất yêu cầu
    private Long   accountLinkExpiresAt; // Unix timestamp hết hạn
}
