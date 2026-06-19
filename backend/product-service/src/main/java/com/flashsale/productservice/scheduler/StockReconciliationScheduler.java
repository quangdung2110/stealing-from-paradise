package com.flashsale.productservice.scheduler;

import com.flashsale.productservice.entity.ProductVariant;
import com.flashsale.productservice.repository.ProductVariantRepository;
import com.flashsale.productservice.service.StockCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Safety-net scheduler that reconciles the Redis stock cache with the
 * Postgres source of truth. The Redis Lua atomic decrement + DB CAS pattern
 * keeps them in sync during the happy path; this scheduler catches rare
 * drift situations (Redis crash mid-flight, expired connections, manual DB
 * edits, etc.) by sampling a small set of variants and resetting any
 * out-of-sync keys back to the DB value.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StockReconciliationScheduler {

    private final ProductVariantRepository variantRepository;
    private final StockCacheService stockCacheService;

    @Value("${stock.reconciliation.enabled:true}")
    private boolean enabled;

    @Value("${stock.reconciliation.sample-size:1000}")
    private int sampleSize;

    @Scheduled(fixedRateString = "${stock.reconciliation.interval-ms:300000}")
    @SchedulerLock(name = "product-stock-reconciliation", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void reconcile() {
        if (!enabled) {
            return;
        }
        try {
            long total = variantRepository.count();
            if (total == 0) {
                return;
            }
            int pageSize = (int) Math.min(sampleSize, total);
            int totalPages = (int) Math.max(1, (total + pageSize - 1) / pageSize);
            int randomPage = ThreadLocalRandom.current().nextInt(0, totalPages);

            Page<ProductVariant> page = variantRepository.findAll(PageRequest.of(randomPage, pageSize));
            List<ProductVariant> variants = page.getContent();

            int checked = 0;
            int driftCount = 0;
            for (ProductVariant v : variants) {
                if (v.getDeletedAt() != null) {
                    continue;
                }
                checked++;
                String key = stockCacheService.key(v.getId());
                String redisVal = stockCacheService.getRedisTemplate().opsForValue().get(key);
                int dbVal = v.getStockQuantity();
                if (redisVal == null) {
                    stockCacheService.setStock(v.getId(), dbVal);
                    driftCount++;
                } else {
                    try {
                        int redisInt = Integer.parseInt(redisVal);
                        if (redisInt != dbVal) {
                            log.warn("Stock drift detected: variantId={}, db={}, redis={} - fixing",
                                    v.getId(), dbVal, redisInt);
                            stockCacheService.setStock(v.getId(), dbVal);
                            driftCount++;
                        }
                    } catch (NumberFormatException nfe) {
                        log.warn("Invalid stock value in Redis for variant {}: {} - resetting",
                                v.getId(), redisVal);
                        stockCacheService.setStock(v.getId(), dbVal);
                        driftCount++;
                    }
                }
            }

            if (driftCount > 0) {
                log.warn("Stock reconciliation: checked={}, drift={} (corrected)", checked, driftCount);
            } else {
                log.debug("Stock reconciliation: checked={}, no drift", checked);
            }
        } catch (Exception e) {
            log.error("Stock reconciliation failed", e);
        }
    }
}
