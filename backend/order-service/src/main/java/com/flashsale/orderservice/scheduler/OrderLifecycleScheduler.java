package com.flashsale.orderservice.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.infra.outbox.OutboxEventWriter;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.orderservice.domain.model.Order;
import com.flashsale.orderservice.domain.model.ParentOrder;
import com.flashsale.orderservice.domain.repository.OrderRepository;
import com.flashsale.orderservice.domain.repository.ParentOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JOB-13: auto-cancel PENDING orders unpaid >30 minutes (anti-zombie safety net for orders
 *         without stock reservations, which would otherwise never get cleaned up).
 * JOB-22: auto-deliver SHIPPING orders with no tracking update for >7 days.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderLifecycleScheduler {

    private final OrderRepository orderRepository;
    private final ParentOrderRepository parentOrderRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final ObjectMapper objectMapper;

    @Scheduled(cron = "${order.scheduler.auto-cancel-cron:0 */10 * * * *}")
    @SchedulerLock(name = "order-auto-cancel-stale", lockAtMostFor = "PT5M", lockAtLeastFor = "PT10S")
    @Transactional
    public void autoCancelStalePendingOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        List<Order> stale = orderRepository.findAllByStatusAndCreatedAtBefore("PENDING", cutoff);
        if (stale.isEmpty()) return;

        for (Order order : stale) {
            order.setStatus("CANCELLED");
            order.setCancelledBy("SYSTEM");
            order.setCancelReason("Payment timeout — auto-cancelled after 24 hours");
            orderRepository.save(order);

            publish(KafkaTopics.ORDER_AUTO_CANCELLED, order, Map.of(
                    "reason", "PAYMENT_TIMEOUT",
                    "cancelled_by", "SYSTEM"
            ));
        }
        log.info("JOB-13: auto-cancelled {} stale PENDING orders (cutoff={})", stale.size(), cutoff);
    }

    @Scheduled(cron = "${order.scheduler.auto-deliver-cron:0 0 */6 * * *}")
    @SchedulerLock(name = "order-auto-deliver-stale", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    @Transactional
    public void autoDeliverStaleShippingOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        List<Order> stale = orderRepository.findAllByStatusAndUpdatedAtBefore("SHIPPING", cutoff);
        if (stale.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        for (Order order : stale) {
            order.setStatus("DELIVERED");
            order.setDeliveredAt(now);
            orderRepository.save(order);

            publish(KafkaTopics.ORDER_DELIVERED, order, Map.of(
                    "delivered_at", now.toString(),
                    "auto_delivered", true
            ));
        }
        log.info("JOB-22: auto-delivered {} stale SHIPPING orders (cutoff={})", stale.size(), cutoff);
    }

    private void publish(String topic, Order order, Map<String, Object> extras) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event_id", "evt_" + System.currentTimeMillis() + "_" + order.getId());
            payload.put("event_type", topic);
            payload.put("order_id", order.getId());
            payload.put("parent_order_id", order.getParentOrderId());
            payload.put("order_code", order.getOrderCode());
            payload.put("customer_id", order.getCustomerId());
            payload.put("seller_id", order.getSellerId());
            payload.put("session_id", sessionIdFor(order));
            payload.put("status", order.getStatus());
            payload.put("timestamp", Instant.now().toString());
            payload.putAll(extras);
            
            outboxEventWriter.append("order", String.valueOf(order.getId()), topic, topic, String.valueOf(order.getId()), payload);
        } catch (Exception e) {
            log.error("Failed to publish {} for orderId={}: {}", topic, order.getId(), e.getMessage(), e);
        }
    }

    private String sessionIdFor(Order order) {
        if (order.getParentOrderId() == null) {
            return "";
        }
        return parentOrderRepository.findById(order.getParentOrderId())
                .map(ParentOrder::getSessionId)
                .orElse("");
    }
}
