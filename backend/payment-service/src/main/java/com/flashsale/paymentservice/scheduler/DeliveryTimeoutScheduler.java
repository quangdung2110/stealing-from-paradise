package com.flashsale.paymentservice.scheduler;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.paymentservice.domain.model.SellerTransfer;
import com.flashsale.paymentservice.domain.repository.SellerTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Safety-net scheduler that transitions seller transfers stuck in AWAITING_DELIVERY
 * to RETURN_WINDOW when the delivery deadline has passed.
 *
 * The nominal flow:
 *   order.paid → PENDING → AWAITING_DELIVERY
 *   ORDER_DELIVERED → RETURN_WINDOW (+ payout_eligible_at = now + 7d)
 *   every 5 min → PayoutScheduler processes eligible RETURN_WINDOW
 *
 * This scheduler handles the case where the ORDER_DELIVERED event was never fired
 * (e.g. customer never clicked "confirm receipt", Kafka message was lost, or the
 * delivery is self-evident after a long enough period). After RETURN_WINDOW_DAYS
 * past the transfer creation date, it auto-transitions to RETURN_WINDOW with
 * payout_eligible_at = now + RETURN_WINDOW so the PayoutScheduler picks it up.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeliveryTimeoutScheduler {

    private final SellerTransferRepository sellerTransferRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /** How many days after transfer creation before we assume delivery happened. */
    private static final long RETURN_WINDOW_DAYS = 30;
    /** Standard return window before seller can receive payout. */
    private static final long RETURN_WINDOW_DURATION_DAYS = 7;

    /**
     * Runs every hour, picks up transfers in AWAITING_DELIVERY older than
     * RETURN_WINDOW_DAYS, transitions them to RETURN_WINDOW.
     */
    @Scheduled(cron = "0 0 * * * *")
    @SchedulerLock(name = "payment-delivery-timeout", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    @Transactional
    public void processExpiredDeliveries() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETURN_WINDOW_DAYS);

        List<SellerTransfer> stuck = sellerTransferRepository
                .findByStatusAndCreatedAtBefore("AWAITING_DELIVERY", cutoff, PageRequest.of(0, 200));

        if (stuck.isEmpty()) {
            return;
        }

        log.warn("DeliveryTimeoutScheduler: found {} transfers stuck in AWAITING_DELIVERY since before {}",
                stuck.size(), cutoff.toLocalDate());

        for (SellerTransfer st : stuck) {
            try {
                LocalDateTime payoutEligibleAt = LocalDateTime.now().plusDays(RETURN_WINDOW_DURATION_DAYS);
                st.setDeliveredAt(st.getCreatedAt().plusDays(RETURN_WINDOW_DAYS));
                st.setPayoutEligibleAt(payoutEligibleAt);
                st.setStatus("RETURN_WINDOW");
                sellerTransferRepository.save(st);

                log.warn("DeliveryTimeoutScheduler: auto-transitioned transfer orderId={}, sellerId={}, "
                        + "createdAt={}, payoutEligibleAt={}",
                        st.getOrderId(), st.getSellerId(), st.getCreatedAt().toLocalDate(), payoutEligibleAt.toLocalDate());

                // Emit delivery.timeout.auto event so other services know
                Map<String, Object> event = new HashMap<>();
                event.put("transfer_id", st.getId());
                event.put("order_id", st.getOrderId());
                event.put("seller_id", st.getSellerId());
                event.put("event_type", "DELIVERY_TIMEOUT_AUTO");
                event.put("auto_delivered_at", st.getDeliveredAt().toString());
                event.put("payout_eligible_at", payoutEligibleAt.toString());
                event.put("timestamp", Instant.now().toString());

                kafkaTemplate.send(KafkaTopics.PAYOUT_PROCESSED,
                        String.valueOf(st.getOrderId()),
                        new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(event));

            } catch (Exception e) {
                log.error("DeliveryTimeoutScheduler: failed to process transfer id={}, orderId={}: {}",
                        st.getId(), st.getOrderId(), e.getMessage(), e);
            }
        }

        log.warn("DeliveryTimeoutScheduler: completed batch of {} transfers", stuck.size());
    }
}
