package com.flashsale.paymentservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.paymentservice.config.StripeConfig;
import com.flashsale.paymentservice.domain.model.SellerStripeAccount;
import com.flashsale.paymentservice.domain.model.SellerTransfer;
import com.flashsale.paymentservice.domain.repository.SellerStripeAccountRepository;
import com.flashsale.paymentservice.domain.repository.SellerTransferRepository;
import com.flashsale.paymentservice.support.StripeAmounts;
import com.stripe.model.PaymentIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SellerTransferService {

    private final SellerTransferRepository sellerTransferRepository;
    private final SellerStripeAccountRepository sellerStripeAccountRepository;
    private final StripeConfig stripeConfig;
    private final ObjectMapper objectMapper;

    /**
     * Parses the sub-order list from payment.requested payload and creates
     * PENDING SellerTransfer records so that when payment succeeds,
     * Stripe transfers can be executed immediately.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public void createSellerTransferRecords(Long parentOrderId, Map<String, Object> payload, Long transactionId) {
        List<?> orders = (List<?>) payload.get("orders");
        if (orders == null || orders.isEmpty()) {
            log.warn("payment.requested for parentOrderId={} has no orders array — seller transfers skipped", parentOrderId);
            return;
        }

        double feePct = stripeConfig.getPlatformFeePercentage();

        for (Object raw : orders) {
            Map<String, Object> order = (Map<String, Object>) raw;
            Long orderId   = StripeAmounts.toLong(order.get("order_id"));
            Long sellerId  = StripeAmounts.toLong(order.get("seller_id"));
            BigDecimal amount = StripeAmounts.toBigDecimal(order.get("amount"));

            if (orderId == null || sellerId == null || amount == null) continue;

            // Idempotency: skip if transfer record already exists for this order
            if (sellerTransferRepository.findByOrderId(orderId).isPresent()) {
                log.info("SellerTransfer already exists for orderId={}, skipping", orderId);
                continue;
            }

            BigDecimal fee       = amount.multiply(BigDecimal.valueOf(feePct / 100.0)).setScale(0, RoundingMode.HALF_UP);
            BigDecimal netAmount = amount.subtract(fee);

            SellerTransfer st = SellerTransfer.builder()
                    .orderId(orderId)
                    .parentOrderId(parentOrderId)
                    .sellerId(sellerId)
                    .transferAmount(amount)
                    .status("PENDING")
                    .build();
            sellerTransferRepository.save(st);

            log.info("SellerTransfer record created: orderId={}, sellerId={}, amount={}, fee={}, net={}",
                    orderId, sellerId, amount, fee, netAmount);
        }
    }

    /**
     * Validates seller accounts and transitions PENDING transfers to AWAITING_DELIVERY.
     * No actual Stripe transfer is created yet — the money stays in the platform balance.
     * Actual payout happens after the return window expires (see PayoutScheduler).
     */
    @Transactional
    public void createSellerTransfers(Long parentOrderId, PaymentIntent pi) {
        List<SellerTransfer> pendingTransfers = sellerTransferRepository.findAllByParentOrderId(parentOrderId)
                .stream()
                .filter(t -> "PENDING".equals(t.getStatus()))
                .toList();

        if (pendingTransfers.isEmpty()) {
            log.warn("No pending seller transfers for parentOrderId={}", parentOrderId);
            return;
        }

        for (SellerTransfer st : pendingTransfers) {
            SellerStripeAccount sellerAccount = sellerStripeAccountRepository
                    .findBySellerId(st.getSellerId()).orElse(null);

            if (sellerAccount == null || !Boolean.TRUE.equals(sellerAccount.getChargesEnabled())) {
                log.warn("Seller {} has no active Stripe account — skipping transfer for orderId={}",
                        st.getSellerId(), st.getOrderId());
                st.setStatus("SKIPPED");
            } else {
                st.setStatus("AWAITING_DELIVERY");
            }
            sellerTransferRepository.save(st);
        }

        log.info("SellerTransitions transitioned to AWAITING_DELIVERY for parentOrderId={}", parentOrderId);
    }

    /**
     * Listens for ORDER_DELIVERED and schedules the seller payout deadline.
     * Transitions SellerTransfer from AWAITING_DELIVERY → RETURN_WINDOW
     * with payout_eligible_at = delivered_at + 7 days.
     */
    @Transactional
    public void onOrderDelivered(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            Long orderId = StripeAmounts.toLong(payload.get("order_id"));

            if (orderId == null) {
                log.warn("onOrderDelivered: missing order_id in payload");
                return;
            }

            SellerTransfer st = sellerTransferRepository.findByOrderId(orderId).orElse(null);
            if (st == null) {
                log.warn("onOrderDelivered: no SellerTransfer for orderId={}", orderId);
                return;
            }

            if (!"AWAITING_DELIVERY".equals(st.getStatus())) {
                log.debug("onOrderDelivered: skip — SellerTransfer orderId={} status={} (expected AWAITING_DELIVERY)",
                        orderId, st.getStatus());
                return;
            }

            LocalDateTime deliveredAt = LocalDateTime.now();
            LocalDateTime payoutEligibleAt = deliveredAt.plusDays(7);

            st.setDeliveredAt(deliveredAt);
            st.setPayoutEligibleAt(payoutEligibleAt);
            st.setStatus("RETURN_WINDOW");
            sellerTransferRepository.save(st);

            log.info("Seller payout scheduled: orderId={}, sellerId={}, deliveredAt={}, payoutEligibleAt={}",
                    orderId, st.getSellerId(), deliveredAt, payoutEligibleAt);

        } catch (Exception e) {
            log.error("Failed to process ORDER_DELIVERED: {}", e.getMessage(), e);
        }
    }
}
