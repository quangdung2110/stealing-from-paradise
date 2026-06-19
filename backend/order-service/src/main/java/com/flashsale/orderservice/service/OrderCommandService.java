package com.flashsale.orderservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.commonlib.infra.outbox.OutboxEventWriter;
import com.flashsale.orderservice.axon.event.*;
import com.flashsale.orderservice.domain.model.Order;
import com.flashsale.orderservice.domain.model.OrderItem;
import com.flashsale.orderservice.domain.repository.OrderItemRepository;
import com.flashsale.orderservice.domain.repository.OrderRepository;
import com.flashsale.orderservice.dto.request.CancelOrderRequest;
import com.flashsale.orderservice.dto.request.ReturnToSenderRequest;
import com.flashsale.orderservice.dto.request.UpdateTrackingRequest;
import com.flashsale.orderservice.dto.response.CancelOrderResponse;
import com.flashsale.orderservice.dto.response.ConfirmReceivedResponse;
import com.flashsale.orderservice.dto.response.ReturnToSenderResponse;
import com.flashsale.orderservice.dto.response.TrackingUpdateResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCommandService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ObjectMapper objectMapper;
    private final EventGateway eventGateway;
    private final OutboxEventWriter outboxEventWriter;
    private final MinioClient minioClient;

    @Value("${minio.bucket:refund-evidences}")
    private String minioBucket;

    @Value("${minio.public-url:http://localhost:9000}")
    private String minioPublicUrl;

    // Thời hạn giao hàng mặc định: 3 ngày
    private static final int DEFAULT_SHIPPING_DAYS = 3;

    @Transactional
    public CancelOrderResponse cancelOrder(Long orderId, Long userId, String role, CancelOrderRequest req) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Đơn hàng không tồn tại"));

        // Kiểm tra quyền: Buyer hoặc Seller chủ đơn
        boolean isBuyer  = order.getCustomerId().equals(userId);
        boolean isSeller = order.getSellerId().equals(userId);
        if (!isBuyer && !isSeller) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền hủy đơn hàng này");
        }

        String cancelledBy = isBuyer ? "BUYER" : "SELLER";
        String status = order.getStatus();
        boolean paid = "PAID".equals(status);
        boolean pending = "PENDING".equals(status);
        boolean shipped = order.getTrackingNumber() != null && !order.getTrackingNumber().isBlank();

        if (isBuyer) {
            if (!pending && !paid) {
                throw new AppException(ErrorCode.ORDER_NOT_CANCELLABLE,
                        "Buyer can only cancel PENDING or PAID orders before shipping");
            }
            if (paid && shipped) {
                throw new AppException(ErrorCode.ORDER_NOT_CANCELLABLE,
                        "Order already shipped; please request return/refund instead");
            }
        } else {
            if (!paid || shipped) {
                throw new AppException(ErrorCode.ORDER_NOT_CANCELLABLE,
                        "Seller can only cancel PAID orders before shipping");
            }
            if (req.getReason() == null || req.getReason().trim().length() < 10) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "Reason phai co toi thieu 10 ky tu khi seller huy");
            }
        }
        String cancelReason = req.getNote() != null
                ? req.getReason() + " - " + req.getNote()
                : req.getReason();

        order.setStatus("CANCELLED");
        order.setCancelledBy(cancelledBy);
        order.setCancelReason(cancelReason);
        orderRepository.save(order);

        // Emit Axon event → Saga publishes order.cancelled (and seller.order_cancelled if needed)
        eventGateway.publish(new OrderCancelledEvent(
                order.getId(),
                order.getParentOrderId(),
                userId,
                order.getSellerId(),
                cancelledBy,
                cancelReason,
                order.getTotalAmt()
        ));

        if (paid) {
            publishAutoFullRefundRequested(order, userId, cancelledBy, cancelReason);
        }

        log.info("Order cancelled: orderId={}, cancelledBy={}", orderId, cancelledBy);

        return CancelOrderResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .status("CANCELLED")
                .cancelledBy(cancelledBy)
                .cancelReason(cancelReason)
                .build();
    }

    private void publishAutoFullRefundRequested(Order order, Long userId, String cancelledBy, String reason) {
        BigDecimal amount = order.getFinalAmt() != null ? order.getFinalAmt() : order.getTotalAmt();
        List<OrderItem> items = orderItemRepository.findAllByOrderId(order.getId());

        Map<String, Object> refundItem = new LinkedHashMap<>();
        refundItem.put("order_id", order.getId());
        refundItem.put("seller_id", order.getSellerId());
        refundItem.put("amount", amount);
        refundItem.put("item_count", items.size());

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("parent_order_id", order.getParentOrderId());
        event.put("user_id", userId);
        event.put("group_ref", UUID.randomUUID().toString());
        event.put("reason", reason);
        event.put("total_amount", amount);
        event.put("refunds", List.of(refundItem));
        event.put("evidence_images", List.of());
        event.put("initiated_by", cancelledBy);
        event.put("refund_reason_type", "SELLER".equals(cancelledBy) ? "SELLER_CANCEL" : "BUYER_CANCEL");
        event.put("auto_process", true);
        event.put("timestamp", Instant.now().toString());

        try {
            outboxEventWriter.append("order", String.valueOf(order.getParentOrderId()),
                    KafkaTopics.REFUND_FULL_REQUESTED, KafkaTopics.REFUND_FULL_REQUESTED,
                    String.valueOf(order.getParentOrderId()), event);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish auto full refund request", e);
        }
    }

    @Transactional
    public TrackingUpdateResponse updateTracking(Long orderId, Long sellerId, UpdateTrackingRequest req) {
        Order order = orderRepository.findByIdAndSellerId(orderId, sellerId)
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN,
                        "Đơn hàng không tồn tại hoặc không thuộc về bạn"));

        // Chỉ cập nhật tracking khi đơn ở trạng thái PAID
        if (!"PAID".equals(order.getStatus())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Chỉ có thể cập nhật tracking khi đơn ở trạng thái PAID");
        }

        LocalDateTime shippingDeadline = LocalDateTime.now().plusDays(DEFAULT_SHIPPING_DAYS);

        order.setTrackingNumber(req.getTrackingNumber());
        order.setStatus("SHIPPING");
        order.setShippingDeadline(shippingDeadline);
        orderRepository.save(order);

        // Emit Axon event → Saga publishes order.shipped and schedules shipping deadline
        eventGateway.publish(new OrderShippedEvent(
                order.getId(),
                order.getCustomerId(),
                order.getSellerId(),
                req.getTrackingNumber(),
                req.getCarrier(),
                shippingDeadline
        ));

        log.info("Order tracking updated: orderId={}, trackingNumber={}", orderId, req.getTrackingNumber());

        return TrackingUpdateResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .status("SHIPPING")
                .trackingNumber(req.getTrackingNumber())
                .shippingDeadline(shippingDeadline.toInstant(ZoneOffset.UTC))
                .updatedAt(order.getUpdatedAt().toInstant(ZoneOffset.UTC))
                .build();
    }

    @Transactional
    public ConfirmReceivedResponse confirmReceived(Long orderId, Long userId) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN,
                        "Đơn hàng không tồn tại hoặc không thuộc về bạn"));

        if (!"SHIPPING".equals(order.getStatus())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Chỉ có thể xác nhận nhận hàng khi đơn đang ở trạng thái SHIPPING");
        }

        LocalDateTime now = LocalDateTime.now();
        order.setStatus("DELIVERED");
        order.setDeliveredAt(now);
        orderRepository.save(order);

        // Emit Axon event → Saga publishes order.delivered (ends saga)
        eventGateway.publish(new OrderDeliveredEvent(
                order.getId(),
                order.getCustomerId(),
                order.getSellerId(),
                order.getFinalAmt(),
                "BUYER"
        ));

        log.info("Order delivered confirmed: orderId={}", orderId);

        return ConfirmReceivedResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .status("DELIVERED")
                .deliveredAt(now.toInstant(ZoneOffset.UTC))
                .build();
    }

    @Transactional
    public ReturnToSenderResponse returnToSender(Long orderId, Long sellerId, ReturnToSenderRequest req) {
        Order order = orderRepository.findByIdAndSellerId(orderId, sellerId)
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN,
                        "Đơn hàng không tồn tại hoặc không thuộc về bạn"));

        // Chỉ cho phép khi đơn đang SHIPPING
        if (!"SHIPPING".equals(order.getStatus())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Chỉ có thể xác nhận hoàn hàng khi đơn đang ở trạng thái SHIPPING");
        }

        // Validate evidence images
        List<MultipartFile> images = req.getEvidenceImages();
        if (images == null || images.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Cần cung cấp ít nhất 1 ảnh bằng chứng");
        }
        if (images.size() > 5) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Tối đa 5 ảnh bằng chứng");
        }

        order.setStatus("RETURNED");
        orderRepository.save(order);

        // Upload evidence images to MinIO
        List<String> evidenceUrls = new ArrayList<>();
        for (MultipartFile image : images) {
            evidenceUrls.add(uploadRtsEvidenceImage(orderId, image));
        }

        // Tạo refund code (placeholder — sẽ được tạo bởi Refund Service sau khi nhận event)
        String refundCode = "RF-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + order.getId();

        // Emit Axon event → Saga publishes order.returned (ends saga)
        eventGateway.publish(new OrderReturnedEvent(
                order.getId(),
                order.getParentOrderId(),
                order.getCustomerId(),
                order.getSellerId(),
                order.getFinalAmt(),
                req.getReturnTrackingNumber(),
                images.size(),
                evidenceUrls
        ));

        log.info("Return-to-sender processed: orderId={}, evidenceCount={}, evidenceUrls={}",
                orderId, images.size(), evidenceUrls);

        return ReturnToSenderResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .orderStatus("RETURNED")
                .refundCode(refundCode)
                .refundStatus("PENDING")
                .refundAmount(order.getFinalAmt())
                .returnTrackingNumber(req.getReturnTrackingNumber())
                .evidenceCount(images.size())
                .estimatedRefundDays(3)
                .message("Hàng hoàn đã được ghi nhận. Hệ thống đang tự động hoàn tiền cho Buyer.")
                .sellerNotification(ReturnToSenderResponse.NotificationInfo.builder()
                        .status("sent")
                        .message("Xác nhận hàng hoàn đã được lưu. Tồn kho đã được cộng lại.")
                        .build())
                .buyerNotification(ReturnToSenderResponse.NotificationInfo.builder()
                        .status("sent")
                        .message("Seller đã nhận lại hàng hoàn. Tiền đang được hoàn về tài khoản của bạn.")
                        .build())
                .createdAt(Instant.now())
                .build();
    }

    /**
     * Upload a single RTS evidence image to MinIO and return its public URL.
     */
    private String uploadRtsEvidenceImage(Long orderId, MultipartFile image) {
        try {
            String originalName = image.getOriginalFilename();
            int dot = originalName != null ? originalName.lastIndexOf('.') : -1;
            String ext = dot > 0 ? originalName.substring(dot) : ".jpg";
            String objectKey = "refunds/rts/" + orderId + "/" + UUID.randomUUID() + ext;

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioBucket)
                            .object(objectKey)
                            .stream(new ByteArrayInputStream(image.getBytes()), image.getSize(), -1)
                            .contentType(image.getContentType() != null ? image.getContentType() : "image/jpeg")
                            .build()
            );

            String publicUrl = minioPublicUrl.replaceAll("/$", "") + "/" + minioBucket + "/" + objectKey;
            log.info("RTS evidence uploaded: orderId={}, key={}", orderId, objectKey);
            return publicUrl;
        } catch (Exception e) {
            log.error("Failed to upload RTS evidence image for orderId={}: {}", orderId, e.getMessage());
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Không thể tải ảnh bằng chứng: " + e.getMessage());
        }
    }
}
