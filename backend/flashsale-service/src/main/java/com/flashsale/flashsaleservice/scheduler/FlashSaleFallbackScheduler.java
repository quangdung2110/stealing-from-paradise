package com.flashsale.flashsaleservice.scheduler;

import com.flashsale.flashsaleservice.domain.repository.FlashSaleSessionRepository;
import com.flashsale.flashsaleservice.service.FlashSaleSessionStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

/**
 * Fallback scheduler. Runs every {@code flashsale.reconcile.delay-ms} (default 5min).
 * Catches sessions whose triggers the worker missed (e.g. Redis was down at trigger time,
 * worker crashed between ZRANGEBYSCORE and dispatch, etc.).
 *
 * <p>Idempotency: {@link FlashSaleSessionStateService#activate} and
 * {@link FlashSaleSessionStateService#end} both filter on current status, so
 * the fallback is safe even if the worker has already fired the same transition.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FlashSaleFallbackScheduler {

    private final FlashSaleSessionRepository sessionRepo;
    private final FlashSaleSessionStateService stateService;

    @Value("${flashsale.reconcile.threshold-minutes:1}")
    private int thresholdMinutes;

    @Scheduled(fixedDelayString = "${flashsale.reconcile.delay-ms:300000}")
    @SchedulerLock(name = "flashsale-fallback-reconcile",
            lockAtMostFor = "PT4M", lockAtLeastFor = "PT10S")
    public void tick() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(thresholdMinutes);
        LocalDateTime now = LocalDateTime.now();

        long activated = sessionRepo.findByStatusAndStartTimeLessThanEqual("UPCOMING", threshold)
                .filter(s -> s.getDeletedAt() == null)
                .flatMap(s -> stateService.activate(s.getId()))
                .count()
                .block();

        long ended = sessionRepo.findByStatusAndEndTimeLessThanEqual("ACTIVE", threshold)
                .filter(s -> s.getDeletedAt() == null)
                .flatMap(s -> stateService.end(s.getId()))
                .count()
                .block();

        if (activated > 0) {
            log.info("Fallback: activated {} sessions missed by worker (threshold<={} before now={})",
                    activated, threshold, now);
        }
        if (ended > 0) {
            log.info("Fallback: ended {} sessions missed by worker (threshold<={} before now={})",
                    ended, threshold, now);
        }
    }
}
