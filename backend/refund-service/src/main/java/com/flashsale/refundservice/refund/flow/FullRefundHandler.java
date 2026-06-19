package com.flashsale.refundservice.refund.flow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.refundservice.domain.model.Refund;
import com.flashsale.refundservice.domain.model.Transaction;
import com.flashsale.refundservice.domain.repository.RefundRepository;
import com.flashsale.refundservice.domain.repository.TransactionRepository;
import com.flashsale.refundservice.stripe.SellerTransferReversalService;
import com.flashsale.refundservice.stripe.StripeRefundClient;
import com.flashsale.refundservice.support.KafkaPublisher;
import com.flashsale.refundservice.support.RefundTypeConverter;
import com.flashsale.commonlib.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class FullRefundHandler {

    private final RefundRepository refundRepository;
    private final TransactionRepository transactionRepository;
    private final StripeRefundClient stripeRefundClient;
    private final SellerTransferReversalService sellerTransferReversalService;
    private final KafkaPublisher kafkaPublisher;
    private final RefundTypeConverter typeConverter;
    private final ObjectMapper objectMapper;

    @Transactional
    public void onRefundFullRequested(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            Long parentOrderId = typeConverter.toLong(payload.get("parent_order_id"));
            Long userId        = typeConverter.toLong(payload.get("user_id"));
            java.util.UUID groupRef = typeConverter.parseUUID(payload.get("group_ref"));
            String initiatedBy = payload.get("initiated_by") != null
                    ? String.valueOf(payload.get("initiated_by"))
                    : "BUYER";
            String reasonType = payload.get("refund_reason_type") != null
                    ? String.valueOf(payload.get("refund_reason_type"))
                    : "BUYER_CANCEL";
            String reason = payload.get("reason") != null
                    ? String.valueOf(payload.get("reason"))
                    : "Full refund requested by buyer";
            boolean autoProcess = Boolean.TRUE.equals(payload.get("auto_process"))
                    || "true".equalsIgnoreCase(String.valueOf(payload.get("auto_process")));

            Transaction tx = transactionRepository.findByParentOrderId(parentOrderId).orElse(null);
            if (tx == null) {
                log.error("Transaction not found for parentOrderId={}, cannot create full refund", parentOrderId);
                return;
            }

            List<?> refundList = (List<?>) payload.get("refunds");
            if (refundList == null || refundList.isEmpty()) return;

            List<Long> createdIds = new ArrayList<>();
            for (Object raw : refundList) {
                @SuppressWarnings("unchecked")
                Map<String, Object> r = (Map<String, Object>) raw;
                Long orderId  = typeConverter.toLong(r.get("order_id"));
                BigDecimal amt = typeConverter.toBigDecimal(r.get("amount"));

                if (refundRepository.existsByOrderIdAndStatusIn(orderId, List.of("PENDING", "SUCCESS"))) {
                    log.warn("Duplicate REFUND_FULL_REQUESTED for orderId={}, skipping sub-order", orderId);
                    continue;
                }

                Refund refund = Refund.builder()
                        .transactionId(tx.getId())
                        .orderId(orderId)
                        .groupRef(groupRef)
                        .type("FULL")
                        .initiatedBy(initiatedBy)
                        .refundReasonType(reasonType)
                        .amount(amt)
                        .reason(reason)
                        .status("PENDING")
                        .build();
                refund = refundRepository.save(refund);

                if (autoProcess) {
                    try {
                        String stripeRefundId = stripeRefundClient.executeStripeRefund(tx.getId(), amt);
                        refund.setStatus("SUCCESS");
                        refund.setRefundRef(stripeRefundId);
                        refund.setReviewedAt(LocalDateTime.now());
                        sellerTransferReversalService.reverseSellerTransfer(orderId, amt, refund.getId());
                        refund = refundRepository.save(refund);
                    } catch (AppException e) {
                        log.error("Auto full refund failed for orderId={}: {}", orderId, e.getMessage());
                        refund.setStatus("FAILED");
                        refund = refundRepository.save(refund);
                    }
                }

                createdIds.add(refund.getId());

                Map<String, Object> refundCreatedEvent = new LinkedHashMap<>();
                refundCreatedEvent.put("refund_id", refund.getId());
                refundCreatedEvent.put("order_id", orderId);
                refundCreatedEvent.put("parent_order_id", parentOrderId);
                refundCreatedEvent.put("user_id", userId);
                refundCreatedEvent.put("amount", amt);
                refundCreatedEvent.put("type", "FULL");
                refundCreatedEvent.put("initiated_by", initiatedBy);
                refundCreatedEvent.put("refund_reason_type", reasonType);
                refundCreatedEvent.put("status", refund.getStatus());
                refundCreatedEvent.put("auto_process", autoProcess);
                refundCreatedEvent.put("timestamp", Instant.now().toString());
                kafkaPublisher.publish(KafkaTopics.REFUND_CREATED, String.valueOf(refund.getId()), refundCreatedEvent);
            }

            log.info("Full refund created from REFUND_FULL_REQUESTED: parentOrderId={}, refundCount={}",
                    parentOrderId, createdIds.size());

        } catch (Exception e) {
            log.error("Error processing REFUND_FULL_REQUESTED: {}", e.getMessage(), e);
        }
    }
}
