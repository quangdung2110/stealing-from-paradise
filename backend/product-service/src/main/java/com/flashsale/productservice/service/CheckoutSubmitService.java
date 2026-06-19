package com.flashsale.productservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.productservice.dto.checkout.CheckoutPreviewResponse;
import com.flashsale.productservice.dto.checkout.CheckoutSubmitRequest;
import com.flashsale.productservice.dto.checkout.CheckoutSubmitResponse;
import com.flashsale.productservice.dto.checkout.CheckoutPreviewError;
import com.flashsale.productservice.dto.checkout.CheckoutPreviewError.PreviewItemError;
import com.flashsale.productservice.entity.ProductVariant;
import com.flashsale.productservice.entity.VariantStatus;
import com.flashsale.productservice.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutSubmitService {

    private static final String CHECKOUT_SESSION_PREFIX = "checkout:session:";
    private static final Duration CHECKOUT_SESSION_TTL = Duration.ofMinutes(15);

    private final CheckoutPreviewService checkoutPreviewService;
    private final InventoryService inventoryService;
    private final ProductVariantRepository variantRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final KafkaReplyService kafkaReplyService;

    @Transactional
    public ApiResponse<CheckoutSubmitResponse> submit(CheckoutSubmitRequest request, Long customerId) {
        String previewToken = request.getPreviewToken();

        CheckoutPreviewResponse preview =
                checkoutPreviewService.validateAndGetPreview(previewToken, customerId);

        revalidateStock(preview);
        Map<String, Object> addressInfo = fetchAddress(customerId, request.getAddressId());
        String addressSnapshot = buildAddressSnapshot(addressInfo);

        String sessionId = UUID.randomUUID().toString();
        List<Map<String, Object>> orderItems = new ArrayList<>();
        List<UUID> variantIds = new ArrayList<>();
        int totalItems = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CheckoutPreviewResponse.PreviewSellerGroup sg : preview.getSellers()) {
            for (CheckoutPreviewResponse.PreviewItem item : sg.getItems()) {
                UUID variantId = UUID.fromString(item.getVariantId());
                variantIds.add(variantId);

                try {
                    inventoryService.reserveStock(variantId, item.getQuantity(), sessionId);
                } catch (AppException e) {
                    log.warn("Stock reservation failed for variant {}: {}",
                            item.getVariantId(), e.getMessage());
                    throw new AppException(ErrorCode.INSUFFICIENT_STOCK,
                            "Không đủ hàng cho sản phẩm: " + item.getProductName());
                }

                Map<String, Object> orderItem = new LinkedHashMap<>();
                orderItem.put("customer_id", item.getCustomerId());
                orderItem.put("variant_id", item.getVariantId());
                orderItem.put("sku_code", item.getSkuCode());
                orderItem.put("product_name", item.getProductName());
                orderItem.put("variant_name", item.getVariantName());
                orderItem.put("price_snapshot", item.getPriceSnapshot());
                orderItem.put("quantity", item.getQuantity());
                orderItem.put("image_url", item.getImageUrl());
                orderItem.put("seller_id", item.getSellerId());
                orderItem.put("fs_item_id", item.getFsItemId());
                orderItems.add(orderItem);

                totalItems += item.getQuantity();
                totalAmount = totalAmount.add(item.getSubtotal());
            }
        }

        Map<String, Object> sessionPayload = new LinkedHashMap<>();
        sessionPayload.put("session_id", sessionId);
        sessionPayload.put("customer_id", customerId);
        sessionPayload.put("address_id", request.getAddressId());
        sessionPayload.put("preview_token", previewToken);
        sessionPayload.put("items", orderItems);
        sessionPayload.put("total_amount", totalAmount);
        sessionPayload.put("total_items", totalItems);
        sessionPayload.put("address_snapshot", addressSnapshot);
        sessionPayload.put("created_at", Instant.now().toString());

        String sessionKey = CHECKOUT_SESSION_PREFIX + sessionId;
        redisTemplate().opsForValue().set(sessionKey,
                toJson(sessionPayload), CHECKOUT_SESSION_TTL);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event_id", "evt_" + System.currentTimeMillis());
        event.put("event_type", "order.checkout_submitted");
        event.put("timestamp", Instant.now().toString());
        event.put("session_id", sessionId);
        event.put("customer_id", customerId);
        event.put("address_id", request.getAddressId());
        event.put("preview_token", previewToken);
        event.put("items", orderItems);
        event.put("total_amount", totalAmount);
        event.put("total_items", totalItems);
        event.put("address_snapshot", addressSnapshot);

        kafkaTemplate.send(KafkaTopics.ORDER_CHECKOUT_SUBMITTED,
                String.valueOf(customerId), toJson(event));

        log.info("Checkout submitted: sessionId={}, customerId={}, totalItems={}, totalAmount={}",
                sessionId, customerId, totalItems, totalAmount);

        checkoutPreviewService.invalidateToken(previewToken);

        return ApiResponse.success(CheckoutSubmitResponse.builder()
                .sessionId(sessionId)
                .totalItems(totalItems)
                .totalAmount(totalAmount)
                .createdAt(Instant.now())
                .message("Checkout đã được gửi. Đơn hàng đang trong quá trình xử lý.")
                .build());
    }

    private void revalidateStock(CheckoutPreviewResponse preview) {
        Set<String> variantIds = preview.getSellers().stream()
                .flatMap(sg -> sg.getItems().stream())
                .map(CheckoutPreviewResponse.PreviewItem::getVariantId)
                .collect(java.util.stream.Collectors.toSet());

        List<ProductVariant> variants = variantRepository.findAllById(
                variantIds.stream().map(UUID::fromString).toList());
        Map<String, ProductVariant> variantMap = variants.stream()
                .collect(java.util.stream.Collectors.toMap(
                        v -> v.getId().toString(), v -> v));

        List<PreviewItemError> errors = new ArrayList<>();

        for (CheckoutPreviewResponse.PreviewSellerGroup sg : preview.getSellers()) {
            for (CheckoutPreviewResponse.PreviewItem item : sg.getItems()) {
                ProductVariant variant = variantMap.get(item.getVariantId());

                if (variant == null || variant.getDeletedAt() != null) {
                    errors.add(PreviewItemError.builder()
                            .customerId(item.getCustomerId())
                            .variantId(item.getVariantId())
                            .sellerId(item.getSellerId())
                            .reason("VARIANT_UNAVAILABLE")
                            .build());
                    continue;
                }

                if (variant.getStatus() != VariantStatus.ACTIVE) {
                    errors.add(PreviewItemError.builder()
                            .customerId(item.getCustomerId())
                            .variantId(item.getVariantId())
                            .sellerId(item.getSellerId())
                            .reason("VARIANT_INACTIVE")
                            .build());
                    continue;
                }

                if (variant.getPrice().compareTo(item.getPriceSnapshot()) != 0) {
                    errors.add(PreviewItemError.builder()
                            .customerId(item.getCustomerId())
                            .variantId(item.getVariantId())
                            .sellerId(item.getSellerId())
                            .reason("PRICE_CHANGED")
                            .currentValue(variant.getPrice().toString())
                            .expectedValue(item.getPriceSnapshot().toString())
                            .build());
                    continue;
                }

                if (variant.getStockQuantity() == 0) {
                    errors.add(PreviewItemError.builder()
                            .customerId(item.getCustomerId())
                            .variantId(item.getVariantId())
                            .sellerId(item.getSellerId())
                            .reason("OUT_OF_STOCK")
                            .currentValue("0")
                            .expectedValue(String.valueOf(item.getQuantity()))
                            .build());
                    continue;
                }

                if (variant.getStockQuantity() < item.getQuantity()) {
                    errors.add(PreviewItemError.builder()
                            .customerId(item.getCustomerId())
                            .variantId(item.getVariantId())
                            .sellerId(item.getSellerId())
                            .reason("INSUFFICIENT_STOCK")
                            .currentValue(String.valueOf(variant.getStockQuantity()))
                            .expectedValue(String.valueOf(item.getQuantity()))
                            .build());
                }
            }
        }

        if (!errors.isEmpty()) {
            String errorJson = toJson(CheckoutPreviewError.builder()
                    .error("STOCK_CHANGED")
                    .message("Tồn kho hoặc giá đã thay đổi. Vui lòng làm mới giỏ hàng.")
                    .details(errors)
                    .build());
            throw new AppException(ErrorCode.CONFLICT, errorJson);
        }
    }

    private Map<String, Object> fetchAddress(Long customerId, Long addressId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", customerId);
        payload.put("address_id", addressId);

        Map<String, Object> response = kafkaReplyService.sendAndReceive(KafkaTopics.ORDER_ADDRESS_REQUEST, payload);
        Object error = response.get("error");
        if (Boolean.TRUE.equals(error)
                || "true".equalsIgnoreCase(String.valueOf(error))
                || response.get("addressId") == null) {
            throw new AppException(ErrorCode.CONFLICT, "Dia chi giao hang khong hop le");
        }

        return response;
    }

    private String buildAddressSnapshot(Map<String, Object> addressInfo) {
        try {
            Map<String, Object> addr = new LinkedHashMap<>();
            addr.put("address_id", toLong(addressInfo.get("addressId")));
            addr.put("province_id", toLong(addressInfo.get("provinceId")));
            addr.put("district_id", toLong(addressInfo.get("districtId")));
            addr.put("full_address", addressInfo.get("fullAddress"));
            return objectMapper.writeValueAsString(addr);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize address snapshot", e);
            return "{}";
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate() {
        return checkoutPreviewService.getRedisTemplate();
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize payload", e);
            return "{}";
        }
    }
}
