package com.flashsale.orderservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.orderservice.domain.model.Order;
import com.flashsale.orderservice.domain.model.ParentOrder;
import com.flashsale.orderservice.domain.repository.OrderRepository;
import com.flashsale.orderservice.domain.repository.ParentOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Handles stock.reservation.expired events from Product Service.
 * When a stock reservation TTL expires, auto-cancels the parent order and all sub-orders.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StockReservationExpiredConsumer {

    private final ParentOrderRepository parentOrderRepository;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.STOCK_RESERVATION_EXPIRED, groupId = "order-service-stock")
    @Transactional
    public void onStockReservationExpired(String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> event = objectMapper.readValue(message, Map.class);
            String sessionId = (String) event.get("session_id");
            String reservationId = (String) event.get("reservation_id");

            if (sessionId == null) {
                log.warn("Stock reservation expired event missing session_id: {}", message);
                return;
            }

            log.info("Stock reservation expired: reservationId={}, sessionId={}", reservationId, sessionId);

            // Find the parent order by its checkout session id, then cancel its PENDING sub-orders.
            ParentOrder parentOrder = parentOrderRepository.findBySessionId(sessionId).orElse(null);
            if (parentOrder == null) {
                log.info("No parent order found for reservation expiry, sessionId={}", sessionId);
                return;
            }

            List<Order> subOrders = orderRepository.findAllByParentOrderIdAndStatus(
                    parentOrder.getId(), "PENDING");
            if (subOrders.isEmpty()) {
                log.info("No PENDING sub-orders for reservation expiry, sessionId={}, parentOrderId={}",
                        sessionId, parentOrder.getId());
                return;
            }

            for (Order order : subOrders) {
                if ("PENDING".equals(order.getStatus())) {
                    order.setStatus("CANCELLED");
                    order.setCancelledBy("SYSTEM");
                    order.setCancelReason("Stock reservation expired");
                    orderRepository.save(order);
                }
            }

            log.info("Auto-cancelled {} sub-orders due to stock reservation expiry, sessionId={}",
                    subOrders.size(), sessionId);

        } catch (Exception e) {
            log.error("Failed to process stock.reservation.expired event: {}", e.getMessage(), e);
        }
    }
}
