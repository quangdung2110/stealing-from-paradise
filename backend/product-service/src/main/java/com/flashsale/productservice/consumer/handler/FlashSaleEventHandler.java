package com.flashsale.productservice.consumer.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.productservice.entity.ProductVariant;
import com.flashsale.productservice.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class FlashSaleEventHandler {

    private final ProductVariantRepository variantRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public void handleSessionStarted(String message) {
        try {
            JsonNode payload = objectMapper.readTree(message);
            String sessionId = payload.has("sessionId") ? payload.get("sessionId").asText() : null;

            log.info("Received flash_sale.session_started event: sessionId={}", sessionId);

            if (payload.has("flashPriceMap") && payload.get("flashPriceMap").isObject()) {
                JsonNode flashPriceMap = payload.get("flashPriceMap");
                flashPriceMap.fieldNames().forEachRemaining(variantIdStr -> {
                    try {
                        UUID variantId = UUID.fromString(variantIdStr);
                        BigDecimal flashPrice = new BigDecimal(flashPriceMap.get(variantIdStr).asText());

                        variantRepository.findById(variantId)
                                .ifPresent(variant -> applyFlashPrice(variant, flashPrice));
                    } catch (Exception e) {
                        log.error("Failed to apply flash price for variantId={}: {}", variantIdStr, e.getMessage());
                    }
                });
            }

            if (payload.has("flashItems") && payload.get("flashItems").isArray()) {
                for (JsonNode item : payload.get("flashItems")) {
                    String skuCode = item.path("sku_code").asText(null);
                    String flashPriceRaw = item.path("flash_price").asText(null);
                    if (skuCode == null || flashPriceRaw == null) {
                        continue;
                    }
                    try {
                        BigDecimal flashPrice = new BigDecimal(flashPriceRaw);
                        variantRepository.findByVariantCode(skuCode)
                                .ifPresent(variant -> applyFlashPrice(variant, flashPrice));
                    } catch (Exception e) {
                        log.error("Failed to apply flash price for skuCode={}: {}", skuCode, e.getMessage());
                    }
                }
            }

            log.info("Flash sale session started processing complete: sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("Error processing flash_sale.session_started event: {}", message, e);
        }
    }

    private void applyFlashPrice(ProductVariant variant, BigDecimal flashPrice) {
        if (variant.getOriginalPrice() == null) {
            variant.setOriginalPrice(variant.getPrice());
        }
        BigDecimal originalPrice = variant.getOriginalPrice() != null ? variant.getOriginalPrice() : variant.getPrice();
        variant.setPrice(flashPrice);
        variantRepository.save(variant);

        log.info("Applied flash price to variant: variantId={}, originalPrice={}, flashPrice={}",
                variant.getId(), originalPrice, flashPrice);

        emitPriceSyncEvent(variant.getId(), flashPrice, true, variant.getProductId(), originalPrice);
    }

    public void handleSessionEnded(String message) {
        try {
            JsonNode payload = objectMapper.readTree(message);
            String sessionId = payload.has("sessionId") ? payload.get("sessionId").asText() : null;

            log.info("Received flash_sale.session_ended event: sessionId={}", sessionId);

            List<ProductVariant> variantsWithOriginalPrice = variantRepository.findByOriginalPriceNotNull();
            for (ProductVariant variant : variantsWithOriginalPrice) {
                variant.setPrice(variant.getOriginalPrice());
                variant.setOriginalPrice(null);
                variantRepository.save(variant);

                log.info("Restored original price for variant: variantId={}, restoredPrice={}",
                        variant.getId(), variant.getPrice());

                emitPriceSyncEvent(variant.getId(), variant.getPrice(), false, variant.getProductId(), null);
            }

            log.info("Flash sale session ended processing complete: sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("Error processing flash_sale.session_ended event: {}", message, e);
        }
    }

    private void emitPriceSyncEvent(UUID variantId, BigDecimal price, boolean active, UUID productId,
                                    BigDecimal originalPrice) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("variantId", variantId);
            payload.put("productId", productId);
            payload.put("price", price);
            payload.put("originalPrice", originalPrice);
            payload.put("active", active);
            if (active && originalPrice != null && price.compareTo(originalPrice) < 0) {
                int discountPct = originalPrice.subtract(price)
                        .multiply(BigDecimal.valueOf(100))
                        .divideToIntegralValue(originalPrice)
                        .intValue();
                payload.put("hasDiscount", true);
                payload.put("discountPct", discountPct);
            } else {
                payload.put("hasDiscount", false);
                payload.put("discountPct", 0);
            }

            String value = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(KafkaTopics.FLASH_SALE_PRICE_SYNC, variantId.toString(), value);
        } catch (Exception e) {
            log.error("Failed to emit flash_sale.price_sync event for variantId={}", variantId, e);
        }
    }
}
