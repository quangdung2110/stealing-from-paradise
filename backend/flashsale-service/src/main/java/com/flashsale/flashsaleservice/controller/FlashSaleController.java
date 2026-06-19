package com.flashsale.flashsaleservice.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.flashsaleservice.dto.request.*;
import com.flashsale.flashsaleservice.dto.response.FlashSaleItemResponse;
import com.flashsale.flashsaleservice.dto.response.SessionDetailResponse;
import com.flashsale.flashsaleservice.dto.response.SessionListResponse;
import com.flashsale.flashsaleservice.dto.response.SessionResponse;
import com.flashsale.flashsaleservice.service.FlashSaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/v1/flash-sales")
@RequiredArgsConstructor
public class FlashSaleController {

    private final FlashSaleService flashSaleService;

    @GetMapping
    public Mono<ApiResponse<List<SessionResponse>>> getSessions(
            @RequestParam(required = false) String status) {
        return flashSaleService.getSessions(status)
                .map(resp -> ApiResponse.success(resp.getSessions()));
    }

    @GetMapping("/active")
    public Mono<ApiResponse<List<SessionResponse>>> getActiveSessions() {
        return flashSaleService.getActiveSessions()
                .map(resp -> ApiResponse.success(resp.getSessions()));
    }

    @GetMapping("/{sessionId}")
    public Mono<ApiResponse<SessionDetailResponse>> getSessionDetail(
            @PathVariable Long sessionId) {
        return flashSaleService.getSessionDetail(sessionId)
                .map(ApiResponse::success);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApiResponse<SessionResponse>> createSession(
            @Valid @RequestBody CreateSessionRequest request) {
        return flashSaleService.createSession(request)
                .map(ApiResponse::success);
    }

    @PutMapping("/{sessionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApiResponse<SessionResponse>> updateSession(
            @PathVariable Long sessionId,
            @Valid @RequestBody UpdateSessionRequest request) {
        return flashSaleService.updateSession(sessionId, request)
                .map(ApiResponse::success);
    }

    @DeleteMapping("/{sessionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApiResponse<Void>> deleteSession(
            @PathVariable Long sessionId) {
        return flashSaleService.deleteSession(sessionId)
                .then(Mono.just(ApiResponse.success(null, "Session deleted")));
    }

    @PostMapping("/{sessionId}/items")
    @PreAuthorize("hasRole('SELLER')")
    public Mono<ApiResponse<FlashSaleItemResponse>> createFlashSaleItem(
            @PathVariable Long sessionId,
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.flashsale.commonlib.security.UserDetailsImpl user,
            @Valid @RequestBody CreateFlashSaleItemRequest request) {
        return flashSaleService.createFlashSaleItem(sessionId, user.getId(), request)
                .map(ApiResponse::success);
    }

    @PostMapping("/{sessionId}/items/{itemId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApiResponse<FlashSaleItemResponse>> approveItem(
            @PathVariable Long sessionId,
            @PathVariable Long itemId,
            @Valid @RequestBody ApproveItemRequest request) {
        return flashSaleService.approveItem(sessionId, itemId, request)
                .map(ApiResponse::success);
    }

    @PostMapping("/{sessionId}/items/{itemId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApiResponse<FlashSaleItemResponse>> rejectItem(
            @PathVariable Long sessionId,
            @PathVariable Long itemId,
            @Valid @RequestBody RejectItemRequest request) {
        return flashSaleService.rejectItem(sessionId, itemId, request)
                .map(ApiResponse::success);
    }
}
