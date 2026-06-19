package com.flashsale.productservice.scheduler;

import com.flashsale.productservice.entity.Cart;
import com.flashsale.productservice.entity.Product;
import com.flashsale.productservice.entity.ProductStatus;
import com.flashsale.productservice.repository.CartItemRepository;
import com.flashsale.productservice.repository.CartRepository;
import com.flashsale.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * JOB-07: hourly cleanup of cart items belonging to carts inactive >24h.
 * JOB-10: weekly hard-delete of products soft-deleted >90 days.
 * JOB-16: daily soft-delete (hide) of REJECTED products idle >30 days.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductCleanupScheduler {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    @Scheduled(cron = "${product.scheduler.stale-cart-cron:0 0 */2 * * *}")
    @SchedulerLock(name = "product-cleanup-stale-carts", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    @Transactional
    public void cleanupStaleCarts() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        List<Cart> staleCarts = cartRepository.findAllByUpdatedAtBefore(cutoff);
        if (staleCarts.isEmpty()) return;

        for (Cart cart : staleCarts) {
            cartItemRepository.deleteAllByCustomerId(cart.getCustomerId());
        }
        log.info("JOB-07: cleared cart items for {} stale carts (cutoff={})", staleCarts.size(), cutoff);
    }

    @Scheduled(cron = "${product.scheduler.hard-delete-cron:0 0 3 * * SUN}")
    @SchedulerLock(name = "product-hard-delete-soft-deleted", lockAtMostFor = "PT15M", lockAtLeastFor = "PT30S")
    @Transactional
    public void hardDeleteOldSoftDeletedProducts() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        List<Product> stale = productRepository.findAllByDeletedAtBefore(cutoff);
        if (stale.isEmpty()) return;

        productRepository.deleteAllInBatch(stale);
        log.info("JOB-10: hard-deleted {} products soft-deleted before {}", stale.size(), cutoff);
    }

    @Scheduled(cron = "${product.scheduler.auto-hide-cron:0 0 2 * * *}")
    @SchedulerLock(name = "product-auto-hide-rejected", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    @Transactional
    public void autoHideRejectedProducts() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        List<Product> stale = productRepository
                .findAllByStatusAndUpdatedAtBeforeAndDeletedAtIsNull(ProductStatus.REJECTED, cutoff);
        if (stale.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        for (Product product : stale) {
            product.setDeletedAt(now);
        }
        productRepository.saveAll(stale);
        log.info("JOB-16: auto-hid {} REJECTED products idle since {}", stale.size(), cutoff);
    }
}
