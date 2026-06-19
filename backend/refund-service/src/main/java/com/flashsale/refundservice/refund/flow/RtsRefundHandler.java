package com.flashsale.refundservice.refund.flow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.refundservice.domain.model.Refund;
import com.flashsale.refundservice.domain.model.RefundItem;
import com.flashsale.refundservice.domain.model.Transaction;
import com.flashsale.refundservice.domain.repository.RefundItemRepository;
import com.flashsale.refundservice.domain.repository.RefundRepository;
import com.flashsale.refundservice.domain.repository.TransactionRepository;
import com.flashsale.refundservice.stripe.SellerTransferReversalService;
import com.flashsale.refundservice.stripe.StripeRefundClient;
import com.flashsale.refundservice.support.KafkaPublisher;
import com.flashsale.refundservice.support.RefundTypeConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RtsRefundHandler {

    private final RefundRepository refundRepository;
    private final RefundItemRepository refundItemRepository;
    private final TransactionRepository transactionRepository;
    private final StripeRefundClient stripeRefundClient;
    private final SellerTransferReversalService sellerTransferReversalService;
    private final KafkaPublisher kafkaPublisher;
    private final RefundTypeConverter typeConverter;
    private final ObjectMapper objectMapper;

    @Transactional
    public void onOrderReturnedRts(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            Long orderId       = typeConverter.toLong(payload.get("order_id"));
            Long parentOrderId = typeConverter.toLong(payload.get("parent_order_id"));
            Long userId        = typeConverter.toLong(payload.get("user_id"));
            BigDecimal amount  = typeConverter.toBigDecimal(payload.get("total_amount"));
            String returnTracking = (String) payload.get("return_tracking_number");
            List<String> evidenceImages = typeConverter.toStringList(payload.get("evidence_images"));

            // Idempotency
            if (refundRepository.existsByOrderIdAndStatusIn(orderId, List.of("PENDING", "SUCCESS"))) {
                log.warn("RTS refund already exists for orderId={}, skipping", orderId);
                return;
            }

            Transaction tx = transactionRepository.findByParentOrderId(parentOrderId).orElse(null);
            if (tx == null) {
                log.error("Transaction not found for parentOrderId={} (RTS), cannot refund", parentOrderId);
                return;
            }

            // Tạo Refund record
            Refund refund = Refund.builder()
                    .transactionId(tx.getId())
                    .orderId(orderId)
                    .type("FULL")
                    .initiatedBy("SELLER")
                    .refundReasonType("RETURN_TO_SENDER")
                    .amount(amount)
                    .reason("Hàng hoàn về Seller (Return To Sender)")
                    .status("PENDING")
                    .evidenceImages(evidenceImages.isEmpty() ? null : evidenceImages)
                    .build();
            refund = refundRepository.save(refund);

            // Cập nhật tracking trên RefundItem nếu có
            if (returnTracking != null && !returnTracking.isBlank()) {
                RefundItem ri = RefundItem.builder()
                        .refundId(refund.getId())
                        .itemId(orderId) // placeholder — all items of this order
                        .quantity(1)
                        .refundAmount(amount)
                        .itemReason("Return To Sender")
                        .status("PENDING")
                        .returnTrackingNumber(returnTracking)
                        .build();
                refundItemRepository.save(ri);
            }

            // Thực hiện Stripe refund tự động
            String stripeRefundId;
            try {
                stripeRefundId = stripeRefundClient.executeStripeRefund(tx.getId(), amount);
                refund.setStatus("SUCCESS");
                refund.setRefundRef(stripeRefundId);
                refund.setReviewedAt(LocalDateTime.now());

                // Reverse toàn bộ transfer đã gửi cho Seller (RTS = full refund)
                sellerTransferReversalService.reverseSellerTransfer(orderId, amount, refund.getId());
            } catch (AppException e) {
                log.error("Stripe refund failed for RTS orderId={}: {}", orderId, e.getMessage());
                refund.setStatus("FAILED");
                stripeRefundId = null;
            }
            refundRepository.save(refund);

            // Publish REFUND_RTS_COMPLETED
            Map<String, Object> event = new HashMap<>();
            event.put("refund_id",    refund.getId());
            event.put("order_id",     orderId);
            event.put("user_id",      userId);
            event.put("amount",       amount);
            event.put("status",       refund.getStatus());
            event.put("stripe_refund_id", stripeRefundId != null ? stripeRefundId : "");
            event.put("timestamp",    Instant.now().toString());
            kafkaPublisher.publish(KafkaTopics.REFUND_RTS_COMPLETED, String.valueOf(orderId), event);

            log.info("RTS refund processed: refundId={}, orderId={}, status={}, stripeRefundId={}",
                    refund.getId(), orderId, refund.getStatus(), stripeRefundId);

        } catch (Exception e) {
            log.error("Error processing ORDER_RETURNED_RTS: {}", e.getMessage(), e);
        }
    }
}
