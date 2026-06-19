package com.flashsale.refundservice.refund.flow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.refundservice.domain.model.Refund;
import com.flashsale.refundservice.domain.model.Transaction;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class StripeAutoRefundHandler {

    private final RefundRepository refundRepository;
    private final TransactionRepository transactionRepository;
    private final KafkaPublisher kafkaPublisher;
    private final RefundTypeConverter typeConverter;
    private final ObjectMapper objectMapper;

    @Transactional
    public void onRefundStripeAuto(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            String chargeId = (String) payload.get("charge_id");
            Long amountRefunded = typeConverter.toLong(payload.get("amount_refunded"));

            // Tìm transaction có charge này trong raw_response
            Transaction tx = transactionRepository.findByRawResponseContaining(chargeId).orElse(null);

            if (tx == null) {
                log.warn("REFUND_STRIPE_AUTO: No transaction found for chargeId={} — likely external Stripe Dashboard refund", chargeId);
                return;
            }

            if (refundRepository.existsByOrderIdAndStatusIn(tx.getParentOrderId(), List.of("PENDING", "SUCCESS"))) {
                log.warn("REFUND_STRIPE_AUTO: Refund already exists for parentOrderId={}, skipping", tx.getParentOrderId());
                return;
            }

            BigDecimal amount = amountRefunded != null ? BigDecimal.valueOf(amountRefunded) : tx.getAmount();

            Refund refund = Refund.builder()
                    .transactionId(tx.getId())
                    .orderId(tx.getParentOrderId())
                    .type("FULL")
                    .initiatedBy("SYSTEM")
                    .refundReasonType("CHARGEBACK")
                    .amount(amount)
                    .reason("Stripe chargeback — chargeId=" + chargeId)
                    .status("SUCCESS")
                    .refundRef(chargeId)
                    .reviewedAt(LocalDateTime.now())
                    .build();
            refundRepository.save(refund);

            kafkaPublisher.publish(KafkaTopics.REFUND_CREATED, String.valueOf(refund.getId()), Map.of(
                    "refund_id", refund.getId(),
                    "order_id",  tx.getParentOrderId(),
                    "amount",    amount,
                    "type",      "FULL",
                    "status",    "SUCCESS",
                    "charge_id", chargeId,
                    "timestamp", Instant.now().toString()
            ));

            log.warn("REFUND_STRIPE_AUTO: Chargeback recorded — refundId={}, chargeId={}, amount={}",
                    refund.getId(), chargeId, amount);

        } catch (Exception e) {
            log.error("Error processing REFUND_STRIPE_AUTO: {}", e.getMessage(), e);
        }
    }
}
