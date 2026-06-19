package com.flashsale.paymentservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.paymentservice.domain.model.Transaction;
import com.flashsale.paymentservice.domain.repository.TransactionRepository;
import com.flashsale.paymentservice.support.KafkaPublisher;
import com.flashsale.paymentservice.support.StripeAmounts;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCommandService {

    private final TransactionRepository transactionRepository;
    private final SellerTransferService sellerTransferService;
    private final KafkaPublisher kafkaPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public void onPaymentRequested(String message) {
        Long parentOrderId = null;
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            parentOrderId = StripeAmounts.toLong(payload.get("parent_order_id"));
            Long userId = StripeAmounts.toLong(payload.get("user_id"));
            BigDecimal totalAmount = StripeAmounts.toBigDecimal(payload.get("total_amount"));

            if (parentOrderId == null || totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Ignore payment.requested with invalid payload: {}", message);
                return;
            }

            // Idempotency: skip if transaction already exists and is usable
            Transaction existing = transactionRepository.findByParentOrderId(parentOrderId).orElse(null);
            if (existing != null && ("PENDING".equals(existing.getStatus()) || "SUCCESS".equals(existing.getStatus()))) {
                log.info("Skip payment.requested — transaction already exists: parentOrderId={}, status={}",
                        parentOrderId, existing.getStatus());
                return;
            }

            // Create Stripe PaymentIntent
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(StripeAmounts.toStripeAmount(totalAmount))
                    .setCurrency("vnd")
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                                    .build()
                    )
                    .putMetadata("parent_order_id", String.valueOf(parentOrderId))
                    .putMetadata("user_id", userId != null ? String.valueOf(userId) : "")
                    .build();

            PaymentIntent pi = PaymentIntent.create(params);

            // Persist transaction
            Transaction tx = existing != null ? existing : new Transaction();
            tx.setParentOrderId(parentOrderId);
            tx.setAmount(totalAmount);
            tx.setStatus("PENDING");
            tx.setTransRef(StripeAmounts.buildTransRef(parentOrderId));
            tx.setRawResponse(pi.toJson());
            transactionRepository.save(tx);

            log.info("Payment initialized: parentOrderId={}, txId={}, piId={}", parentOrderId, tx.getId(), pi.getId());

            // Create SellerTransfer records from sub-order breakdown in payload
            sellerTransferService.createSellerTransferRecords(parentOrderId, payload, tx.getId());

        } catch (Exception e) {
            log.error("Failed to initialize payment from payment.requested: {}", e.getMessage(), e);
            if (parentOrderId != null) {
                kafkaPublisher.publish(KafkaTopics.PAYMENT_FAILED, String.valueOf(parentOrderId), Map.of(
                        "parent_order_id", parentOrderId,
                        "reason", "Khoi tao thanh toan that bai",
                        "timestamp", Instant.now().toString()
                ));
            }
        }
    }

    /**
     * Nhận ORDER_CANCELLED và ORDER_AUTO_CANCELLED từ order-service.
     * Nếu Transaction vẫn ở PENDING, hủy Stripe PaymentIntent để tránh
     * buyer vô tình thanh toán cho đơn đã bị hủy.
     * Sau đó publish payment.failed để ParentOrderPaymentSaga kết thúc.
     */
    @Transactional
    public void onOrderCancelled(String message) {
        Long parentOrderId = null;
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            parentOrderId = StripeAmounts.toLong(payload.get("parent_order_id"));
            if (parentOrderId == null) {
                log.warn("onOrderCancelled: missing parent_order_id in payload");
                return;
            }

            final Long finalParentOrderId = parentOrderId;
            Transaction tx = transactionRepository.findByParentOrderId(parentOrderId).orElse(null);

            if (tx == null) {
                // Đơn bị hủy trước khi payment-service xử lý payment.requested —
                // vẫn publish payment.failed để Saga có thể kết thúc sạch sẽ.
                log.warn("onOrderCancelled: no transaction for parentOrderId={} — publishing payment.failed for saga cleanup", parentOrderId);
                kafkaPublisher.publish(KafkaTopics.PAYMENT_FAILED, String.valueOf(finalParentOrderId), Map.of(
                        "parent_order_id", finalParentOrderId,
                        "reason",          "Order cancelled before payment",
                        "timestamp",       Instant.now().toString()
                ));
                return;
            }

            if (!"PENDING".equals(tx.getStatus())) {
                log.debug("onOrderCancelled: skip — transaction not PENDING: parentOrderId={}, status={}",
                        parentOrderId, tx.getStatus());
                return;
            }

            // Hủy Stripe PaymentIntent nếu vẫn còn khả dụng
            // (PI ID được lưu trong rawResponse dưới dạng JSON của Stripe PaymentIntent)
            String stripePiId = extractPiIdFromRawResponse(tx.getRawResponse());
            if (stripePiId != null) {
                try {
                    PaymentIntent pi = PaymentIntent.retrieve(stripePiId);
                    String piStatus = pi.getStatus();
                    if (!"canceled".equals(piStatus) && !"succeeded".equals(piStatus)) {
                        pi.cancel();
                        log.info("Stripe PI cancelled: parentOrderId={}, piId={}", parentOrderId, stripePiId);
                    }
                } catch (StripeException e) {
                    // PI không hủy được (đã expired hoặc lỗi Stripe) — vẫn tiếp tục cập nhật DB
                    log.error("Could not cancel Stripe PI {}: {}", stripePiId, e.getMessage());
                }
            }

            tx.setStatus("CANCELLED");
            transactionRepository.save(tx);

            kafkaPublisher.publish(KafkaTopics.PAYMENT_FAILED, String.valueOf(parentOrderId), Map.of(
                    "parent_order_id", parentOrderId,
                    "transaction_id",  tx.getId(),
                    "reason",          "Order cancelled",
                    "timestamp",       Instant.now().toString()
            ));
            log.info("Transaction cancelled after order cancellation: parentOrderId={}, txId={}", parentOrderId, tx.getId());

        } catch (Exception e) {
            log.error("Error processing order.cancelled for parentOrderId={}: {}", parentOrderId, e.getMessage(), e);
        }
    }

    /**
     * Extract the Stripe PaymentIntent ID from the rawResponse JSON stored on a Transaction.
     * Returns null if rawResponse is null or cannot be parsed.
     */
    private String extractPiIdFromRawResponse(String rawResponse) {
        if (rawResponse == null) return null;
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(rawResponse);
            return node.has("id") ? node.get("id").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
