package com.flashsale.flashsaleservice.scheduler;

import com.flashsale.flashsaleservice.domain.repository.FlashSaleItemRepository;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * JOB-08: hard-delete fs_sessions (and their items) soft-deleted >30 days.
 *
 * <p>JOB-21 has been moved to product-service (where the actual stock lives under
 * pessimistic-lock reservations). See
 * {@code product-service/scheduler/ReservationCleanupScheduler} and the
 * {@code stock.reservation.expired} event flow.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FlashSaleMaintenanceScheduler {

    private final FlashSaleSessionRepository sessionRepo;
    private final FlashSaleItemRepository itemRepo;

    @Scheduled(cron = "${flashsale.scheduler.cleanup-cron:0 0 3 * * *}")
    @SchedulerLock(name = "flashsale-cleanup-soft-deleted",
            lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void cleanupSoftDeletedSessions() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        sessionRepo.findByDeletedAtIsNotNullAndDeletedAtBefore(cutoff)
                .flatMap(session -> itemRepo.deleteBySessionId(session.getId())
                        .then(sessionRepo.delete(session))
                        .thenReturn(session.getId()))
                .count()
                .doOnNext(n -> { if (n > 0) log.info("JOB-08: hard-deleted {} flash-sale sessions older than {}", n, cutoff); })
                .doOnError(e -> log.error("JOB-08 cleanup error: {}", e.getMessage(), e))
                .onErrorReturn(0L)
                .block();
    }
}
