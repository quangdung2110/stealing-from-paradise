package com.flashsale.flashsaleservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.flashsaleservice.domain.model.FlashSaleItem;
import com.flashsale.flashsaleservice.domain.model.FlashSaleSession;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleItemRepository;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleSessionRepository;
import com.flashsale.flashsaleservice.dto.request.ApproveItemRequest;
import com.flashsale.flashsaleservice.dto.request.CreateFlashSaleItemRequest;
import com.flashsale.flashsaleservice.dto.request.RejectItemRequest;
import com.flashsale.flashsaleservice.dto.response.FlashSaleItemResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class FlashSaleItemService {

    private final FlashSaleItemRepository itemRepo;
    private final FlashSaleSessionRepository sessionRepo;
    private final FlashSaleSessionStateService stateService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final FlashSaleItemMapper itemMapper;
    private final WebClient webClient;

    public FlashSaleItemService(
            FlashSaleItemRepository itemRepo,
            FlashSaleSessionRepository sessionRepo,
            FlashSaleSessionStateService stateService,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            FlashSaleItemMapper itemMapper,
            @Value("${product-service.url:http://localhost:8084}") String productServiceUrl) {
        this.itemRepo = itemRepo;
        this.sessionRepo = sessionRepo;
        this.stateService = stateService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.itemMapper = itemMapper;
        this.webClient = WebClient.builder().baseUrl(productServiceUrl).build();
    }

    public Mono<FlashSaleItemResponse> createFlashSaleItem(Long sessionId, Long sellerId, CreateFlashSaleItemRequest req) {
        return sessionRepo.findById(sessionId)
                .switchIfEmpty(Mono.error(new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy phiên Flash Sale")))
                .flatMap(session -> {
                    if (session.getDeletedAt() != null) {
                        return Mono.error(new AppException(ErrorCode.NOT_FOUND, "Phiên Flash Sale đã bị xóa"));
                    }
                    if (!"UPCOMING".equals(session.getStatus())) {
                        return Mono.error(new AppException(ErrorCode.BAD_REQUEST,
                                "Phiên không còn nhận đăng ký (status=" + session.getStatus() + ")"));
                    }
                    if (session.getRegistrationDeadline() != null
                            && LocalDateTime.now().isAfter(session.getRegistrationDeadline())) {
                        return Mono.error(new AppException(ErrorCode.BAD_REQUEST,
                                "Đã hết hạn đăng ký sản phẩm cho phiên này"));
                    }

                    return stateService.isRegistrationClosed(sessionId)
                            .flatMap(closed -> {
                                if (Boolean.TRUE.equals(closed)) {
                                    return Mono.error(new AppException(ErrorCode.BAD_REQUEST,
                                            "Đã hết hạn đăng ký sản phẩm cho phiên này"));
                                }

                                return fetchVariantDetails(req.getSkuCode())
                                        .flatMap(variantInfo -> {
                                            Long variantSellerId = toLong(variantInfo.get("sellerId"));
                                            if (variantSellerId == null || !variantSellerId.equals(sellerId)) {
                                                return Mono.error(new AppException(ErrorCode.FORBIDDEN,
                                                        "Bạn không sở hữu sản phẩm (SKU) này"));
                                            }

                                            FlashSaleItem item = FlashSaleItem.builder()
                                                    .sessionId(sessionId)
                                                    .sellerId(sellerId)
                                                    .skuCode(req.getSkuCode())
                                                    .flashPrice(req.getFlashPrice())
                                                    .flashStock(req.getFlashStock())
                                                    .limitPerUser(req.getLimitPerUser() != null ? req.getLimitPerUser() : 1)
                                                    .soldQty(0)
                                                    .status("PENDING")
                                                    .build();
                                            return itemRepo.save(item);
                                        });
                            });
                })
                .doOnSuccess(this::publishItemRegisteredEvent)
                .map(itemMapper::toItemResponse);
    }

    private Mono<Map<String, Object>> fetchVariantDetails(String skuCode) {
        return webClient.get()
                .uri("/v1/products/variants/sku/{skuCode}", skuCode)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {})
                .map(ApiResponse::getData)
                .onErrorMap(ex -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy thông tin SKU sản phẩm"));
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(value.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private void publishItemRegisteredEvent(FlashSaleItem item) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event_id", "evt_" + System.currentTimeMillis() + "_" + item.getId());
            event.put("event_type", KafkaTopics.FLASH_SALE_ITEM_REGISTERED);
            event.put("fs_item_id", item.getId());
            event.put("session_id", item.getSessionId());
            event.put("sku_code", item.getSkuCode());
            event.put("seller_id", item.getSellerId());
            event.put("flash_price", item.getFlashPrice());
            event.put("flash_stock", item.getFlashStock());
            event.put("status", item.getStatus());
            event.put("timestamp", Instant.now().toString());
            kafkaTemplate.send(KafkaTopics.FLASH_SALE_ITEM_REGISTERED,
                    String.valueOf(item.getId()), objectMapper.writeValueAsString(event));
            log.info("Published flash_sale.item_registered: fsItemId={}, sessionId={}, sellerId={}",
                    item.getId(), item.getSessionId(), item.getSellerId());
        } catch (Exception e) {
            log.error("Failed to publish flash_sale.item_registered: {}", e.getMessage(), e);
        }
    }

    private void publishFlashSaleItemEvent(String topic, FlashSaleItem item, Map<String, Object> extraFields) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event_id", "evt_" + System.currentTimeMillis() + "_" + item.getId());
            event.put("event_type", topic);
            event.put("fs_item_id", item.getId());
            event.put("session_id", item.getSessionId());
            event.put("sku_code", item.getSkuCode());
            event.put("seller_id", item.getSellerId());
            event.put("flash_price", item.getFlashPrice());
            event.put("flash_stock", item.getFlashStock());
            event.put("status", item.getStatus());
            if (extraFields != null) {
                event.putAll(extraFields);
            }
            event.put("timestamp", Instant.now().toString());
            kafkaTemplate.send(topic, String.valueOf(item.getId()), objectMapper.writeValueAsString(event));
            log.info("Published {}: fsItemId={}, sessionId={}, sellerId={}",
                    topic, item.getId(), item.getSessionId(), item.getSellerId());
        } catch (Exception e) {
            log.error("Failed to publish {}: {}", topic, e.getMessage(), e);
        }
    }

    public Mono<FlashSaleItemResponse> approveItem(Long sessionId, Long itemId, ApproveItemRequest req) {
        return itemRepo.findById(itemId)
                .switchIfEmpty(Mono.error(new AppException(ErrorCode.NOT_FOUND, "Khong tim thay Flash Sale item")))
                .flatMap(item -> {
                    if (!sessionId.equals(item.getSessionId())) {
                        return Mono.error(new AppException(ErrorCode.NOT_FOUND, "Flash Sale item khong thuoc session nay"));
                    }
                    item.setStatus("APPROVED");
                    return itemRepo.save(item);
                })
                .doOnSuccess(saved -> publishFlashSaleItemEvent(KafkaTopics.FLASH_SALE_ITEM_APPROVED, saved,
                        Map.of("note", req != null && req.getNote() != null ? req.getNote() : "")))
                .map(itemMapper::toItemResponse);
    }

    public Mono<FlashSaleItemResponse> rejectItem(Long sessionId, Long itemId, RejectItemRequest req) {
        return itemRepo.findById(itemId)
                .switchIfEmpty(Mono.error(new AppException(ErrorCode.NOT_FOUND, "Khong tim thay Flash Sale item")))
                .flatMap(item -> {
                    if (!sessionId.equals(item.getSessionId())) {
                        return Mono.error(new AppException(ErrorCode.NOT_FOUND, "Flash Sale item khong thuoc session nay"));
                    }
                    item.setStatus("REJECTED");
                    return itemRepo.save(item);
                })
                .doOnSuccess(saved -> publishFlashSaleItemEvent(KafkaTopics.FLASH_SALE_ITEM_REJECTED, saved,
                        Map.of("reject_reason", req != null && req.getRejectReason() != null ? req.getRejectReason() : "")))
                .map(itemMapper::toItemResponse);
    }
}
