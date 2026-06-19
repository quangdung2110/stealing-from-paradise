package com.flashsale.refundservice.reply;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.refundservice.domain.model.Refund;
import com.flashsale.refundservice.domain.model.RefundItem;
import com.flashsale.refundservice.domain.model.Transaction;
import com.flashsale.refundservice.domain.repository.RefundItemRepository;
import com.flashsale.refundservice.domain.repository.RefundRepository;
import com.flashsale.refundservice.domain.repository.TransactionRepository;
import com.flashsale.refundservice.support.RefundMapper;
import com.flashsale.refundservice.support.RefundTypeConverter;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundReplyService {

    private final RefundRepository refundRepository;
    private final RefundItemRepository refundItemRepository;
    private final TransactionRepository transactionRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RefundTypeConverter typeConverter;
    private final ObjectMapper objectMapper;
    private final MinioClient minioClient;

    @Value("${minio.url:http://localhost:9000}")
    private String minioUrl;

    @Value("${minio.public-url:http://localhost:9000}")
    private String minioPublicUrl;

    @Value("${minio.bucket:refund-evidences}")
    private String bucket;

    @Value("${minio.presigned-ttl-minutes:15}")
    private int presignedTtlMinutes;

    public void onOrderRefundsRequest(String message) {
        String correlationId = null;
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            correlationId = (String) payload.get("correlation_id");
            if (correlationId == null) return;

            List<Map<String, Object>> refundData;
            long totalElements = 0;
            int totalPages = 1;

            if (payload.containsKey("order_id")) {
                Long orderId = typeConverter.toLong(payload.get("order_id"));
                List<Refund> refunds = refundRepository.findAllByOrderId(orderId);
                refundData = refunds.stream().map(this::toRefundMap).collect(Collectors.toList());
                totalElements = refundData.size();

            } else if (payload.containsKey("user_id")) {
                Long userId    = typeConverter.toLong(payload.get("user_id"));
                String status  = (String) payload.get("status");
                String type    = (String) payload.get("type");
                int page       = typeConverter.toInt(payload.getOrDefault("page", 0));
                int size       = typeConverter.toInt(payload.getOrDefault("size", 20));
                String fromStr = (String) payload.get("from_date");
                String toStr   = (String) payload.get("to_date");

                LocalDateTime from = fromStr != null ? LocalDateTime.parse(fromStr + "T00:00:00") : null;
                LocalDateTime to   = toStr   != null ? LocalDateTime.parse(toStr   + "T23:59:59") : null;

                Page<Refund> pageResult = refundRepository.findAllByUserIdWithFilters(
                        userId, status, type, from, to,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
                refundData = pageResult.getContent().stream().map(this::toRefundMap).collect(Collectors.toList());
                totalElements = pageResult.getTotalElements();
                totalPages    = pageResult.getTotalPages();

            } else {
                refundData = List.of();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("correlation_id",  correlationId);
            response.put("refunds",          refundData);
            response.put("total_elements",   totalElements);
            response.put("total_pages",      totalPages);

            kafkaTemplate.send(KafkaTopics.ORDER_REFUNDS_RESPONSE, correlationId, typeConverter.toJson(response));

        } catch (Exception e) {
            log.error("Error processing ORDER_REFUNDS_REQUEST: {}", e.getMessage(), e);
            if (correlationId != null) {
                Map<String, Object> errorResp = Map.of(
                        "correlation_id", correlationId,
                        "error", true,
                        "refunds", List.of()
                );
                kafkaTemplate.send(KafkaTopics.ORDER_REFUNDS_RESPONSE, correlationId, typeConverter.toJson(errorResp));
            }
        }
    }

    private Map<String, Object> toRefundMap(Refund refund) {
        Map<String, Object> m = RefundMapper.toRefundMap(refund);
        m.put("evidence_images", refund.getEvidenceImages());
        m.put("evidenceImages", refund.getEvidenceImages());
        m.put("items", refundItemRepository.findAllByRefundId(refund.getId()).stream()
                .map(this::toRefundItemMap)
                .collect(Collectors.toList()));
        return m;
    }

    private Map<String, Object> toRefundItemMap(RefundItem item) {
        Map<String, Object> m = new HashMap<>();
        m.put("item_id", item.getItemId());
        m.put("order_item_id", item.getItemId());
        m.put("product_name", item.getProductName());
        m.put("image_snapshot", item.getImageSnapshot());
        m.put("quantity", item.getQuantity());
        m.put("refund_amount", item.getRefundAmount());
        m.put("item_reason", item.getItemReason());
        m.put("status", item.getStatus());
        m.put("return_tracking_number", item.getReturnTrackingNumber());
        m.put("returned_at", item.getReturnedAt() != null
                ? item.getReturnedAt().toInstant(java.time.ZoneOffset.UTC).toString() : null);
        // camelCase keys: order-service dùng ObjectMapper mặc định (camelCase) khi
        // convertValue reply sang OrderRefundInfo.RefundItemInfo — nếu thiếu thì các field
        // này bị null. Giữ luôn key snake_case ở trên để không phá consumer nào khác.
        m.put("itemId", item.getItemId());
        m.put("orderItemId", item.getItemId());
        m.put("productName", item.getProductName());
        m.put("imageSnapshot", item.getImageSnapshot());
        m.put("refundAmount", item.getRefundAmount());
        m.put("itemReason", item.getItemReason());
        m.put("returnTrackingNumber", item.getReturnTrackingNumber());
        m.put("returnedAt", item.getReturnedAt() != null
                ? item.getReturnedAt().toInstant(java.time.ZoneOffset.UTC).toString() : null);
        return m;
    }

    public void onRefundPresignedUrlRequest(String message) {
        String correlationId = null;
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            correlationId = (String) payload.get("correlation_id");
            if (correlationId == null) return;

            Long orderId = typeConverter.toLong(payload.get("order_id"));
            String fileName = (String) payload.get("file_name");
            String contentType = (String) payload.get("content_type");

            if (fileName == null) fileName = "evidence.jpg";
            int dot = fileName.lastIndexOf('.');
            String ext = dot > 0 ? fileName.substring(dot) : ".jpg";
            String objectKey = "refunds/" + orderId + "/" + UUID.randomUUID() + ext;

            int ttlSeconds = presignedTtlMinutes * 60;
            String signedUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(ttlSeconds)
                            .build()
            );
            String presignedUrl = signedUrl;

            Map<String, Object> response = new HashMap<>();
            response.put("correlation_id", correlationId);
            response.put("url", presignedUrl);
            response.put("fileName", objectKey);
            response.put("contentType", contentType);
            response.put("expiresAt", Instant.now().plusSeconds(ttlSeconds).toString());

            kafkaTemplate.send(KafkaTopics.ORDER_REFUND_PRESIGNED_URL_RESPONSE, correlationId, typeConverter.toJson(response));

        } catch (Exception e) {
            log.error("Error processing ORDER_REFUND_PRESIGNED_URL_REQUEST: {}", e.getMessage(), e);
            if (correlationId != null) {
                Map<String, Object> errorResp = Map.of(
                        "correlation_id", correlationId,
                        "error", true,
                        "message", e.getMessage()
                );
                kafkaTemplate.send(KafkaTopics.ORDER_REFUND_PRESIGNED_URL_RESPONSE, correlationId, typeConverter.toJson(errorResp));
            }
        }
    }

    public void onOrderPaymentStatusRequest(String message) {
        String correlationId = null;
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            correlationId = (String) payload.get("correlation_id");
            if (correlationId == null) return;
            Long parentOrderId = typeConverter.toLong(payload.get("parent_order_id"));
            Optional<Transaction> txOpt = transactionRepository.findByParentOrderId(parentOrderId);

            Map<String, Object> response = new HashMap<>();
            response.put("correlation_id",   correlationId);
            response.put("parent_order_id",  parentOrderId);
            if (txOpt.isPresent()) {
                Transaction tx = txOpt.get();
                response.put("transaction_id", tx.getId());
                response.put("stripe_pi_id",   null);
                response.put("status",         tx.getStatus());
                response.put("amount",         tx.getAmount());
                response.put("error",          false);
            } else {
                response.put("error",  true);
                response.put("status", "NOT_FOUND");
            }

            kafkaTemplate.send(KafkaTopics.ORDER_PAYMENT_STATUS_RESPONSE, correlationId, typeConverter.toJson(response));

        } catch (Exception e) {
            log.error("Error processing ORDER_PAYMENT_STATUS_REQUEST: {}", e.getMessage(), e);
            kafkaTemplate.send(KafkaTopics.ORDER_PAYMENT_STATUS_RESPONSE, correlationId,
                    typeConverter.toJson(Map.of("correlation_id", correlationId, "error", true)));
        }
    }
}
