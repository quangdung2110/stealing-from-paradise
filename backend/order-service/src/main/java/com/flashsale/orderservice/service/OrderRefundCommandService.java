package com.flashsale.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.orderservice.domain.model.Order;
import com.flashsale.orderservice.domain.model.OrderItem;
import com.flashsale.orderservice.domain.repository.OrderItemRepository;
import com.flashsale.orderservice.domain.repository.OrderRepository;
import com.flashsale.orderservice.domain.repository.ParentOrderRepository;
import com.flashsale.orderservice.dto.request.BuyerPartialRefundItem;
import com.flashsale.orderservice.dto.request.BuyerPartialRefundRequest;
import com.flashsale.orderservice.dto.request.FullRefundRequest;
import com.flashsale.orderservice.dto.response.FullRefundCreatedResponse;
import com.flashsale.orderservice.dto.response.RefundCreatedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderRefundCommandService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ParentOrderRepository parentOrderRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public RefundCreatedResponse createPartialRefund(Long orderId, BuyerPartialRefundRequest req, Long userId) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN,
                        "Đơn hàng không tồn tại hoặc không thuộc về bạn"));

        validateOrderStatusForRefund(order);

        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(orderId);
        Map<Long, OrderItem> itemMap = orderItems.stream()
                .collect(Collectors.toMap(OrderItem::getId, i -> i));

        BigDecimal totalRefundAmount = BigDecimal.ZERO;
        List<Map<String, Object>> refundItems = new ArrayList<>();

        for (BuyerPartialRefundItem reqItem : req.getItems()) {
            OrderItem oi = itemMap.get(reqItem.getOrderItemId());
            if (oi == null) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Item " + reqItem.getOrderItemId() + " không thuộc đơn hàng này");
            }
            int refunded = oi.getRefundedQuantity() != null ? oi.getRefundedQuantity() : 0;
            int available = oi.getQuantity() - refunded;
            if (reqItem.getQuantity() > available) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Số lượng hoàn của item " + reqItem.getOrderItemId()
                                + " vượt quá số lượng khả dụng (" + available + ")");
            }
            BigDecimal itemAmt = oi.getPriceSnapshot().multiply(BigDecimal.valueOf(reqItem.getQuantity()));
            totalRefundAmount = totalRefundAmount.add(itemAmt);

            Map<String, Object> ri = new HashMap<>();
            ri.put("order_item_id", reqItem.getOrderItemId());
            ri.put("quantity", reqItem.getQuantity());
            ri.put("refund_amount", itemAmt);
            ri.put("item_reason", reqItem.getItemReason() != null ? reqItem.getItemReason() : "");
            // Snapshot tên + ảnh sản phẩm để refund-service hiển thị được mà không cần join lại
            ri.put("product_name", oi.getNameSnapshot());
            ri.put("image_snapshot", oi.getImageSnapshot());
            refundItems.add(ri);
        }

        String groupRef = UUID.randomUUID().toString();
        Map<String, Object> event = new HashMap<>();
        event.put("refund_type",        "PARTIAL");
        event.put("order_id",           orderId);
        event.put("parent_order_id",    order.getParentOrderId());
        event.put("user_id",            userId);
        event.put("seller_id",          order.getSellerId());
        event.put("reason",             req.getReason());
        event.put("amount",             totalRefundAmount);
        event.put("group_ref",          groupRef);
        event.put("refund_reason_type", "BUYER_REQUEST");
        event.put("items",              refundItems);
        event.put("evidence_images",    req.getEvidenceImages() != null ? req.getEvidenceImages() : List.of());
        event.put("timestamp",          Instant.now().toString());
        kafkaTemplate.send(KafkaTopics.REFUND_REQUESTED, String.valueOf(orderId), toJson(event));

        log.info("Partial refund requested: orderId={}, userId={}, amount={}", orderId, userId, totalRefundAmount);

        return RefundCreatedResponse.builder()
                .groupRef(groupRef)
                .orderId(orderId)
                .type("PARTIAL")
                .status("PENDING")
                .totalAmount(order.getFinalAmt())
                .refundAmount(totalRefundAmount)
                .itemCount(req.getItems().size())
                .items(refundItems)
                .evidenceImages(req.getEvidenceImages())
                .estimatedDays(3)
                .message("Yêu cầu hoàn tiền đã được ghi nhận và đang chờ xử lý")
                .createdAt(Instant.now())
                .build();
    }

    @Transactional
    public FullRefundCreatedResponse createFullRefund(Long parentOrderId, FullRefundRequest req, Long userId) {
        parentOrderRepository.findByIdAndCustomerId(parentOrderId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN,
                        "Đơn cha không tồn tại hoặc không thuộc về bạn"));

        List<Order> subOrders = orderRepository.findAllByParentOrderId(parentOrderId);
        if (subOrders.isEmpty()) {
            throw new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn con nào");
        }

        for (Order o : subOrders) {
            validateOrderStatusForRefund(o);
        }

        String groupRef = UUID.randomUUID().toString();
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<Map<String, Object>> refundList = new ArrayList<>();
        List<FullRefundCreatedResponse.SubRefundInfo> subInfos = new ArrayList<>();

        for (Order o : subOrders) {
            int itemCount = orderItemRepository.findAllByOrderId(o.getId()).size();
            refundList.add(Map.of(
                    "order_id",   o.getId(),
                    "seller_id",  o.getSellerId(),
                    "amount",     o.getFinalAmt(),
                    "item_count", itemCount
            ));
            totalAmount = totalAmount.add(o.getFinalAmt());
            subInfos.add(FullRefundCreatedResponse.SubRefundInfo.builder()
                    .orderId(o.getId())
                    .sellerId(o.getSellerId())
                    .amount(o.getFinalAmt())
                    .itemCount(itemCount)
                    .status("PENDING")
                    .build());
        }

        Map<String, Object> event = new HashMap<>();
        event.put("parent_order_id",  parentOrderId);
        event.put("user_id",          userId);
        event.put("group_ref",        groupRef);
        event.put("reason",           req.getReason());
        event.put("total_amount",     totalAmount);
        event.put("refunds",          refundList);
        event.put("evidence_images",  req.getEvidenceImages() != null ? req.getEvidenceImages() : List.of());
        event.put("refund_reason_type", "BUYER_REQUEST");
        event.put("timestamp",        Instant.now().toString());
        kafkaTemplate.send(KafkaTopics.REFUND_FULL_REQUESTED, String.valueOf(parentOrderId), toJson(event));

        log.info("Full refund requested: parentOrderId={}, userId={}, total={}", parentOrderId, userId, totalAmount);

        return FullRefundCreatedResponse.builder()
                .groupRef(groupRef)
                .parentOrderId(parentOrderId)
                .type("FULL")
                .totalAmount(totalAmount)
                .status("PENDING")
                .refunds(subInfos)
                .estimatedDays(3)
                .message("Yêu cầu hoàn tiền toàn bộ đã được ghi nhận")
                .createdAt(Instant.now())
                .build();
    }

    @Transactional
    public FullRefundCreatedResponse createMultiSellerPartialRefund(Long parentOrderId, BuyerPartialRefundRequest req, Long userId) {
        parentOrderRepository.findByIdAndCustomerId(parentOrderId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN,
                        "Đơn cha không tồn tại hoặc không thuộc về bạn"));

        List<Order> subOrders = orderRepository.findAllByParentOrderId(parentOrderId);
        Map<Long, Order> itemIdToOrder = new HashMap<>();
        for (Order o : subOrders) {
            for (OrderItem oi : orderItemRepository.findAllByOrderId(o.getId())) {
                itemIdToOrder.put(oi.getId(), o);
            }
        }

        Map<Long, List<BuyerPartialRefundItem>> byOrder = new LinkedHashMap<>();
        for (BuyerPartialRefundItem ri : req.getItems()) {
            Order o = itemIdToOrder.get(ri.getOrderItemId());
            if (o == null) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Item " + ri.getOrderItemId() + " không thuộc parent order này");
            }
            validateOrderStatusForRefund(o);
            byOrder.computeIfAbsent(o.getId(), k -> new ArrayList<>()).add(ri);
        }

        String groupRef = UUID.randomUUID().toString();
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<FullRefundCreatedResponse.SubRefundInfo> subInfos = new ArrayList<>();

        Map<Long, OrderItem> allItems = new HashMap<>();
        for (Order o : subOrders) {
            orderItemRepository.findAllByOrderId(o.getId())
                    .forEach(oi -> allItems.put(oi.getId(), oi));
        }

        for (Map.Entry<Long, List<BuyerPartialRefundItem>> entry : byOrder.entrySet()) {
            Long orderId = entry.getKey();
            Order order  = subOrders.stream().filter(o -> o.getId().equals(orderId)).findFirst().orElseThrow();
            List<BuyerPartialRefundItem> reqItems = entry.getValue();

            BigDecimal subAmt = BigDecimal.ZERO;
            List<Map<String, Object>> refundItems = new ArrayList<>();
            for (BuyerPartialRefundItem ri : reqItems) {
                OrderItem oi = allItems.get(ri.getOrderItemId());
                int refunded = oi.getRefundedQuantity() != null ? oi.getRefundedQuantity() : 0;
                if (ri.getQuantity() > oi.getQuantity() - refunded) {
                    throw new AppException(ErrorCode.VALIDATION_FAILED,
                            "Số lượng hoàn của item " + ri.getOrderItemId() + " vượt quá khả dụng");
                }
                BigDecimal amt = oi.getPriceSnapshot().multiply(BigDecimal.valueOf(ri.getQuantity()));
                subAmt = subAmt.add(amt);

                Map<String, Object> rim = new HashMap<>();
                rim.put("order_item_id", ri.getOrderItemId());
                rim.put("quantity",      ri.getQuantity());
                rim.put("refund_amount", amt);
                rim.put("item_reason",   ri.getItemReason() != null ? ri.getItemReason() : "");
                // Snapshot tên + ảnh sản phẩm để refund-service hiển thị được mà không cần join lại
                rim.put("product_name",  oi.getNameSnapshot());
                rim.put("image_snapshot", oi.getImageSnapshot());
                refundItems.add(rim);
            }

            totalAmount = totalAmount.add(subAmt);

            Map<String, Object> event = new HashMap<>();
            event.put("refund_type",        "PARTIAL");
            event.put("order_id",           orderId);
            event.put("parent_order_id",    parentOrderId);
            event.put("user_id",            userId);
            event.put("seller_id",          order.getSellerId());
            event.put("reason",             req.getReason());
            event.put("amount",             subAmt);
            event.put("group_ref",          groupRef);
            event.put("refund_reason_type", "BUYER_REQUEST");
            event.put("items",              refundItems);
            event.put("evidence_images",    req.getEvidenceImages() != null ? req.getEvidenceImages() : List.of());
            event.put("timestamp",          Instant.now().toString());
            kafkaTemplate.send(KafkaTopics.REFUND_REQUESTED, String.valueOf(orderId), toJson(event));

            subInfos.add(FullRefundCreatedResponse.SubRefundInfo.builder()
                    .orderId(orderId)
                    .sellerId(order.getSellerId())
                    .amount(subAmt)
                    .itemCount(reqItems.size())
                    .build());
        }

        log.info("Multi-seller partial refund requested: parentOrderId={}, userId={}, sellerCount={}",
                parentOrderId, userId, byOrder.size());

        return FullRefundCreatedResponse.builder()
                .groupRef(groupRef)
                .parentOrderId(parentOrderId)
                .type("PARTIAL")
                .totalAmount(totalAmount)
                .status("PENDING")
                .refunds(subInfos)
                .message("Yêu cầu hoàn tiền đã được ghi nhận")
                .createdAt(Instant.now())
                .build();
    }

    private void validateOrderStatusForRefund(Order order) {
        String status = order.getStatus();
        if (!"DELIVERED".equals(status) && !"PARTIALLY_REFUNDED".equals(status)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Chỉ có thể yêu cầu hoàn tiền khi đơn hàng đã được giao (DELIVERED). "
                            + "Đơn " + order.getOrderCode() + " đang ở trạng thái " + status);
        }
        if (order.getDeliveredAt() == null
                || Duration.between(order.getDeliveredAt(), LocalDateTime.now()).toDays() > 7) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Đã quá 7 ngày kể từ khi nhận hàng, không thể yêu cầu hoàn tiền");
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize Kafka payload: {}", e.getMessage());
            return "{}";
        }
    }
}
