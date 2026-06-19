package com.flashsale.notificationservice.service.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.notificationservice.service.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class FlashSaleItemRejectedConsumer {

    private final NotificationEventPublisher publisher;

    @KafkaListener(topics = KafkaTopics.FLASH_SALE_ITEM_REJECTED, groupId = "notification-service-flashsale")
    public void onMessage(String message) {
        try {
            Map<String, Object> event = publisher.readEvent(message);
            Long sellerId = publisher.toLong(event.get("seller_id"));
            if (sellerId == null) {
                log.warn("flash_sale.item_rejected: missing seller_id, skipping notification");
                return;
            }

            String skuCode = String.valueOf(event.getOrDefault("sku_code", ""));
            publisher.emitToUser(sellerId,
                    "FS_ITEM_REJECTED",
                    "Flash Sale item rejected",
                    "SKU " + skuCode + " was rejected for the Flash Sale session.",
                    message,
                    "flash_sale.item_rejected");
        } catch (Exception e) {
            log.error("Failed to process flash_sale.item_rejected: {}", e.getMessage());
        }
    }
}
