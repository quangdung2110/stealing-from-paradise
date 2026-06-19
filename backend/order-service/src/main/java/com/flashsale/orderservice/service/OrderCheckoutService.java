package com.flashsale.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.orderservice.axon.event.OrderCreatedEvent;
import com.flashsale.orderservice.axon.event.ParentOrderCheckoutCreatedEvent;
import com.flashsale.orderservice.client.dto.CartItemInfo;
import com.flashsale.orderservice.domain.model.Order;
import com.flashsale.orderservice.domain.model.OrderItem;
import com.flashsale.orderservice.domain.model.ParentOrder;
import com.flashsale.orderservice.domain.repository.OrderItemRepository;
import com.flashsale.orderservice.domain.repository.OrderRepository;
import com.flashsale.orderservice.domain.repository.ParentOrderRepository;
import com.flashsale.orderservice.dto.response.CheckoutOrderItem;
import com.flashsale.orderservice.dto.response.CheckoutResponse;
import com.flashsale.orderservice.dto.response.CheckoutSubOrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCheckoutService {

    private final OrderRepository orderRepository;
    private final ParentOrderRepository parentOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final EventGateway eventGateway;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Tạo order từ event order.checkout_submitted.
     * Được gọi bởi OrderCheckoutConsumer khi nhận event từ Product Service.
     */
    @Transactional
    public CheckoutResponse createOrderFromEvent(Long userId, List<CartItemInfo> cartItems,
                                                 Long addressId, String addressJson, String sessionId) {

        if (cartItems == null || cartItems.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Không có item hợp lệ trong giỏ hàng");
        }

        // 3. Tính tổng tiền
        BigDecimal totalAmt = cartItems.stream()
                .map(item -> item.getPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal finalAmt = totalAmt;

        // Group items by seller
        Map<Long, List<CartItemInfo>> itemsBySeller = cartItems.stream()
                .collect(Collectors.groupingBy(CartItemInfo::getSellerId));

        // Build shipping address JSON
        String shippingAddressJson = addressJson != null ? addressJson : "{}";

        // 7. Tạo ParentOrder
        ParentOrder parentOrder = parentOrderRepository.save(ParentOrder.builder()
                .customerId(userId)
                .sessionId(sessionId)
                .totalAmt(totalAmt)
                .finalAmt(finalAmt)
                .build());

        // 8. Tạo sub-orders theo từng seller
        List<Order> subOrders = new ArrayList<>();
        List<CheckoutSubOrderResponse> subOrderResponses = new ArrayList<>();

        for (Map.Entry<Long, List<CartItemInfo>> entry : itemsBySeller.entrySet()) {
            Long sellerId = entry.getKey();
            List<CartItemInfo> sellerItems = entry.getValue();

            BigDecimal sellerTotal = sellerItems.stream()
                    .map(i -> i.getPriceSnapshot().multiply(BigDecimal.valueOf(i.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            String sellerName = sellerItems.get(0).getSellerName();

            Order order = Order.builder()
                    .parentOrderId(parentOrder.getId())
                    .sellerId(sellerId)
                    .sellerName(sellerName)
                    .orderCode("OR-TEMP-" + java.util.UUID.randomUUID())  // unique placeholder, prevents unique-constraint collision
                    .customerId(userId)
                    .totalAmt(sellerTotal)
                    .finalAmt(sellerTotal)
                    .status("PENDING")
                    .isFlashSale(sellerItems.stream().anyMatch(i -> i.getFsItemId() != null))
                    .shippingAddress(shippingAddressJson)
                    .build();
            order = orderRepository.save(order);
            java.time.LocalDateTime createdAt = order.getCreatedAt();
            if (createdAt == null) {
                createdAt = java.time.LocalDateTime.now();
            }
            String orderCode = "OR-"
                    + createdAt.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                    + "-" + order.getId();
            order.setOrderCode(orderCode);
            order = orderRepository.save(order);

            // Tạo order items
            List<CheckoutOrderItem> itemResponses = new ArrayList<>();
            for (CartItemInfo ci : sellerItems) {
                BigDecimal subtotal = ci.getPriceSnapshot().multiply(BigDecimal.valueOf(ci.getQuantity()));
                OrderItem orderItem = OrderItem.builder()
                        .order(order)
                        .skuCode(ci.getSkuCode())
                        .variantId(ci.getVariantId())
                        .nameSnapshot(ci.getProductName())
                        .imageSnapshot(ci.getImageUrl())
                        .priceSnapshot(ci.getPriceSnapshot())
                        .quantity(ci.getQuantity())
                        .refundedQuantity(0)
                        .fsItemId(ci.getFsItemId())
                        .build();
                orderItemRepository.save(orderItem);

                itemResponses.add(CheckoutOrderItem.builder()
                        .orderItemId(orderItem.getId())
                        .skuCode(ci.getSkuCode())
                        .productName(ci.getProductName())
                        .imageSnapshot(ci.getImageUrl())
                        .priceSnapshot(ci.getPriceSnapshot())
                        .quantity(ci.getQuantity())
                        .subtotal(subtotal)
                        .build());
            }

            subOrders.add(order);
            subOrderResponses.add(CheckoutSubOrderResponse.builder()
                    .orderId(order.getId())
                    .orderCode(orderCode)
                    .sellerId(sellerId)
                    .sellerName(sellerName)
                    .totalAmt(sellerTotal)
                    .finalAmt(order.getFinalAmt())
                    .status("PENDING")
                    .items(itemResponses)
                    .createdAt(order.getCreatedAt().toInstant(ZoneOffset.UTC))
                    .build());
        }

        // 9. Emit one OrderCreatedEvent per sub-order → Saga starts, publishes order.created to Kafka
        subOrders.forEach(o -> eventGateway.publish(new OrderCreatedEvent(
                o.getId(),
                parentOrder.getId(),
                userId,
                o.getSellerId(),
                o.getOrderCode(),
                o.getTotalAmt(),
                Boolean.TRUE.equals(o.getIsFlashSale()),
                sessionId
        )));

        // 10. Emit parent-order event so Axon saga can orchestrate payment flow once per checkout
        eventGateway.publish(new ParentOrderCheckoutCreatedEvent(
                parentOrder.getId(),
                userId,
                finalAmt
        ));
        publishPaymentRequestedAfterCommit(parentOrder.getId(), userId, finalAmt, subOrders);

        log.info("Order created from event: parentOrderId={}, userId={}, totalAmt={}, sessionId={}",
                parentOrder.getId(), userId, totalAmt, sessionId);

        return CheckoutResponse.builder()
                .parentOrderId(parentOrder.getId())
                .orders(subOrderResponses)
                .totalAmount(totalAmt)
                .shippingAddress(null) // address đã được snapshot trong Order entity
                .totalItems(cartItems.stream().mapToInt(CartItemInfo::getQuantity).sum())
                .createdAt(parentOrder.getCreatedAt().toInstant(ZoneOffset.UTC))
                .build();
    }

    private void publishPaymentRequestedAfterCommit(Long parentOrderId,
                                                    Long userId,
                                                    BigDecimal totalAmount,
                                                    List<Order> subOrders) {
        List<Map<String, Object>> orders = subOrders.stream()
                .map(order -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("order_id", order.getId());
                    item.put("seller_id", order.getSellerId());
                    item.put("amount", order.getTotalAmt());
                    return item;
                })
                .toList();

        Map<String, Object> payload = new HashMap<>();
        payload.put("parent_order_id", parentOrderId);
        payload.put("user_id", userId);
        payload.put("total_amount", totalAmount);
        payload.put("currency", "VND");
        payload.put("timestamp", Instant.now().toString());
        payload.put("orders", orders);

        Runnable publish = () -> sendPaymentRequested(parentOrderId, payload, orders.size());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }

    private void sendPaymentRequested(Long parentOrderId, Map<String, Object> payload, int orderCount) {
        try {
            String message = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(KafkaTopics.PAYMENT_REQUESTED, String.valueOf(parentOrderId), message)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("[CheckoutPaymentBridge][{}] Failed to publish payment.requested: {}",
                                    parentOrderId, ex.getMessage(), ex);
                            return;
                        }
                        log.info("[CheckoutPaymentBridge][{}] payment.requested published directly with {} sub-orders",
                                parentOrderId, orderCount);
                    });
        } catch (JsonProcessingException e) {
            log.error("[CheckoutPaymentBridge][{}] Failed to serialize payment.requested: {}",
                    parentOrderId, e.getMessage(), e);
        } catch (Exception e) {
            log.error("[CheckoutPaymentBridge][{}] Failed to schedule payment.requested publish: {}",
                    parentOrderId, e.getMessage(), e);
        }
    }
}
