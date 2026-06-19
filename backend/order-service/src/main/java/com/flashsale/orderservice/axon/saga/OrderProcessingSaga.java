package com.flashsale.orderservice.axon.saga;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.orderservice.axon.event.*;
import com.flashsale.orderservice.domain.model.Order;
import com.flashsale.orderservice.domain.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.SagaLifecycle;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OrderProcessingSaga — Axon Saga orchestrating the full order lifecycle.
 *
 * One saga instance per sub-order (Order entity), keyed by orderId.
 *
 * Responsibilities:
 * - Schedule and cancel payment timeout deadline (5 min default)
 * - Schedule and cancel shipping deadline (shippingDeadline from Order)
 * - Publish all order lifecycle Kafka events to downstream services
 * - Auto-cancel PENDING orders whose payment window has expired
 *
 * State transitions handled:
 *   PENDING → [PaymentTimeout] → CANCELLED (auto, publishes order.auto_cancelled)
 *   PENDING → PAID   → SHIPPING → DELIVERED  (terminal — @EndSaga)
 *   PENDING → CANCELLED                       (terminal — @EndSaga)
 *   SHIPPING → RETURNED                       (terminal — @EndSaga)
 */
@Saga
@Slf4j
public class OrderProcessingSaga {

    private static final String PAYMENT_TIMEOUT  = "payment-timeout";
    private static final String SHIPPING_TIMEOUT = "shipping-timeout";

    // ─── Injected Spring beans (transient = not serialized by Axon) ───────────

    @Autowired private transient DeadlineManager deadlineManager;
    @Autowired private transient KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private transient ObjectMapper objectMapper;
    @Autowired private transient OrderRepository orderRepository;

    // ─── Saga state (serialized by Axon / XStream) ────────────────────────────

    private Long orderId;
    private Long parentOrderId;
    private Long userId;
    private Long sellerId;
    private BigDecimal totalAmount;
    private boolean isFlashSale;
    private String sessionId;
    private String paymentDeadlineId;
    private String shippingDeadlineId;

    // ─── Start ────────────────────────────────────────────────────────────────

    @StartSaga
    @SagaEventHandler(associationProperty = "orderId")
    public void on(OrderCreatedEvent event) {
        orderId           = event.getOrderId();
        parentOrderId     = event.getParentOrderId();
        userId            = event.getUserId();
        sellerId          = event.getSellerId();
        totalAmount       = event.getTotalAmount();
        isFlashSale       = event.isFlashSale();
        sessionId         = event.getSessionId();

        // Secondary association so payment events can also route by parentOrderId if needed
        SagaLifecycle.associateWith("parentOrderId", String.valueOf(parentOrderId));

        // Schedule payment timeout
        Duration timeout = computeTimeout(null);
        paymentDeadlineId = deadlineManager.schedule(timeout, PAYMENT_TIMEOUT);

        log.info("[Saga][{}] Started — parentOrderId={}, paymentTimeout={}m",
                orderId, parentOrderId, timeout.toMinutes());

        // Publish order.created (downstream: notification-service, worker-service)
        Map<String, Object> payload = new HashMap<>();
        payload.put("parent_order_id",     parentOrderId);
        payload.put("order_id",            orderId);
        payload.put("user_id",             userId);
        payload.put("seller_id",           sellerId);
        payload.put("order_code",          event.getOrderCode());
        payload.put("total_amount",        totalAmount);
        payload.put("is_flash_sale",       isFlashSale);
        payload.put("session_id",          sessionId != null ? sessionId : "");
        payload.put("timestamp",           Instant.now().toString());
        send(KafkaTopics.ORDER_CREATED, String.valueOf(parentOrderId), payload);
    }

    // ─── Payment ──────────────────────────────────────────────────────────────

    @SagaEventHandler(associationProperty = "orderId")
    public void on(OrderPaidEvent event) {
        cancelDeadline(PAYMENT_TIMEOUT, paymentDeadlineId);
        paymentDeadlineId = null;
        log.info("[Saga][{}] PAID", orderId);
        // payment.success already published by payment-service — no Kafka publish needed here
    }

    // ─── Shipping ─────────────────────────────────────────────────────────────

    @SagaEventHandler(associationProperty = "orderId")
    public void on(OrderShippedEvent event) {
        // Cancel any prior shipping deadline before rescheduling
        cancelDeadline(SHIPPING_TIMEOUT, shippingDeadlineId);
        Duration timeout = computeTimeout(event.getShippingDeadline());
        shippingDeadlineId = deadlineManager.schedule(timeout, SHIPPING_TIMEOUT);

        log.info("[Saga][{}] SHIPPED — tracking={}, deadline={}m",
                orderId, event.getTrackingNumber(), timeout.toMinutes());

        Map<String, Object> payload = new HashMap<>();
        payload.put("order_id",        orderId);
        payload.put("user_id",         userId);
        payload.put("seller_id",       sellerId);
        payload.put("tracking_number", event.getTrackingNumber());
        payload.put("carrier",         event.getCarrier() != null ? event.getCarrier() : "");
        payload.put("shipped_at",      Instant.now().toString());
        send(KafkaTopics.ORDER_SHIPPED, String.valueOf(orderId), payload);
    }

    // ─── Delivery ─────────────────────────────────────────────────────────────

    @EndSaga
    @SagaEventHandler(associationProperty = "orderId")
    public void on(OrderDeliveredEvent event) {
        cancelDeadline(SHIPPING_TIMEOUT, shippingDeadlineId);
        shippingDeadlineId = null;

        log.info("[Saga][{}] DELIVERED by={}", orderId, event.getDeliveredBy());

        Map<String, Object> payload = new HashMap<>();
        payload.put("order_id",       orderId);
        payload.put("user_id",        userId);
        payload.put("seller_id",      sellerId);
        payload.put("total_amount",   event.getTotalAmount());
        payload.put("delivered_by",   event.getDeliveredBy());
        payload.put("delivered_at",   Instant.now().toString());
        payload.put("timestamp",      Instant.now().toString());
        send(KafkaTopics.ORDER_DELIVERED, String.valueOf(orderId), payload);
    }

    // ─── Cancellation ─────────────────────────────────────────────────────────

    @EndSaga
    @SagaEventHandler(associationProperty = "orderId")
    public void on(OrderCancelledEvent event) {
        cancelDeadline(PAYMENT_TIMEOUT,  paymentDeadlineId);
        cancelDeadline(SHIPPING_TIMEOUT, shippingDeadlineId);
        paymentDeadlineId = shippingDeadlineId = null;

        log.info("[Saga][{}] CANCELLED by={}", orderId, event.getCancelledBy());

        Map<String, Object> payload = new HashMap<>();
        payload.put("order_id",       orderId);
        payload.put("parent_order_id", parentOrderId);
        payload.put("user_id",        userId);
        payload.put("seller_id",      sellerId);
        payload.put("session_id",     sessionId != null ? sessionId : "");
        payload.put("cancelled_by",   event.getCancelledBy());
        payload.put("cancel_reason",  event.getCancelReason());
        payload.put("total_amount",   event.getTotalAmount());
        payload.put("timestamp",      Instant.now().toString());
        send(KafkaTopics.ORDER_CANCELLED, String.valueOf(orderId), payload);

        if ("SELLER".equals(event.getCancelledBy())) {
            Map<String, Object> sellerPayload = new HashMap<>();
            sellerPayload.put("order_id",        orderId);
            sellerPayload.put("parent_order_id", parentOrderId);
            sellerPayload.put("seller_id",       sellerId);
            sellerPayload.put("buyer_id",        userId);
            sellerPayload.put("customer_id",     userId);
            sellerPayload.put("session_id",      sessionId != null ? sessionId : "");
            sellerPayload.put("cancel_reason",   event.getCancelReason());
            sellerPayload.put("refund_amount",   event.getTotalAmount());
            sellerPayload.put("currency",        "VND");
            sellerPayload.put("cancelled_at",    Instant.now().toString());
            sellerPayload.put("timestamp",       Instant.now().toString());
            send(KafkaTopics.SELLER_ORDER_CANCELLED, String.valueOf(sellerId), sellerPayload);
        }
    }

    // ─── Return To Sender ─────────────────────────────────────────────────────

    @EndSaga
    @SagaEventHandler(associationProperty = "orderId")
    public void on(OrderReturnedEvent event) {
        log.info("[Saga][{}] RETURNED — returnTracking={}", orderId, event.getReturnTrackingNumber());

        Map<String, Object> payload = new HashMap<>();
        payload.put("order_id",               orderId);
        payload.put("parent_order_id",        parentOrderId);
        payload.put("user_id",                userId);
        payload.put("seller_id",              sellerId);
        payload.put("session_id",             sessionId != null ? sessionId : "");
        payload.put("refund_reason_type",     "RETURN_TO_SENDER");
        payload.put("return_tracking_number", event.getReturnTrackingNumber() != null
                ? event.getReturnTrackingNumber() : "");
        payload.put("total_amount",           event.getAmount());
        payload.put("evidence_count",         event.getEvidenceCount());
        payload.put("evidence_images",        event.getEvidenceUrls() != null
                ? event.getEvidenceUrls() : List.of());
        payload.put("timestamp",              Instant.now().toString());
        send(KafkaTopics.ORDER_RETURNED_RTS, String.valueOf(orderId), payload);
    }

    // ─── Deadline Handlers ────────────────────────────────────────────────────

    /**
     * Payment timeout: auto-cancel the order and publish order.auto_cancelled.
     * worker-service (JOB-13) is a safety net; this fires first if Axon Server is healthy.
     */
    @DeadlineHandler(deadlineName = PAYMENT_TIMEOUT)
    public void onPaymentTimeout() {
        log.warn("[Saga][{}] Payment timeout — auto-cancelling", orderId);

        // Update JPA state directly (no transaction context — Axon handles its own tx)
        Optional<Order> opt = orderRepository.findById(orderId);
        if (opt.isPresent()) {
            Order order = opt.get();
            if ("PENDING".equals(order.getStatus())) {
                order.setStatus("CANCELLED");
                order.setCancelledBy("SYSTEM");
                order.setCancelReason("Payment timeout");
                orderRepository.save(order);
            }
        }

        // Publish order.payment_timeout (for notification-service)
        Map<String, Object> timeoutPayload = new HashMap<>();
        timeoutPayload.put("parent_order_id", parentOrderId);
        timeoutPayload.put("order_ids", java.util.List.of(orderId));
        timeoutPayload.put("session_id", sessionId != null ? sessionId : "");
        timeoutPayload.put("timeout_threshold_minutes", 5);
        timeoutPayload.put("timeout_reason", "PAYMENT_NOT_COMPLETED");
        timeoutPayload.put("auto_cancelled_at", Instant.now().toString());
        send(KafkaTopics.ORDER_PAYMENT_TIMEOUT, String.valueOf(parentOrderId), timeoutPayload);

        Map<String, Object> payload = new HashMap<>();
        payload.put("order_id",        orderId);
        payload.put("parent_order_id", parentOrderId);
        payload.put("user_id",         userId);
        payload.put("seller_id",       sellerId);
        payload.put("session_id",      sessionId != null ? sessionId : "");
        payload.put("cancelled_by",    "SYSTEM");
        payload.put("cancel_reason",   "Payment timeout");
        payload.put("total_amount", totalAmount);
        payload.put("timestamp",    Instant.now().toString());
        send(KafkaTopics.ORDER_AUTO_CANCELLED, String.valueOf(orderId), payload);

        SagaLifecycle.end();
    }

    /**
     * Shipping deadline exceeded — worker-service (JOB-22) handles auto-delivery.
     * Saga keeps itself alive; it ends when OrderDeliveredEvent fires.
     */
    @DeadlineHandler(deadlineName = SHIPPING_TIMEOUT)
    public void onShippingTimeout() {
        log.warn("[Saga][{}] Shipping deadline passed — worker-service will auto-deliver", orderId);
        // Do not end saga here; wait for OrderDeliveredEvent from worker-service
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Duration computeTimeout(LocalDateTime deadline) {
        if (deadline == null) return Duration.ofMinutes(5);
        Duration d = Duration.between(LocalDateTime.now(), deadline);
        return (d.isNegative() || d.isZero()) ? Duration.ofSeconds(30) : d;
    }

    private void cancelDeadline(String name, String id) {
        if (id == null) return;
        try {
            deadlineManager.cancelSchedule(name, id);
        } catch (Exception e) {
            log.warn("[Saga][{}] Could not cancel deadline {} id={}: {}", orderId, name, id, e.getMessage());
        }
    }

    private void send(String topic, String key, Map<String, Object> payload) {
        try {
            kafkaTemplate.send(topic, key, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            log.error("[Saga][{}] Failed to serialize payload for topic {}: {}", orderId, topic, e.getMessage());
        }
    }
}
