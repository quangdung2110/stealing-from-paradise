package com.flashsale.productservice.service;

import com.flashsale.productservice.entity.ProductVariant;
import com.flashsale.productservice.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockCacheService {

    public static final String STOCK_KEY_PREFIX = "stock:available:";

    private final StringRedisTemplate redisTemplate;
    private final ProductVariantRepository variantRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        log.info("Warming up stock cache from database...");
        long start = System.currentTimeMillis();
        try {
            int count = 0;
            for (ProductVariant v : variantRepository.findAll()) {
                if (v.getDeletedAt() != null) {
                    continue;
                }
                redisTemplate.opsForValue().set(key(v.getId()), String.valueOf(v.getStockQuantity()));
                count++;
            }
            log.info("Stock cache warmed up: {} variants loaded in {}ms",
                    count, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("Failed to warm up stock cache - service will operate with lazy load on miss", e);
        }
    }

    public Integer loadAndCache(UUID variantId) {
        try {
            return variantRepository.findById(variantId)
                    .filter(v -> v.getDeletedAt() == null)
                    .map(v -> {
                        String stockStr = String.valueOf(v.getStockQuantity());
                        redisTemplate.opsForValue().set(key(variantId), stockStr);
                        return v.getStockQuantity();
                    })
                    .orElse(null);
        } catch (Exception e) {
            log.error("Failed to load and cache stock for variant {}", variantId, e);
            return null;
        }
    }

    public void setStock(UUID variantId, int quantity) {
        try {
            redisTemplate.opsForValue().set(key(variantId), String.valueOf(quantity));
        } catch (Exception e) {
            log.error("Failed to set stock in Redis for variant {} (will be reconciled)", variantId, e);
        }
    }

    public void evict(UUID variantId) {
        try {
            redisTemplate.delete(key(variantId));
        } catch (Exception e) {
            log.error("Failed to evict stock from Redis for variant {}", variantId, e);
        }
    }

    public StringRedisTemplate getRedisTemplate() {
        return redisTemplate;
    }

    public String key(UUID variantId) {
        return STOCK_KEY_PREFIX + variantId;
    }
}
