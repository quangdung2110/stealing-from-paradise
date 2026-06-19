package com.flashsale.refundservice.stripe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.refundservice.domain.model.Transaction;
import com.flashsale.refundservice.domain.repository.TransactionRepository;
import com.stripe.exception.StripeException;
import com.stripe.param.RefundCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class StripeRefundClient {

    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;

    public String executeStripeRefund(Long transactionId, BigDecimal amount) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Transaction không tồn tại"));

        String stripePiId = extractPiIdFromRawResponse(tx.getRawResponse());
        if (stripePiId == null) {
            log.warn("No Stripe PI ID for transaction {}, skipping Stripe refund call", transactionId);
            return "manual_refund_" + transactionId;
        }

        try {
            long stripeAmount = amount.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(stripePiId)
                    .setAmount(stripeAmount)
                    .build();
            com.stripe.model.Refund stripeRefund = com.stripe.model.Refund.create(params);
            log.info("Stripe refund created: refundId={}, amount={}", stripeRefund.getId(), stripeAmount);
            return stripeRefund.getId();
        } catch (StripeException e) {
            if (stripePiId != null && stripePiId.startsWith("pi_")) {
                log.error("Stripe refund failed for transaction {}: {}", transactionId, e.getMessage(), e);
                throw new AppException(ErrorCode.INTERNAL_ERROR, "Stripe refund failed: " + e.getMessage());
            } else {
                log.warn("Stripe refund failed for transaction {} (dev mode or mock PI): {}. Using manual fallback.", transactionId, e.getMessage());
                return "manual_refund_" + transactionId;
            }
        }
    }

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
