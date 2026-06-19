package com.flashsale.refundservice.scheduler;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.refundservice.domain.model.Refund;
import com.flashsale.refundservice.domain.repository.RefundRepository;
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
 * Scheduler that auto-finalizes refunds stuck in PROCESSING when the
 * customer does not return the item within the return window.
 *
 * Business rules:
 * - Refund in PROCESSING with RETURN_TO_SENDER reason: if no return within
 *   7 days from creation, cancel the refund.
 * - Non-RTS refunds in PROCESSING past 7 days: auto-complete.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefundReturnWindowScheduler {

    private static final long RETURN_WINDOW_DAYS = 7;

    private final RefundRepository refundRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Scheduled(cron = "0 30 * * * *")  // every hour at :30
    @SchedulerLock(name = "refund-process-return-window", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    @Transactional
    public void processReturnWindowExpiry() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETURN_WINDOW_DAYS);

        List<Refund> pending = refundRepository.findByStatusAndCreatedAtBefore("PROCESSING", cutoff, PageRequest.of(0, 100));

        if (pending.isEmpty()) {
            return;
        }

        log.warn("RefundReturnWindowScheduler: found {} refunds stuck in PROCESSING since before {}",
                pending.size(), cutoff.toLocalDate());

        for (Refund r : pending) {
            try {
                boolean isRts = "RETURN_TO_SENDER".equals(r.getRefundReasonType());

                if (isRts) {
                    r.setStatus("CANCELLED");
                    refundRepository.save(r);
                    log.warn("RefundReturnWindowScheduler: RTS window expired — CANCELLED refund id={}, orderId={}, "
                            + "createdAt={}, reason={}",
                            r.getId(), r.getOrderId(), r.getCreatedAt().toLocalDate(), r.getRefundReasonType());
                } else {
                    r.setStatus("COMPLETED");
                    refundRepository.save(r);
                    log.warn("RefundReturnWindowScheduler: processing window expired — COMPLETED refund id={}, orderId={}, "
                            + "createdAt={}, reason={}",
                            r.getId(), r.getOrderId(), r.getCreatedAt().toLocalDate(), r.getRefundReasonType());
                }

                Map<String, Object> event = new HashMap<>();
                event.put("refund_id", r.getId());
                event.put("order_id", r.getOrderId());
                event.put("event_type", isRts ? "REFUND_RTS_EXPIRED_CANCELLED" : "REFUND_PROCESSING_AUTO_COMPLETED");
                event.put("new_status", r.getStatus());
                event.put("timestamp", Instant.now().toString());

                String topic = isRts ? KafkaTopics.REFUND_RTS_COMPLETED : KafkaTopics.REFUND_ADMIN_APPROVED;
                kafkaTemplate.send(topic, String.valueOf(r.getId()), objectMapper.writeValueAsString(event));

            } catch (Exception e) {
                log.error("RefundReturnWindowScheduler: failed to process refund id={}, orderId={}: {}",
                        r.getId(), r.getOrderId(), e.getMessage(), e);
            }
        }

        log.warn("RefundReturnWindowScheduler: completed batch of {} refunds", pending.size());
    }
}
