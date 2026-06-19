package com.flashsale.productservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.productservice.dto.checkout.CheckoutPreviewRequest;
import com.flashsale.productservice.dto.checkout.CheckoutPreviewResponse;
import com.flashsale.productservice.entity.CartItem;
import com.flashsale.productservice.entity.ProductVariant;
import com.flashsale.productservice.entity.VariantStatus;
import com.flashsale.productservice.repository.CartItemRepository;
import com.flashsale.productservice.repository.CartRepository;
import com.flashsale.productservice.repository.ProductVariantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckoutPreviewServiceTest {

    @Mock
    private CartRepository cartRepository;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private ProductVariantRepository variantRepository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private CheckoutPreviewService checkoutPreviewService;

    @Test
    void validateAndGetPreviewShouldThrowWhenTokenBlank() {
        AppException ex = assertThrows(AppException.class,
                () -> checkoutPreviewService.validateAndGetPreview("", 1L));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());

        AppException ex2 = assertThrows(AppException.class,
                () -> checkoutPreviewService.validateAndGetPreview(null, 1L));
        assertEquals(ErrorCode.BAD_REQUEST, ex2.getErrorCode());
    }

    @Test
    void validateAndGetPreviewShouldThrowWhenTokenNotFound() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        AppException ex = assertThrows(AppException.class,
                () -> checkoutPreviewService.validateAndGetPreview("nonexistent-token", 1L));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void validateAndGetPreviewShouldThrowWhenCustomerMismatch() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String token = "valid-token";
        CheckoutPreviewResponse cached = CheckoutPreviewResponse.builder()
                .customerId(99L)
                .build();
        String json = "{\"customerId\":99}";
        when(valueOperations.get(anyString())).thenReturn(json);
        when(objectMapper.readValue(json, CheckoutPreviewResponse.class)).thenReturn(cached);

        AppException ex = assertThrows(AppException.class,
                () -> checkoutPreviewService.validateAndGetPreview(token, 1L));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void invalidateTokenShouldDeleteFromRedis() {
        checkoutPreviewService.invalidateToken("some-token");
        verify(redisTemplate).delete(contains("checkout:preview:some-token"));
    }

    @Test
    void invalidateTokenShouldSkipWhenBlank() {
        checkoutPreviewService.invalidateToken("");
        checkoutPreviewService.invalidateToken(null);
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void generatePreviewShouldThrowWhenItemIdsEmpty() {
        CheckoutPreviewRequest req = new CheckoutPreviewRequest();
        req.setItemIds(Collections.emptyList());

        AppException ex = assertThrows(AppException.class,
                () -> checkoutPreviewService.generatePreview(req, 1L));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void generatePreviewShouldThrowWhenItemIdsNull() {
        CheckoutPreviewRequest req = new CheckoutPreviewRequest();
        req.setItemIds(null);

        AppException ex = assertThrows(AppException.class,
                () -> checkoutPreviewService.generatePreview(req, 1L));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void generatePreviewShouldThrowWhenCartItemsNotFound() {
        UUID variantId = UUID.randomUUID();
        String itemId = "1:" + variantId;
        CheckoutPreviewRequest req = new CheckoutPreviewRequest();
        req.setItemIds(List.of(itemId));

        when(cartItemRepository.findByCustomerIdAndVariantIds(eq(1L), anyList()))
                .thenReturn(Collections.emptyList());

        AppException ex = assertThrows(AppException.class,
                () -> checkoutPreviewService.generatePreview(req, 1L));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void generatePreviewShouldThrowWhenCustomerIdMismatch() {
        UUID variantId = UUID.randomUUID();
        String itemId = "2:" + variantId;
        CheckoutPreviewRequest req = new CheckoutPreviewRequest();
        req.setItemIds(List.of(itemId));

        AppException ex = assertThrows(AppException.class,
                () -> checkoutPreviewService.generatePreview(req, 1L));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void generatePreviewShouldRejectVariantUnavailable() {
        UUID variantId = UUID.randomUUID();
        String itemId = "1:" + variantId;
        CheckoutPreviewRequest req = new CheckoutPreviewRequest();
        req.setItemIds(List.of(itemId));

        CartItem cartItem = CartItem.builder()
                .customerId(1L)
                .variantId(variantId)
                .quantity(1)
                .priceSnapshot(BigDecimal.TEN)
                .build();
        when(cartItemRepository.findByCustomerIdAndVariantIds(eq(1L), anyList()))
                .thenReturn(List.of(cartItem));
        when(variantRepository.findAllById(anyList()))
                .thenReturn(Collections.emptyList());

        AppException ex = assertThrows(AppException.class,
                () -> checkoutPreviewService.generatePreview(req, 1L));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void generatePreviewShouldRejectInactiveVariant() {
        UUID variantId = UUID.randomUUID();
        String itemId = "1:" + variantId;
        CheckoutPreviewRequest req = new CheckoutPreviewRequest();
        req.setItemIds(List.of(itemId));

        CartItem cartItem = CartItem.builder()
                .customerId(1L)
                .variantId(variantId)
                .quantity(1)
                .priceSnapshot(BigDecimal.TEN)
                .sellerId(1L)
                .build();
        ProductVariant variant = ProductVariant.builder()
                .id(variantId)
                .status(VariantStatus.INACTIVE)
                .stockQuantity(10)
                .price(BigDecimal.TEN)
                .build();
        when(cartItemRepository.findByCustomerIdAndVariantIds(eq(1L), anyList()))
                .thenReturn(List.of(cartItem));
        when(variantRepository.findAllById(anyList()))
                .thenReturn(List.of(variant));

        AppException ex = assertThrows(AppException.class,
                () -> checkoutPreviewService.generatePreview(req, 1L));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void generatePreviewShouldRejectInsufficientStock() {
        UUID variantId = UUID.randomUUID();
        String itemId = "1:" + variantId;
        CheckoutPreviewRequest req = new CheckoutPreviewRequest();
        req.setItemIds(List.of(itemId));

        CartItem cartItem = CartItem.builder()
                .customerId(1L)
                .variantId(variantId)
                .quantity(10)
                .priceSnapshot(BigDecimal.TEN)
                .sellerId(1L)
                .build();
        ProductVariant variant = ProductVariant.builder()
                .id(variantId)
                .status(VariantStatus.ACTIVE)
                .stockQuantity(3)
                .price(BigDecimal.TEN)
                .build();
        when(cartItemRepository.findByCustomerIdAndVariantIds(eq(1L), anyList()))
                .thenReturn(List.of(cartItem));
        when(variantRepository.findAllById(anyList()))
                .thenReturn(List.of(variant));

        AppException ex = assertThrows(AppException.class,
                () -> checkoutPreviewService.generatePreview(req, 1L));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void revalidateStockShouldThrowWhenVariantMissing() {
        CheckoutPreviewResponse preview = CheckoutPreviewResponse.builder()
                .sellers(List.of(CheckoutPreviewResponse.PreviewSellerGroup.builder()
                        .sellerId(1L)
                        .items(List.of(CheckoutPreviewResponse.PreviewItem.builder()
                                .variantId(UUID.randomUUID().toString())
                                .quantity(1)
                                .build()))
                        .build()))
                .build();

        when(variantRepository.findAllById(anyList())).thenReturn(Collections.emptyList());

        AppException ex = assertThrows(AppException.class,
                () -> checkoutPreviewService.revalidateStock(preview));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void revalidateStockShouldThrowWhenPriceChanged() {
        UUID variantId = UUID.randomUUID();
        CheckoutPreviewResponse preview = CheckoutPreviewResponse.builder()
                .sellers(List.of(CheckoutPreviewResponse.PreviewSellerGroup.builder()
                        .sellerId(1L)
                        .items(List.of(CheckoutPreviewResponse.PreviewItem.builder()
                                .variantId(variantId.toString())
                                .quantity(1)
                                .priceSnapshot(new BigDecimal("100"))
                                .build()))
                        .build()))
                .build();

        ProductVariant variant = ProductVariant.builder()
                .id(variantId)
                .status(VariantStatus.ACTIVE)
                .stockQuantity(10)
                .price(new BigDecimal("120"))
                .build();
        when(variantRepository.findAllById(anyList())).thenReturn(List.of(variant));

        AppException ex = assertThrows(AppException.class,
                () -> checkoutPreviewService.revalidateStock(preview));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }
}
