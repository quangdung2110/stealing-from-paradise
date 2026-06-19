package com.flashsale.orderservice.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.orderservice.client.dto.CartItemInfo;
import com.flashsale.orderservice.domain.repository.ParentOrderRepository;
import com.flashsale.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class CheckoutSubmittedConsumer {

    private final OrderService orderService;
    private final ParentOrderRepository parentOrderRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${kafka.topics.order-checkout-submitted:order.checkout_submitted}",
            groupId = "order-service-checkout-group"
    )
    @Transactional
    public void onCheckoutSubmitted(ConsumerRecord<String, String> record) {
        // AckMode is BATCH (KafkaConfig) — listener container commits the offset
        // automatically after the method returns successfully.
        try {
            Map<String, Object> payload = objectMapper.readValue(record.value(),
                    new TypeReference<Map<String, Object>>() {});

            String sessionId = payload.containsKey("session_id")
                    ? payload.get("session_id").toString()
                    : null;
            Long customerId = toLong(payload.get("customer_id"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> itemsRaw = objectMapper.convertValue(
                    payload.get("items"),
                    new TypeReference<List<Map<String, Object>>>() {});

            String addressSnapshot = payload.containsKey("address_snapshot")
                    ? payload.get("address_snapshot").toString()
                    : "{}";

            if (customerId == null) {
                log.error("Missing customer_id in order.checkout_submitted event");
                return;
            }

            List<CartItemInfo> cartItems = itemsRaw.stream()
                    .map(this::toCartItemInfo)
                    .toList();

            log.info("Received order.checkout_submitted: sessionId={}, customerId={}, itemCount={}",
                    sessionId, customerId, cartItems.size());

            var response = orderService.createOrderFromEvent(
                    customerId, cartItems, null, addressSnapshot, sessionId);

            log.info("Order created from event: parentOrderId={}, sessionId={}",
                    response.getParentOrderId(), sessionId);
        } catch (Exception e) {
            log.error("Error processing order.checkout_submitted event: {}", record.value(), e);
            // Swallow: BATCH ack commits anyway, prevents DLT spiral on bad payloads
        }
    }

    private CartItemInfo toCartItemInfo(Map<String, Object> item) {
        CartItemInfo info = new CartItemInfo();

        info.setCartItemId(getString(item, "cart_item_id"));
        info.setSkuCode(getString(item, "sku_code"));
        info.setVariantId(getString(item, "variant_id"));
        info.setProductName(getString(item, "product_name"));
        info.setImageUrl(getString(item, "image_url"));
        info.setSellerName(getString(item, "seller_name"));

        Long sellerId = toLong(item.get("seller_id"));
        info.setSellerId(sellerId != null ? sellerId : 0L);

        if (item.containsKey("price_snapshot") && item.get("price_snapshot") != null) {
            info.setPriceSnapshot(new BigDecimal(item.get("price_snapshot").toString()));
        }

        info.setQuantity(toInt(item.get("quantity")));

        if (item.containsKey("fs_item_id") && item.get("fs_item_id") != null) {
            String fsId = item.get("fs_item_id").toString();
            if (!fsId.isEmpty()) {
                info.setFsItemId(toLong(item.get("fs_item_id")));
            }
        }

        return info;
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : "";
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private int toInt(Object value) {
        if (value == null) return 0;
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return 0;
        }
    }
}
