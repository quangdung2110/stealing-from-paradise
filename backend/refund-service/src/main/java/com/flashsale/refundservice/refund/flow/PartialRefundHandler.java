package com.flashsale.refundservice.refund.flow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.refundservice.domain.model.Refund;
import com.flashsale.refundservice.domain.model.RefundItem;
import com.flashsale.refundservice.domain.model.Transaction;
import com.flashsale.refundservice.domain.repository.RefundItemRepository;
import com.flashsale.refundservice.domain.repository.RefundRepository;
import com.flashsale.refundservice.domain.repository.TransactionRepository;
import com.flashsale.refundservice.support.KafkaPublisher;
import com.flashsale.refundservice.support.RefundTypeConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PartialRefundHandler {

    private final RefundRepository refundRepository;
    private final RefundItemRepository refundItemRepository;
    private final TransactionRepository transactionRepository;
    private final KafkaPublisher kafkaPublisher;
    private final RefundTypeConverter typeConverter;
    private final ObjectMapper objectMapper;

    @Transactional
    public void onRefundRequested(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            Long orderId       = typeConverter.toLong(payload.get("order_id"));
            Long parentOrderId = typeConverter.toLong(payload.get("parent_order_id"));
            Long userId        = typeConverter.toLong(payload.get("user_id"));
            java.util.UUID groupRef = typeConverter.parseUUID(payload.get("group_ref"));
            String reason      = (String) payload.get("reason");
            BigDecimal amount  = typeConverter.toBigDecimal(payload.get("amount"));
            String reasonType  = (String) payload.get("refund_reason_type");

            // Idempotency: bỏ qua nếu đã có PENDING refund cho đơn này
            if (refundRepository.existsByOrderIdAndStatusIn(orderId, List.of("PENDING", "SUCCESS"))) {
                log.warn("Duplicate REFUND_REQUESTED for orderId={}, skipping", orderId);
                return;
            }

            // Tìm transaction theo parentOrderId
            Transaction tx = transactionRepository.findByParentOrderId(parentOrderId).orElse(null);
            if (tx == null) {
                log.error("Transaction not found for parentOrderId={}, cannot create refund", parentOrderId);
                return;
            }

            List<String> evidenceImages = typeConverter.toStringList(payload.get("evidence_images"));

            Refund refund = Refund.builder()
                    .transactionId(tx.getId())
                    .orderId(orderId)
                    .groupRef(groupRef)
                    .type("PARTIAL")
                    .initiatedBy("BUYER")
                    .refundReasonType(reasonType)
                    .amount(amount)
                    .reason(reason)
                    .status("PENDING")
                    .evidenceImages(evidenceImages.isEmpty() ? null : evidenceImages)
                    .build();
            refund = refundRepository.save(refund);

            // Tạo RefundItems
            List<?> items = (List<?>) payload.get("items");
            if (items != null) {
                for (Object raw : items) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> item = (Map<String, Object>) raw;
                    RefundItem ri = RefundItem.builder()
                            .refundId(refund.getId())
                            .itemId(typeConverter.toLong(item.get("order_item_id")))
                            .quantity(typeConverter.toInt(item.get("quantity")))
                            .refundAmount(typeConverter.toBigDecimal(item.get("refund_amount")))
                            .itemReason((String) item.get("item_reason"))
                            .productName((String) item.get("product_name"))
                            .imageSnapshot((String) item.get("image_snapshot"))
                            .status("PENDING")
                            .build();
                    refundItemRepository.save(ri);
                }
            }

            kafkaPublisher.publish(KafkaTopics.REFUND_CREATED, String.valueOf(refund.getId()), Map.of(
                    "refund_id", refund.getId(),
                    "order_id",  orderId,
                    "user_id",   userId,
                    "amount",    amount,
                    "type",      "PARTIAL",
                    "status",    "PENDING",
                    "timestamp", Instant.now().toString()
            ));

            log.info("Refund created from REFUND_REQUESTED: refundId={}, orderId={}, amount={}",
                    refund.getId(), orderId, amount);

        } catch (Exception e) {
            log.error("Error processing REFUND_REQUESTED: {}", e.getMessage(), e);
        }
    }
}
