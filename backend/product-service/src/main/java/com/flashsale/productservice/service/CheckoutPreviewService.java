package com.flashsale.productservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.productservice.dto.checkout.CheckoutPreviewError;
import com.flashsale.productservice.dto.checkout.CheckoutPreviewRequest;
import com.flashsale.productservice.dto.checkout.CheckoutPreviewResponse;
import com.flashsale.productservice.entity.Cart;
import com.flashsale.productservice.entity.CartItem;
import com.flashsale.productservice.entity.ProductVariant;
import com.flashsale.productservice.entity.VariantStatus;
import com.flashsale.productservice.repository.CartItemRepository;
import com.flashsale.productservice.repository.CartRepository;
import com.flashsale.productservice.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/*
Thứ tự validate stock thống nhất cho cả Preview và Submit
1. variant == null || deleted?     → VARIANT_UNAVAILABLE  (+sellerId)
2. status != ACTIVE?              → VARIANT_INACTIVE      (+sellerId)
3. price != priceSnapshot?        → PRICE_CHANGED         (+sellerId)
4. stockQuantity == 0?            → OUT_OF_STOCK          (+sellerId)
5. stockQuantity < quantity?       → INSUFFICIENT_STOCK    (+sellerId)
*/

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutPreviewService {

    private static final String PREVIEW_TOKEN_PREFIX = "checkout:preview:";
    private static final Duration PREVIEW_TOKEN_TTL = Duration.ofMinutes(10);

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository variantRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ApiResponse<CheckoutPreviewResponse> generatePreview(
            CheckoutPreviewRequest request, Long customerId) {

        List<String> rawItemIds = request.getItemIds();
        if (rawItemIds == null || rawItemIds.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không có item_ids hợp lệ");
        }

        // Parse "customerId:variantId" format
        List<ParsedItemKey> parsedItems = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        for (String raw : rawItemIds) {
            try {
                parsedItems.add(ParsedItemKey.parse(raw));
            } catch (IllegalArgumentException e) {
                invalid.add(raw);
            }
        }
        if (!invalid.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "item_ids không hợp lệ: " + invalid);
        }

        // Validate all customerId in request match authenticated customer
        for (ParsedItemKey pk : parsedItems) {
            if (!pk.customerId.equals(customerId)) {
                throw new AppException(ErrorCode.FORBIDDEN,
                        "Không được phép checkout items của người khác");
            }
        }

        List<UUID> variantIds = parsedItems.stream()
                .map(pk -> pk.variantId)
                .toList();

        List<CartItem> cartItems = cartItemRepository.findByCustomerIdAndVariantIds(customerId, variantIds);
        if (cartItems.size() != variantIds.size()) {
            Set<UUID> foundVariants = cartItems.stream()
                    .map(CartItem::getVariantId)
                    .collect(Collectors.toSet());
            List<UUID> missing = variantIds.stream()
                    .filter(id -> !foundVariants.contains(id))
                    .toList();
            throw new AppException(ErrorCode.NOT_FOUND,
                    "Một số item không tìm thấy trong giỏ hàng: " + missing);
        }

        List<CheckoutPreviewError.PreviewItemError> errors = new ArrayList<>();
        List<CheckoutPreviewResponse.PreviewItem> validItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        int totalItems = 0;

        List<ProductVariant> variants = variantRepository.findAllById(variantIds);
        Map<UUID, ProductVariant> variantMap = variants.stream()
                .collect(Collectors.toMap(ProductVariant::getId, v -> v));

        for (CartItem item : cartItems) {
            ProductVariant variant = variantMap.get(item.getVariantId());

            if (variant == null || variant.getDeletedAt() != null) {
                errors.add(CheckoutPreviewError.PreviewItemError.builder()
                        .customerId(item.getCustomerId())
                        .variantId(item.getVariantId().toString())
                        .sellerId(item.getSellerId())
                        .reason("VARIANT_UNAVAILABLE")
                        .currentValue("deleted or inactive")
                        .expectedValue("active variant")
                        .build());
                continue;
            }

            if (variant.getStatus() != VariantStatus.ACTIVE) {
                errors.add(CheckoutPreviewError.PreviewItemError.builder()
                        .customerId(item.getCustomerId())
                        .variantId(item.getVariantId().toString())
                        .sellerId(item.getSellerId())
                        .reason("VARIANT_INACTIVE")
                        .currentValue(variant.getStatus().name())
                        .expectedValue("ACTIVE")
                        .build());
                continue;
            }

            if (variant.getPrice().compareTo(item.getPriceSnapshot()) != 0) {
                errors.add(CheckoutPreviewError.PreviewItemError.builder()
                        .customerId(item.getCustomerId())
                        .variantId(item.getVariantId().toString())
                        .sellerId(item.getSellerId())
                        .reason("PRICE_CHANGED")
                        .currentValue(variant.getPrice().toString())
                        .expectedValue(item.getPriceSnapshot().toString())
                        .build());
                continue;
            }

            if (variant.getStockQuantity() == 0) {
                errors.add(CheckoutPreviewError.PreviewItemError.builder()
                        .customerId(item.getCustomerId())
                        .variantId(item.getVariantId().toString())
                        .sellerId(item.getSellerId())
                        .reason("OUT_OF_STOCK")
                        .currentValue("0")
                        .expectedValue(String.valueOf(item.getQuantity()))
                        .build());
                continue;
            }

            if (variant.getStockQuantity() < item.getQuantity()) {
                errors.add(CheckoutPreviewError.PreviewItemError.builder()
                        .customerId(item.getCustomerId())
                        .variantId(item.getVariantId().toString())
                        .sellerId(item.getSellerId())
                        .reason("INSUFFICIENT_STOCK")
                        .currentValue(String.valueOf(variant.getStockQuantity()))
                        .expectedValue(String.valueOf(item.getQuantity()))
                        .build());
                continue;
            }

            BigDecimal subtotal = item.getPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity()));
            validItems.add(CheckoutPreviewResponse.PreviewItem.builder()
                    .customerId(item.getCustomerId())
                    .variantId(item.getVariantId().toString())
                    .skuCode(variant.getVariantCode())
                    .productName(item.getVariantNameSnapshot())
                    .variantName(variant.getVariantName())
                    .priceSnapshot(item.getPriceSnapshot())
                    .quantity(item.getQuantity())
                    .imageUrl(item.getVariantImageSnapshot())
                    .subtotal(subtotal)
                    .fsItemId(null)
                    .sellerId(item.getSellerId())
                    .build());
            totalAmount = totalAmount.add(subtotal);
            totalItems += item.getQuantity();
        }

        if (!errors.isEmpty()) {
            CheckoutPreviewError error = CheckoutPreviewError.builder()
                    .error("CART_ITEMS_CHANGED")
                    .message("Một số sản phẩm trong giỏ hàng đã thay đổi. Vui lòng làm mới giỏ hàng.")
                    .details(errors)
                    .build();
            try {
                throw new AppException(ErrorCode.CONFLICT, objectMapper.writeValueAsString(error));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new AppException(ErrorCode.CONFLICT, "Cart items changed");
            }
        }

        String previewToken = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(PREVIEW_TOKEN_TTL);

        CheckoutPreviewResponse response = CheckoutPreviewResponse.builder()
                .previewToken(previewToken)
                .expiresAt(expiresAt)
                .customerId(customerId)
                .sellers(groupBySeller(validItems))
                .totalItems(totalItems)
                .totalAmount(totalAmount)
                .allValid(true)
                .build();

        String tokenValue = buildTokenValue(response);
        redisTemplate.opsForValue().set(
                PREVIEW_TOKEN_PREFIX + previewToken,
                tokenValue,
                PREVIEW_TOKEN_TTL
        );

        log.info("Checkout preview generated: token={}, customerId={}, itemCount={}",
                previewToken, customerId, validItems.size());
        return ApiResponse.success(response);
    }

    @Transactional(readOnly = true)
    public CheckoutPreviewResponse validateAndGetPreview(String previewToken, Long customerId) {
        if (previewToken == null || previewToken.isBlank()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "preview_token là bắt buộc");
        }

        String key = PREVIEW_TOKEN_PREFIX + previewToken;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            throw new AppException(ErrorCode.NOT_FOUND, "preview_token không tồn tại hoặc đã hết hạn");
        }

        try {
            CheckoutPreviewResponse cached = objectMapper.readValue(value, CheckoutPreviewResponse.class);
            if (!cached.getCustomerId().equals(customerId)) {
                throw new AppException(ErrorCode.FORBIDDEN, "preview_token không thuộc về bạn");
            }
            return cached;
        } catch (JsonProcessingException e) {
            log.error("Failed to parse preview token: {}", previewToken, e);
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Lỗi khi xác thực preview_token");
        }
    }

    @Transactional(readOnly = true)
    public void revalidateStock(CheckoutPreviewResponse preview) {
        Set<String> variantIds = preview.getSellers().stream()
                .flatMap(s -> s.getItems().stream())
                .map(CheckoutPreviewResponse.PreviewItem::getVariantId)
                .collect(Collectors.toSet());

        List<ProductVariant> variants = variantRepository.findAllById(
                variantIds.stream().map(UUID::fromString).toList());
        Map<String, ProductVariant> variantMap = variants.stream()
                .collect(Collectors.toMap(v -> v.getId().toString(), v -> v));

        List<CheckoutPreviewError.PreviewItemError> errors = new ArrayList<>();

        for (CheckoutPreviewResponse.PreviewSellerGroup seller : preview.getSellers()) {
            for (CheckoutPreviewResponse.PreviewItem item : seller.getItems()) {
                ProductVariant variant = variantMap.get(item.getVariantId());

                if (variant == null || variant.getDeletedAt() != null) {
                    errors.add(CheckoutPreviewError.PreviewItemError.builder()
                            .customerId(item.getCustomerId())
                            .variantId(item.getVariantId())
                            .sellerId(item.getSellerId())
                            .reason("VARIANT_UNAVAILABLE")
                            .build());
                    continue;
                }

                if (variant.getStatus() != VariantStatus.ACTIVE) {
                    errors.add(CheckoutPreviewError.PreviewItemError.builder()
                            .customerId(item.getCustomerId())
                            .variantId(item.getVariantId())
                            .sellerId(item.getSellerId())
                            .reason("VARIANT_INACTIVE")
                            .build());
                    continue;
                }

                if (variant.getPrice().compareTo(item.getPriceSnapshot()) != 0) {
                    errors.add(CheckoutPreviewError.PreviewItemError.builder()
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
                    errors.add(CheckoutPreviewError.PreviewItemError.builder()
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
                    errors.add(CheckoutPreviewError.PreviewItemError.builder()
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
            String errorJson;
            try {
                errorJson = objectMapper.writeValueAsString(CheckoutPreviewError.builder()
                        .error("STOCK_CHANGED")
                        .message("Tồn kho hoặc giá đã thay đổi. Vui lòng làm mới giỏ hàng.")
                        .details(errors)
                        .build());
            } catch (JsonProcessingException e) {
                errorJson = "{\"error\":\"STOCK_CHANGED\"}";
            }
            throw new AppException(ErrorCode.CONFLICT, errorJson);
        }
    }

    public void invalidateToken(String previewToken) {
        if (previewToken != null && !previewToken.isBlank()) {
            redisTemplate.delete(PREVIEW_TOKEN_PREFIX + previewToken);
            log.debug("Preview token invalidated: {}", previewToken);
        }
    }

    public StringRedisTemplate getRedisTemplate() {
        return redisTemplate;
    }

    private List<CheckoutPreviewResponse.PreviewSellerGroup> groupBySeller(
            List<CheckoutPreviewResponse.PreviewItem> items) {
        Map<Long, List<CheckoutPreviewResponse.PreviewItem>> bySeller = new LinkedHashMap<>();
        for (CheckoutPreviewResponse.PreviewItem item : items) {
            Long sellerId = item.getSellerId() != null ? item.getSellerId() : 0L;
            bySeller.computeIfAbsent(sellerId, k -> new ArrayList<>()).add(item);
        }

        return bySeller.entrySet().stream()
                .map(e -> CheckoutPreviewResponse.PreviewSellerGroup.builder()
                        .sellerId(e.getKey())
                        .items(e.getValue())
                        .subtotal(e.getValue().stream()
                                .map(CheckoutPreviewResponse.PreviewItem::getSubtotal)
                                .reduce(BigDecimal.ZERO, BigDecimal::add))
                        .build())
                .toList();
    }

    private String buildTokenValue(CheckoutPreviewResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize preview response", e);
            return "{}";
        }
    }

    private static class ParsedItemKey {
        final Long customerId;
        final UUID variantId;

        ParsedItemKey(Long customerId, UUID variantId) {
            this.customerId = customerId;
            this.variantId = variantId;
        }

        static ParsedItemKey parse(String raw) {
            String[] parts = raw.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid format: " + raw);
            }
            return new ParsedItemKey(
                    Long.parseLong(parts[0]),
                    UUID.fromString(parts[1])
            );
        }
    }
}
