package com.flashsale.productservice.scheduler;

import com.flashsale.productservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Safety-net scheduler that releases PENDING reservations whose expires_at
 * has already passed. Most reservation lifecycle events are handled by the
 * Kafka consumers (order.paid, order.cancelled, order.payment_failed, etc.),
 * so this scheduler only has to deal with messages that were lost during a
 * Kafka outage. It now runs at a much slower cadence (default 5 minutes)
 * because Redis Lua + DB CAS already cover the race-condition path at
 * checkout time.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationCleanupScheduler {

    private final InventoryService inventoryService;

    @Scheduled(fixedRateString = "${reservation.cleanup.interval-ms:300000}")
    @SchedulerLock(name = "product-cleanup-expired-reservations", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void cleanupExpiredReservations() {
        log.debug("Checking for expired PENDING reservations");
        try {
            inventoryService.cleanupExpiredReservations();
        } catch (Exception e) {
            log.error("Error during reservation cleanup", e);
        }
    }
}
