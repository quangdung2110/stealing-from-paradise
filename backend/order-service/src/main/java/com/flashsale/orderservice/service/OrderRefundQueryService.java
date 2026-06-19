package com.flashsale.orderservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.orderservice.domain.model.Order;
import com.flashsale.orderservice.domain.model.OrderItem;
import com.flashsale.orderservice.domain.repository.OrderItemRepository;
import com.flashsale.orderservice.domain.repository.OrderRepository;
import com.flashsale.orderservice.domain.repository.ParentOrderRepository;
import com.flashsale.orderservice.dto.response.FullRefundCreatedResponse;
import com.flashsale.orderservice.dto.response.OrderRefundInfo;
import com.flashsale.orderservice.dto.response.PresignedUrlResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderRefundQueryService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ParentOrderRepository parentOrderRepository;
    private final KafkaReplyService kafkaReplyService;
    private final ObjectMapper objectMapper;

    public List<OrderRefundInfo> getOrderRefunds(Long orderId, Long userId, String role) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Đơn hàng không tồn tại"));
        boolean isAdmin  = role.contains("ADMIN");
        boolean isBuyer  = order.getCustomerId().equals(userId);
        boolean isSeller = order.getSellerId().equals(userId);
        if (!isAdmin && !isBuyer && !isSeller) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền xem refunds của đơn hàng này");
        }

        Map<String, Object> request = new HashMap<>();
        request.put("order_id", orderId);
        Map<String, Object> response = kafkaReplyService.sendAndReceive(
                KafkaTopics.ORDER_REFUNDS_REQUEST, request);

        return parseRefundList(response);
    }

    public OrderRefundInfo getRefundDetail(Long orderId, Long refundId, Long userId, String role) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Đơn hàng không tồn tại"));
        boolean isAdmin = role.contains("ADMIN");
        boolean isBuyer = order.getCustomerId().equals(userId);
        if (!isAdmin && !isBuyer) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền xem refund này");
        }

        Map<String, Object> request = new HashMap<>();
        request.put("order_id", orderId);
        request.put("refund_id", refundId);
        Map<String, Object> response = kafkaReplyService.sendAndReceive(
                KafkaTopics.ORDER_REFUNDS_REQUEST, request);

        if (Boolean.TRUE.equals(response.get("error"))) {
            throw new AppException(ErrorCode.NOT_FOUND, "Refund không tồn tại");
        }

        List<OrderRefundInfo> refunds = parseRefundList(response);
        if (refunds.isEmpty()) {
            throw new AppException(ErrorCode.NOT_FOUND, "Refund không tồn tại");
        }

        return refunds.get(0);
    }

    public PresignedUrlResponse getRefundPresignedUrl(Long orderId, String fileName, String contentType, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Đơn hàng không tồn tại"));
        if (!order.getCustomerId().equals(userId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền truy cập đơn hàng này");
        }

        Map<String, Object> request = new HashMap<>();
        request.put("order_id", orderId);
        request.put("user_id", userId);
        request.put("file_name", fileName);
        request.put("content_type", contentType);
        Map<String, Object> response = kafkaReplyService.sendAndReceive(
                KafkaTopics.ORDER_REFUND_PRESIGNED_URL_REQUEST, request);

        if (Boolean.TRUE.equals(response.get("error"))) {
            throw new AppException(ErrorCode.NOT_FOUND,
                    "Không thể tạo presigned URL: " + response.get("message"));
        }

        return objectMapper.convertValue(response, PresignedUrlResponse.class);
    }

    public List<OrderRefundInfo> getBuyerRefunds(String status, String type, String fromDate, String toDate,
                                                 int page, int size, Long userId) {
        Map<String, Object> request = new HashMap<>();
        request.put("user_id", userId);
        if (status   != null) request.put("status",    status);
        if (type     != null) request.put("type",      type);
        if (fromDate != null) request.put("from_date", fromDate);
        if (toDate   != null) request.put("to_date",   toDate);
        request.put("page", page);
        request.put("size", size);

        Map<String, Object> response = kafkaReplyService.sendAndReceive(
                KafkaTopics.ORDER_REFUNDS_REQUEST, request);

        return parseRefundList(response);
    }

    public FullRefundCreatedResponse getFullRefundStatus(Long parentOrderId, Long userId, String role) {
        if (!role.contains("ADMIN")) {
            parentOrderRepository.findByIdAndCustomerId(parentOrderId, userId)
                    .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN,
                            "Đơn cha không tồn tại hoặc không thuộc về bạn"));
        }

        List<Order> subOrders = orderRepository.findAllByParentOrderId(parentOrderId);
        List<FullRefundCreatedResponse.SubRefundInfo> subInfos = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        String groupRef = null;
        String overallStatus = "PENDING";

        for (Order o : subOrders) {
            Map<String, Object> req = new HashMap<>();
            req.put("order_id", o.getId());
            Map<String, Object> response = kafkaReplyService.sendAndReceive(
                    KafkaTopics.ORDER_REFUNDS_REQUEST, req);
            List<OrderRefundInfo> refunds = parseRefundList(response);
            if (!refunds.isEmpty()) {
                OrderRefundInfo r = refunds.get(0);
                if (groupRef == null) groupRef = r.getGroupRef();
                BigDecimal amt = r.getAdjustAmount() != null ? r.getAdjustAmount() : r.getAmount();
                if (amt != null) totalAmount = totalAmount.add(amt);
                subInfos.add(FullRefundCreatedResponse.SubRefundInfo.builder()
                        .orderId(o.getId())
                        .sellerId(o.getSellerId())
                        .amount(amt)
                        .itemCount(0)
                        .build());
                if ("FAILED".equals(r.getStatus()) || "REJECTED".equals(r.getStatus())) {
                    overallStatus = r.getStatus();
                } else if ("PENDING".equals(r.getStatus()) && !"FAILED".equals(overallStatus)) {
                    overallStatus = "PENDING";
                } else if ("SUCCESS".equals(r.getStatus()) && "PENDING".equals(overallStatus)
                        && subInfos.size() == subOrders.size()) {
                    overallStatus = "SUCCESS";
                }
            }
        }

        return FullRefundCreatedResponse.builder()
                .groupRef(groupRef)
                .parentOrderId(parentOrderId)
                .type("FULL")
                .totalAmount(totalAmount)
                .status(overallStatus)
                .refunds(subInfos)
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<OrderRefundInfo> parseRefundList(Map<String, Object> response) {
        if (Boolean.TRUE.equals(response.get("error"))) return List.of();
        Object raw = response.get("refunds");
        if (raw == null) return List.of();
        List<OrderRefundInfo> refunds = objectMapper.convertValue(raw, new TypeReference<List<OrderRefundInfo>>() {});
        enrichRefundItems(refunds);
        return refunds;
    }

    private void enrichRefundItems(List<OrderRefundInfo> refunds) {
        for (OrderRefundInfo refund : refunds) {
            if (refund.getItems() == null) continue;
            for (OrderRefundInfo.RefundItemInfo item : refund.getItems()) {
                Long orderItemId = item.getOrderItemId() != null ? item.getOrderItemId() : item.getItemId();
                if (orderItemId == null) continue;
                orderItemRepository.findById(orderItemId).ifPresent(orderItem -> applyOrderItemSnapshot(item, orderItem));
            }
        }
    }

    private void applyOrderItemSnapshot(OrderRefundInfo.RefundItemInfo target, OrderItem source) {
        target.setOrderItemId(source.getId());
        target.setProductName(source.getNameSnapshot());
        target.setImageSnapshot(source.getImageSnapshot());
    }
}
