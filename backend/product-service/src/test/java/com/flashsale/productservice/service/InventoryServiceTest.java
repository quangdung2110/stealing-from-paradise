package com.flashsale.productservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.productservice.dto.cart.ReservationResponse;
import com.flashsale.productservice.dto.inventory.InventoryResponse;
import com.flashsale.productservice.entity.*;
import com.flashsale.productservice.repository.ProductRepository;
import com.flashsale.productservice.repository.ProductVariantRepository;
import com.flashsale.productservice.repository.StockReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryServiceTest {

    @Mock
    private ProductVariantRepository variantRepository;
    @Mock
    private StockReservationRepository reservationRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private StockCacheService stockCacheService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private DefaultRedisScript<Long> stockDecrementScript;
    @Mock
    private DefaultRedisScript<Long> stockIncrementScript;

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(
                variantRepository,
                reservationRepository,
                productRepository,
                kafkaTemplate,
                objectMapper,
                stockCacheService,
                stockDecrementScript,
                stockIncrementScript);
    }

    private void stubRedisDecrementSuccess(UUID variantId) {
        when(stockCacheService.getRedisTemplate()).thenReturn(redisTemplate);
        when(stockCacheService.key(variantId)).thenReturn("stock:available:" + variantId);
        when(redisTemplate.execute(eq(stockDecrementScript), anyList(), any(Object[].class)))
                .thenReturn(InventoryService.REDIS_OK);
    }

    @Test
    void getInventoryShouldReturnResponseForValidVariant() {
        UUID variantId = UUID.randomUUID();
        ProductVariant variant = ProductVariant.builder()
                .id(variantId)
                .variantCode("SKU-001")
                .variantName("Test Variant")
                .price(new BigDecimal("100"))
                .stockQuantity(50)
                .status(VariantStatus.ACTIVE)
                .build();
        when(variantRepository.findByVariantCode("SKU-001"))
                .thenReturn(Optional.of(variant));
        when(reservationRepository.sumQuantityByVariantIdAndStatus(any(), any()))
                .thenReturn(0);

        ApiResponse<InventoryResponse> response = inventoryService.getInventory("SKU-001");

        assertNotNull(response.getData());
        assertEquals(variantId, response.getData().getVariantId());
        assertEquals(50, response.getData().getStockAvailable());
    }

    @Test
    void getInventoryShouldThrowWhenVariantNotFound() {
        when(variantRepository.findByVariantCode("MISSING")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> inventoryService.getInventory("MISSING"));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void reserveStockShouldThrowWhenQuantityNotPositive() {
        AppException ex = assertThrows(AppException.class,
                () -> inventoryService.reserveStock(UUID.randomUUID(), 0, "session-1"));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void reserveStockShouldThrowWhenRedisInsufficient() {
        UUID variantId = UUID.randomUUID();
        when(stockCacheService.getRedisTemplate()).thenReturn(redisTemplate);
        when(stockCacheService.key(variantId)).thenReturn("stock:available:" + variantId);
        when(redisTemplate.execute(eq(stockDecrementScript), anyList(), any(Object[].class)))
                .thenReturn(InventoryService.REDIS_INSUFFICIENT);

        AppException ex = assertThrows(AppException.class,
                () -> inventoryService.reserveStock(variantId, 10, "session-1"));
        assertEquals(ErrorCode.INSUFFICIENT_STOCK, ex.getErrorCode());
    }

    @Test
    void reserveStockShouldThrowWhenRedisCacheMissAndVariantNotFound() {
        UUID variantId = UUID.randomUUID();
        when(stockCacheService.getRedisTemplate()).thenReturn(redisTemplate);
        when(stockCacheService.key(variantId)).thenReturn("stock:available:" + variantId);
        when(redisTemplate.execute(eq(stockDecrementScript), anyList(), any(Object[].class)))
                .thenReturn(InventoryService.REDIS_NOT_FOUND);
        when(stockCacheService.loadAndCache(variantId)).thenReturn(null);

        AppException ex = assertThrows(AppException.class,
                () -> inventoryService.reserveStock(variantId, 10, "session-1"));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void reserveStockShouldThrowWhenDbCasFails() {
        UUID variantId = UUID.randomUUID();
        stubRedisDecrementSuccess(variantId);
        when(variantRepository.decrementIfEnough(variantId, 3)).thenReturn(0);

        AppException ex = assertThrows(AppException.class,
                () -> inventoryService.reserveStock(variantId, 3, "session-1"));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void reserveStockShouldSucceedAndInsertReservation() {
        UUID variantId = UUID.randomUUID();
        stubRedisDecrementSuccess(variantId);

        UUID reservationId = UUID.randomUUID();
        StockReservation savedReservation = StockReservation.builder()
                .id(reservationId)
                .variantId(variantId)
                .sessionId("session-1")
                .quantity(3)
                .status(ReservationStatus.PENDING)
                .build();
        when(variantRepository.decrementIfEnough(variantId, 3)).thenReturn(1);
        when(reservationRepository.save(any(StockReservation.class))).thenReturn(savedReservation);

        ProductVariant updatedVariant = ProductVariant.builder()
                .id(variantId)
                .productId(UUID.randomUUID())
                .stockQuantity(47)
                .status(VariantStatus.ACTIVE)
                .build();
        when(variantRepository.findById(variantId)).thenReturn(Optional.of(updatedVariant));
        when(variantRepository.findByProductIdAndDeletedAtIsNull(updatedVariant.getProductId()))
                .thenReturn(java.util.List.of(updatedVariant));
        when(productRepository.findById(updatedVariant.getProductId())).thenReturn(Optional.empty());

        ApiResponse<ReservationResponse> response =
                inventoryService.reserveStock(variantId, 3, "session-1");

        assertNotNull(response.getData());
        assertEquals(3, response.getData().getQuantity());
        assertEquals(ReservationStatus.PENDING.name(), response.getData().getStatus());
        verify(reservationRepository).save(any(StockReservation.class));
    }

    @Test
    void adjustStockShouldThrowWhenNegativeResult() {
        UUID variantId = UUID.randomUUID();
        ProductVariant variant = ProductVariant.builder()
                .id(variantId)
                .productId(UUID.randomUUID())
                .stockQuantity(10)
                .status(VariantStatus.ACTIVE)
                .build();
        when(variantRepository.findByVariantCode("SKU-001"))
                .thenReturn(Optional.of(variant));
        Product product = Product.builder()
                .id(variant.getProductId())
                .sellerId(1L)
                .build();
        when(productRepository.findById(variant.getProductId()))
                .thenReturn(Optional.of(product));

        UserDetailsImpl user = UserDetailsImpl.builder().id(1L).build();

        AppException ex = assertThrows(AppException.class,
                () -> inventoryService.adjustStock("SKU-001", -20, null, "MANUAL", user));
        assertEquals(ErrorCode.INSUFFICIENT_STOCK, ex.getErrorCode());
    }

    @Test
    void adjustStockShouldThrowWhenVersionMismatch() {
        UUID variantId = UUID.randomUUID();
        ProductVariant variant = ProductVariant.builder()
                .id(variantId)
                .productId(UUID.randomUUID())
                .stockQuantity(10)
                .version(5)
                .build();
        when(variantRepository.findByVariantCode("SKU-001"))
                .thenReturn(Optional.of(variant));
        Product product = Product.builder()
                .id(variant.getProductId())
                .sellerId(1L)
                .build();
        when(productRepository.findById(variant.getProductId()))
                .thenReturn(Optional.of(product));

        UserDetailsImpl user = UserDetailsImpl.builder().id(1L).build();

        AppException ex = assertThrows(AppException.class,
                () -> inventoryService.adjustStock("SKU-001", 5, 3, "MANUAL", user));
        assertEquals(ErrorCode.OPTIMISTIC_LOCK, ex.getErrorCode());
    }

    @Test
    void adjustStockShouldThrowWhenNotSeller() {
        UUID variantId = UUID.randomUUID();
        ProductVariant variant = ProductVariant.builder()
                .id(variantId)
                .productId(UUID.randomUUID())
                .stockQuantity(10)
                .build();
        when(variantRepository.findByVariantCode("SKU-001"))
                .thenReturn(Optional.of(variant));
        Product product = Product.builder()
                .id(variant.getProductId())
                .sellerId(5L)
                .build();
        when(productRepository.findById(variant.getProductId()))
                .thenReturn(Optional.of(product));

        UserDetailsImpl user = UserDetailsImpl.builder().id(1L).build();

        AppException ex = assertThrows(AppException.class,
                () -> inventoryService.adjustStock("SKU-001", 5, null, "MANUAL", user));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void releaseReservationShouldRestoreStock() throws Exception {
        UUID reservationId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        StockReservation reservation = StockReservation.builder()
                .id(reservationId)
                .variantId(variantId)
                .quantity(5)
                .status(ReservationStatus.PENDING)
                .build();
        when(reservationRepository.findById(reservationId))
                .thenReturn(Optional.of(reservation));

        when(stockCacheService.getRedisTemplate()).thenReturn(redisTemplate);
        when(stockCacheService.key(variantId)).thenReturn("stock:available:" + variantId);
        when(redisTemplate.execute(eq(stockIncrementScript), anyList(), any(Object[].class)))
                .thenReturn(50L);
        when(variantRepository.incrementBy(variantId, 5)).thenReturn(1);

        ProductVariant variant = ProductVariant.builder()
                .id(variantId)
                .productId(UUID.randomUUID())
                .stockQuantity(50)
                .status(VariantStatus.ACTIVE)
                .build();
        when(variantRepository.findById(variantId)).thenReturn(Optional.of(variant));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(variantRepository.findByProductIdAndDeletedAtIsNull(variant.getProductId()))
                .thenReturn(java.util.List.of(variant));
        when(productRepository.findById(variant.getProductId())).thenReturn(Optional.empty());

        ApiResponse<Void> response = inventoryService.releaseReservation(reservationId);

        assertNotNull(response);
        assertEquals(ReservationStatus.RELEASED, reservation.getStatus());
        verify(variantRepository).incrementBy(variantId, 5);
        verify(redisTemplate).execute(eq(stockIncrementScript), anyList(), any(Object[].class));
    }

    @Test
    void confirmReservationShouldThrowWhenNotPending() {
        UUID reservationId = UUID.randomUUID();
        StockReservation reservation = StockReservation.builder()
                .id(reservationId)
                .status(ReservationStatus.CONFIRMED)
                .build();
        when(reservationRepository.findById(reservationId))
                .thenReturn(Optional.of(reservation));

        AppException ex = assertThrows(AppException.class,
                () -> inventoryService.confirmReservation(reservationId));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void confirmReservationShouldMarkAsConfirmed() {
        UUID reservationId = UUID.randomUUID();
        StockReservation reservation = StockReservation.builder()
                .id(reservationId)
                .status(ReservationStatus.PENDING)
                .build();
        when(reservationRepository.findById(reservationId))
                .thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(StockReservation.class))).thenReturn(reservation);

        ApiResponse<Void> response = inventoryService.confirmReservation(reservationId);

        assertNotNull(response);
        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
        verify(redisTemplate, never()).execute(eq(stockDecrementScript), anyList(), any(Object[].class));
        verify(redisTemplate, never()).execute(eq(stockIncrementScript), anyList(), any(Object[].class));
    }

    @Test
    void warmUpShouldSetAllVariantsInRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stockCacheService.getRedisTemplate()).thenReturn(redisTemplate);

        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(stockCacheService.key(id1)).thenReturn("stock:available:" + id1);
        when(stockCacheService.key(id2)).thenReturn("stock:available:" + id2);

        java.util.List<ProductVariant> variants = java.util.List.of(
                ProductVariant.builder().id(id1).stockQuantity(10).build(),
                ProductVariant.builder().id(id2).stockQuantity(20).build()
        );
        when(variantRepository.findAll()).thenReturn(variants);

        com.flashsale.productservice.service.StockCacheService realCacheService =
                new com.flashsale.productservice.service.StockCacheService(redisTemplate, variantRepository);
        realCacheService.warmUp();

        verify(valueOperations).set("stock:available:" + id1, "10");
        verify(valueOperations).set("stock:available:" + id2, "20");
    }
}
