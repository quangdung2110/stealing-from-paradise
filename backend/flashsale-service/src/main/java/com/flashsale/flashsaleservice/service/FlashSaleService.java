package com.flashsale.flashsaleservice.service;

import com.flashsale.flashsaleservice.dto.request.CreateFlashSaleItemRequest;
import com.flashsale.flashsaleservice.dto.request.CreateSessionRequest;
import com.flashsale.flashsaleservice.dto.request.ApproveItemRequest;
import com.flashsale.flashsaleservice.dto.request.RejectItemRequest;
import com.flashsale.flashsaleservice.dto.request.UpdateSessionRequest;
import com.flashsale.flashsaleservice.dto.response.FlashSaleItemResponse;
import com.flashsale.flashsaleservice.dto.response.SessionDetailResponse;
import com.flashsale.flashsaleservice.dto.response.SessionListResponse;
import com.flashsale.flashsaleservice.dto.response.SessionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class FlashSaleService {

    private final FlashSaleSessionService sessionService;
    private final FlashSaleItemService itemService;

    public Mono<SessionListResponse> getSessions(String status) {
        return sessionService.getSessions(status);
    }

    public Mono<SessionDetailResponse> getSessionDetail(Long sessionId) {
        return sessionService.getSessionDetail(sessionId);
    }

    public Mono<SessionListResponse> getActiveSessions() {
        return sessionService.getActiveSessions();
    }

    public Mono<SessionResponse> createSession(CreateSessionRequest req) {
        return sessionService.createSession(req);
    }

    public Mono<SessionResponse> updateSession(Long sessionId, UpdateSessionRequest req) {
        return sessionService.updateSession(sessionId, req);
    }

    public Mono<Void> deleteSession(Long sessionId) {
        return sessionService.deleteSession(sessionId);
    }

    public Mono<FlashSaleItemResponse> createFlashSaleItem(Long sessionId, Long sellerId, CreateFlashSaleItemRequest req) {
        return itemService.createFlashSaleItem(sessionId, sellerId, req);
    }

    public Mono<FlashSaleItemResponse> approveItem(Long sessionId, Long itemId, ApproveItemRequest req) {
        return itemService.approveItem(sessionId, itemId, req);
    }

    public Mono<FlashSaleItemResponse> rejectItem(Long sessionId, Long itemId, RejectItemRequest req) {
        return itemService.rejectItem(sessionId, itemId, req);
    }
}
