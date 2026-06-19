package com.flashsale.flashsaleservice.domain.repository;

import com.flashsale.flashsaleservice.domain.model.FlashSaleSession;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

@Repository
public interface FlashSaleSessionRepository extends ReactiveCrudRepository<FlashSaleSession, Long> {
    Flux<FlashSaleSession> findByStatus(String status);

    Flux<FlashSaleSession> findByStatusAndStartTimeLessThanEqual(String status, LocalDateTime startTime);

    Flux<FlashSaleSession> findByStatusAndEndTimeLessThanEqual(String status, LocalDateTime endTime);

    // ─── Maintenance jobs (JOB-08 / JOB-21) ───────────────────────────────────
    Flux<FlashSaleSession> findByDeletedAtIsNotNullAndDeletedAtBefore(LocalDateTime cutoff);

    Flux<FlashSaleSession> findByStatusAndEndTimeBetween(String status, LocalDateTime from, LocalDateTime to);
}
