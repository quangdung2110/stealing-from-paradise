package com.flashsale.paymentservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.paymentservice.config.StripeConfig;
import com.flashsale.paymentservice.domain.model.SellerStripeAccount;
import com.flashsale.paymentservice.domain.model.SellerTransfer;
import com.flashsale.paymentservice.domain.repository.SellerStripeAccountRepository;
import com.flashsale.paymentservice.domain.repository.SellerTransferRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Transfer;
import com.stripe.param.TransferCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scheduled job that processes seller payouts after the return window expires.
 *
 * Runs every 5 minutes, finds SellerTransfers in RETURN_WINDOW status
 * whose payout_eligible_at has passed, calculates the platform commission,
 * and executes a Stripe Transfer to the seller's connected account.
 *
 * Idempotency: each transfer transitions through
 *   RETURN_WINDOW → READY_FOR_PAYOUT → PAID_OUT
 * The cron query only picks up RETURN_WINDOW records, so concurrent
 * instances never double-process.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutScheduler {

    private final SellerTransferRepository sellerTransferRepository;
    private final SellerStripeAccountRepository sellerStripeAccountRepository;
    private final StripeConfig stripeConfig;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Processes eligible payouts every 5 minutes.
     *
     * Payout eligibility: status = RETURN_WINDOW AND payout_eligible_at <= NOW()
     * Batch size: 100 per cycle to bound execution time.
     */
    @Scheduled(cron = "${payout.schedule.cron:0 */5 * * * *}")
    @SchedulerLock(name = "payment-process-eligible-payouts", lockAtMostFor = "PT4M", lockAtLeastFor = "PT10S")
    @Transactional
    public void processEligiblePayouts() {
        List<SellerTransfer> eligible = sellerTransferRepository
                .findByStatusAndPayoutEligibleAtBefore("RETURN_WINDOW",
                        LocalDateTime.now(), PageRequest.of(0, 100));

        if (eligible.isEmpty()) {
            return;
        }

        log.info("PayoutScheduler: processing {} eligible seller transfers", eligible.size());

        for (SellerTransfer st : eligible) {
            processSinglePayout(st);
        }
    }

    private void processSinglePayout(SellerTransfer st) {
        try {
            // Atomically claim this transfer to prevent double-processing
            st.setStatus("READY_FOR_PAYOUT");
            sellerTransferRepository.save(st);

            // Publish seller.transfer.eligible event
            publishPayoutEvent(st, "SELLER_TRANSFER_ELIGIBLE", Map.of(
                    "transfer_id", st.getId(),
                    "seller_id", st.getSellerId(),
                    "order_id", st.getOrderId(),
                    "amount", st.getTransferAmount(),
                    "eligible_at", Instant.now().toString()
            ));

            // Resolve the seller's Stripe connected account
            SellerStripeAccount sellerAccount = sellerStripeAccountRepository
                    .findBySellerId(st.getSellerId()).orElse(null);

            if (sellerAccount == null || !Boolean.TRUE.equals(sellerAccount.getChargesEnabled())) {
                log.warn("Payout skipped: seller {} has no active Stripe account for orderId={}",
                        st.getSellerId(), st.getOrderId());
                st.setStatus("SKIPPED");
                sellerTransferRepository.save(st);
                return;
            }

            // Calculate platform commission and net amount to transfer
            BigDecimal transferAmount = st.getTransferAmount();
            double feePct = stripeConfig.getPlatformFeePercentage();
            BigDecimal commission = transferAmount
                    .multiply(BigDecimal.valueOf(feePct / 100.0))
                    .setScale(0, RoundingMode.HALF_UP);
            BigDecimal netAmount = transferAmount.subtract(commission);
            long netStripeAmount = netAmount.setScale(0, RoundingMode.HALF_UP).longValueExact();

            // Execute the Stripe Connect transfer to the seller's connected account
            Map<String, String> metadata = new HashMap<>();
            metadata.put("order_id", String.valueOf(st.getOrderId()));
            metadata.put("seller_id", String.valueOf(st.getSellerId()));
            metadata.put("commission", commission.toString());
            metadata.put("payout_type", "delayed");

            TransferCreateParams params = TransferCreateParams.builder()
                    .setAmount(netStripeAmount)
                    .setCurrency("vnd")
                    .setDestination(sellerAccount.getStripeAccountId())
                    .putAllMetadata(metadata)
                    .build();

            Transfer transfer = Transfer.create(params);

            // Persist payout result
            st.setStripeTransferId(transfer.getId());
            st.setPlatformCommissionAmount(commission);
            st.setPayoutAt(LocalDateTime.now());
            st.setStatus("PAID_OUT");
            sellerTransferRepository.save(st);

            log.info("Payout succeeded: orderId={}, sellerId={}, stripeTransferId={}, gross={}, commission={}, net={}",
                    st.getOrderId(), st.getSellerId(), transfer.getId(), transferAmount, commission, netAmount);

            // Publish seller.transfer.paid_out event
            publishPayoutEvent(st, "SELLER_TRANSFER_PAID_OUT", Map.of(
                    "transfer_id", st.getId(),
                    "seller_id", st.getSellerId(),
                    "order_id", st.getOrderId(),
                    "amount", transferAmount,
                    "stripe_payout_id", transfer.getId(),
                    "paid_at", Instant.now().toString()
            ));

            // Publish payout.processed event (legacy)
            publishPayoutEvent(st, "PAYOUT_PROCESSED", Map.of(
                    "order_id", st.getOrderId(),
                    "seller_id", st.getSellerId(),
                    "transfer_amount", transferAmount,
                    "commission", commission,
                    "net_amount", netAmount,
                    "stripe_transfer_id", transfer.getId(),
                    "payout_at", Instant.now().toString()
            ));

            publishPayoutEvent(st, "TRANSFER_COMPLETED", Map.of(
                    "transfer_id", st.getId(),
                    "order_id", st.getOrderId(),
                    "seller_id", st.getSellerId(),
                    "transfer_amount", transferAmount,
                    "commission", commission,
                    "net_amount", netAmount,
                    "stripe_transfer_id", transfer.getId(),
                    "payout_at", Instant.now().toString()
            ));

        } catch (StripeException e) {
            log.error("Stripe transfer failed for orderId={}, sellerId={}: {}",
                    st.getOrderId(), st.getSellerId(), e.getMessage());

            int retryCount = (st.getPayoutRetryCount() != null ? st.getPayoutRetryCount() : 0) + 1;
            st.setStatus("FAILED");
            st.setPayoutRetryCount(retryCount);
            sellerTransferRepository.save(st);

            // Publish seller.transfer.failed event
            publishPayoutEvent(st, "SELLER_TRANSFER_FAILED", Map.of(
                    "transfer_id", st.getId(),
                    "seller_id", st.getSellerId(),
                    "order_id", st.getOrderId(),
                    "failure_code", e.getCode(),
                    "failure_reason", e.getMessage()
            ));

            publishPayoutEvent(st, "PAYOUT_FAILED", Map.of(
                    "order_id", st.getOrderId(),
                    "seller_id", st.getSellerId(),
                    "error", e.getMessage(),
                    "retry_count", retryCount,
                    "timestamp", Instant.now().toString()
            ));
        }
    }

    private void publishPayoutEvent(SellerTransfer st, String type, Map<String, Object> data) {
        String topic = switch (type) {
            case "PAYOUT_PROCESSED" -> KafkaTopics.PAYOUT_PROCESSED;
            case "PAYOUT_FAILED" -> KafkaTopics.PAYOUT_FAILED;
            case "SELLER_TRANSFER_ELIGIBLE" -> KafkaTopics.SELLER_TRANSFER_ELIGIBLE;
            case "SELLER_TRANSFER_PAID_OUT" -> KafkaTopics.SELLER_TRANSFER_PAID_OUT;
            case "SELLER_TRANSFER_FAILED" -> KafkaTopics.SELLER_TRANSFER_FAILED;
            case "TRANSFER_COMPLETED" -> KafkaTopics.TRANSFER_COMPLETED;
            default -> type;
        };

        String key = st.getOrderId() != null ? String.valueOf(st.getOrderId()) : String.valueOf(st.getId());

        Map<String, Object> payload = new HashMap<>(data);
        payload.put("event_type", type);
        payload.put("timestamp", Instant.now().toString());

        try {
            kafkaTemplate.send(topic, key, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize payout event for topic {}: {}", topic, e.getMessage());
        }
    }
}
