package com.flashsale.productservice.service;

import com.flashsale.productservice.entity.ProductVariant;
import com.flashsale.productservice.repository.ProductVariantRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventorySyncService {

    private static final String STOCK_RESERVED_KEY_PREFIX = "stock:reserved:";

    private final ProductVariantRepository variantRepository;
    private final StringRedisTemplate redisTemplate;

    @PostConstruct
    public void syncAllVariantStockToRedis() {
        log.info("Starting Redis stock initialization from database");
        try {
            List<ProductVariant> variants = variantRepository.findAll();
            int count = 0;
            for (ProductVariant variant : variants) {
                if (variant.getDeletedAt() != null) {
                    continue;
                }
                String redisKey = STOCK_RESERVED_KEY_PREFIX + variant.getId();
                redisTemplate.opsForValue().set(redisKey, String.valueOf(variant.getStockQuantity()));
                count++;
            }
            log.info("Redis stock initialization complete: synced {} variants", count);
        } catch (Exception e) {
            log.error("Failed to initialize Redis stock from database", e);
        }
    }

    public void initializeVariantStock(UUID variantId, int stockQuantity) {
        try {
            String redisKey = STOCK_RESERVED_KEY_PREFIX + variantId;
            redisTemplate.opsForValue().setIfAbsent(redisKey, String.valueOf(stockQuantity));
        } catch (Exception e) {
            log.error("Failed to initialize Redis stock for variantId={}", variantId, e);
        }
    }

    public void updateVariantRedisStock(UUID variantId, int stockQuantity) {
        try {
            String redisKey = STOCK_RESERVED_KEY_PREFIX + variantId;
            redisTemplate.opsForValue().set(redisKey, String.valueOf(stockQuantity));
        } catch (Exception e) {
            log.error("Failed to update Redis stock for variantId={}", variantId, e);
        }
    }
}
