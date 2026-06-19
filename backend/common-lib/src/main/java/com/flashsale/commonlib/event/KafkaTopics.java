package com.flashsale.commonlib.event;

public final class KafkaTopics {

    private KafkaTopics() {
    }

    // Product
    public static final String PRODUCT_PENDING_REVIEW = "product.pending_review";
    public static final String PRODUCT_APPROVED = "product.approved";
    public static final String PRODUCT_ACTIVATED = "product.activated";
    public static final String PRODUCT_DEACTIVATED = "product.deactivated";
    public static final String PRODUCT_REJECTED = "product.rejected";
    public static final String PRODUCT_UPDATED = "product.updated";
    public static final String PRODUCT_DELETED = "product.deleted";
    public static final String VARIANT_PRICE_UPDATED = "variant.price_updated";
    public static final String VARIANT_STOCK_UPDATED = "variant.stock_updated";

    // Order
    public static final String ORDER_CREATED = "order.created";
    public static final String ORDER_SHIPPED = "order.shipped";
    public static final String ORDER_DELIVERED = "order.delivered";
    public static final String ORDER_RETURNED = "order.returned";
    public static final String ORDER_RETURNED_RTS = "order.returned";
    public static final String ORDER_CANCELLED = "order.cancelled";
    public static final String ORDER_AUTO_CANCELLED = "order.auto_cancelled";
    public static final String SELLER_ORDER_CANCELLED = "seller.order_cancelled";
    public static final String ORDER_PAYMENT_TIMEOUT = "order.payment_timeout";
    public static final String ORDER_CHECKOUT_SUBMITTED = "order.checkout_submitted";
    public static final String ORDER_PAID = "order.paid";
    public static final String ORDER_PAYMENT_FAILED = "order.payment_failed";

    // Payment / Stripe
    public static final String PAYMENT_REQUESTED = "payment.requested";
    public static final String PAYMENT_SUCCESS = "payment.success";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String STRIPE_ACCOUNT_SUSPENDED = "stripe.account_suspended";
    public static final String STRIPE_DISPUTE_CREATED = "stripe.dispute.created";
    public static final String STRIPE_DISPUTE_CLOSED = "stripe.dispute.closed";
    public static final String STRIPE_TRANSFER_REVERSED = "stripe.transfer.reversed";
    public static final String STRIPE_PAYOUT_FAILED = "stripe.payout.failed";
    public static final String SELLER_STRIPE_REQUIREMENT = "seller.stripe_requirement";

    // Payout / transfer
    public static final String PAYOUT_PROCESSED = "payout.processed";
    public static final String PAYOUT_FAILED = "payout.failed";
    public static final String TRANSFER_COMPLETED = "transfer.completed";
    public static final String SELLER_TRANSFER_ELIGIBLE = "seller.transfer.eligible";
    public static final String SELLER_TRANSFER_PAID_OUT = "seller.transfer.paid_out";
    public static final String SELLER_TRANSFER_FAILED = "seller.transfer.failed";

    // Refund
    public static final String REFUND_REQUESTED = "refund.requested";
    public static final String REFUND_FULL_REQUESTED = "refund.full_requested";
    public static final String REFUND_CREATED = "refund.created";
    public static final String REFUND_ADMIN_APPROVED = "refund.admin_approved";
    public static final String REFUND_REJECTED = "refund.rejected";
    public static final String REFUND_RTS_COMPLETED = "refund.rts_completed";
    public static final String REFUND_STRIPE_AUTO = "refund.stripe_auto";

    // Flash sale
    public static final String FLASH_SALE_SESSION_STARTED = "flash_sale.session_started";
    public static final String FLASH_SALE_SESSION_ENDED = "flash_sale.session_ended";
    public static final String FLASH_SALE_SESSION_CANCELLED = "flash_sale.session_cancelled";
    public static final String FLASH_SALE_SESSION_CREATED = "flash_sale.session_created";
    public static final String FLASH_SALE_ITEM_REGISTERED = "flash_sale.item_registered";
    public static final String FLASH_SALE_ITEM_APPROVED = "flash_sale.item_approved";
    public static final String FLASH_SALE_ITEM_REJECTED = "flash_sale.item_rejected";
    public static final String FLASH_SALE_ITEM_SOLD = "flash_sale.item_sold";
    public static final String FLASH_SALE_REMINDER = "flash_sale.reminder";
    public static final String FLASH_SALE_PRICE_SYNC = "flash_sale.price_sync";

    // Stock reservation
    public static final String STOCK_RESERVATION_EXPIRED = "stock.reservation.expired";

    // Identity
    public static final String ACCOUNT_UPDATED = "account.updated";
    public static final String SELLER_REGISTERED = "seller.registered";

    // Category
    public static final String CATEGORY_UPDATED = "category.updated";

    // AI chat
    public static final String AI_CHAT_MESSAGE_SENT = "ai_chat.message_sent";
    public static final String AI_CHAT_TOOL_CALL_EXECUTED = "ai_chat.tool_call_executed";
    public static final String AI_CHAT_CONFIRMATION_RESOLVED = "ai_chat.confirmation_resolved";
    public static final String AI_SESSION_CREATED = "ai.session.created";
    public static final String AI_SESSION_CLOSED = "ai.session.closed";
    public static final String AI_CHAT_MESSAGE_RECEIVED = "ai.chat.message_received";
    public static final String AI_CONFIRMATION_CONFIRMED = "ai.confirmation.confirmed";
    public static final String AI_CONFIRMATION_REJECTED = "ai.confirmation.rejected";

    // Request-reply
    public static final String ORDER_PAYMENT_STATUS_REQUEST = "order.payment_status.request";
    public static final String ORDER_PAYMENT_STATUS_RESPONSE = "order.payment_status.response";
    public static final String ORDER_ADDRESS_REQUEST = "order.address.request";
    public static final String ORDER_ADDRESS_RESPONSE = "order.address.response";
    public static final String ORDER_REFUNDS_REQUEST = "order.refunds.request";
    public static final String ORDER_REFUNDS_RESPONSE = "order.refunds.response";
    public static final String ORDER_REFUND_PRESIGNED_URL_REQUEST = "order.refund_presigned_url.request";
    public static final String ORDER_REFUND_PRESIGNED_URL_RESPONSE = "order.refund_presigned_url.response";
    public static final String SEARCH_INDEX_DATA_REQUEST = "search.index_data.request";
    public static final String SEARCH_INDEX_DATA_RESPONSE = "search.index_data.response";
}
