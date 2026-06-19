package com.flashsale.searchservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.searchservice.dto.event.FlashSalePriceSyncPayload;
import com.flashsale.searchservice.service.ElasticsearchService;
import com.flashsale.searchservice.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class FlashSaleEventConsumer {

    private final ObjectMapper objectMapper;
    private final ElasticsearchService esService;
    private final IdempotencyService idempotencyService;

    @KafkaListener(
            topics = KafkaTopics.FLASH_SALE_PRICE_SYNC,
            containerFactory = "flashSaleKafkaListenerContainerFactory"
    )
    public void consumeFlashSaleEvent(String message) {
        try {
            FlashSalePriceSyncPayload payload = objectMapper.readValue(message, FlashSalePriceSyncPayload.class);
            String eventKey = "flash_sale:" + payload.getSessionId() + ":" +
                    (payload.getTimestamp() != null ? payload.getTimestamp() : "") + ":" +
                    payload.getAction();

            if (idempotencyService.isProcessed(eventKey)) {
                log.debug("Skipping duplicate flash sale event: {}", eventKey);
                return;
            }

            log.info("Processing flash_sale.price_sync: action={}, session={}, items={}",
                    payload.getAction(), payload.getSessionId(),
                    payload.getItems() != null ? payload.getItems().size() : 0);

            if ("activate".equalsIgnoreCase(payload.getAction())) {
                handleActivate(payload);
            } else if ("deactivate".equalsIgnoreCase(payload.getAction())) {
                handleDeactivate(payload);
            } else {
                log.warn("Unknown flash sale action: {}", payload.getAction());
            }

            idempotencyService.markProcessed(eventKey);
        } catch (Exception e) {
            log.error("Failed to process flash sale event: {}", e.getMessage(), e);
        }
    }

    private void handleActivate(FlashSalePriceSyncPayload payload) {
        List<Map<String, Object>> items = payload.getItems().stream()
                .map(item -> {
                    Map<String, Object> fields = new java.util.HashMap<>();
                    fields.put("skuId", item.getSkuId());
                    fields.put("flashPrice", item.getFlashPrice());
                    fields.put("originalPrice", item.getOriginalPrice());
                    fields.put("hasDiscount", item.getHasDiscount());
                    fields.put("sessionId", payload.getSessionId());
                    return fields;
                })
                .toList();
        try {
            esService.bulkPartialUpdateFlashSaleActivate(items);
            log.info("Activated flash sale session {} for {} items", payload.getSessionId(), items.size());
        } catch (Exception e) {
            log.error("Failed to activate flash sale session {}: {}", payload.getSessionId(), e.getMessage());
        }
    }

    private void handleDeactivate(FlashSalePriceSyncPayload payload) {
        List<String> skuIds = payload.getItems().stream()
                .map(FlashSalePriceSyncPayload.FlashSaleItem::getSkuId)
                .toList();
        try {
            esService.bulkPartialUpdateFlashSaleDeactivate(skuIds, payload.getSessionId());
            log.info("Deactivated flash sale session {} for {} SKUs", payload.getSessionId(), skuIds.size());
        } catch (Exception e) {
            log.error("Failed to deactivate flash sale session {}: {}", payload.getSessionId(), e.getMessage());
        }
    }
}
