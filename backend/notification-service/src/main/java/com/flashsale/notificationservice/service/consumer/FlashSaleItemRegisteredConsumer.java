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
public class FlashSaleItemRegisteredConsumer {

    private final NotificationEventPublisher publisher;

    @KafkaListener(topics = KafkaTopics.FLASH_SALE_ITEM_REGISTERED, groupId = "notification-service-flashsale")
    public void onMessage(String message) {
        try {
            Map<String, Object> event = publisher.readEvent(message);
            Long sellerId = publisher.toLong(event.get("seller_id"));
            if (sellerId == null) {
                log.warn("flash_sale.item_registered: missing seller_id, skipping notification");
                return;
            }

            publisher.emitToUser(sellerId, "FLASH_SALE_ITEM_REGISTERED",
                    "Đăng ký Flash Sale thành công",
                    "Sản phẩm " + event.get("sku_code")
                            + " đã được đăng ký và được áp dụng vào phiên Flash Sale.",
                    message,
                    "flash_sale.item_registered");
        } catch (Exception e) {
            log.error("Failed to process flash_sale.item_registered: {}", e.getMessage());
        }
    }
}
