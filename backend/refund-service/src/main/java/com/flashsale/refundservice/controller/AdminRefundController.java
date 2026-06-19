package com.flashsale.refundservice.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.dto.PageResponse;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.refundservice.dto.request.AdminRefundApproveRequest;
import com.flashsale.refundservice.dto.request.AdminRefundRejectRequest;
import com.flashsale.refundservice.dto.response.AdminRefundApproveResponse;
import com.flashsale.refundservice.dto.response.RefundDetailResponse;
import com.flashsale.refundservice.dto.response.RefundListResponse;
import com.flashsale.refundservice.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/admin/refunds")
@RequiredArgsConstructor
@Slf4j
public class AdminRefundController {

    private final RefundService refundService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<RefundListResponse>>> listRefunds(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(name = "from_date", required = false) String fromDate,
            @RequestParam(name = "to_date", required = false) String toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Admin list refunds: status={}, type={}, page={}", status, type, page);
        PageResponse<RefundListResponse> result = refundService.listAllRefunds(status, type, fromDate, toDate, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{refundId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RefundDetailResponse>> getRefund(
            @PathVariable Long refundId) {

        log.info("Admin get refund detail: refundId={}", refundId);
        RefundDetailResponse response = refundService.getRefundById(refundId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{refundId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminRefundApproveResponse>> approveRefund(
            @PathVariable Long refundId,
            @Valid @RequestBody AdminRefundApproveRequest request,
            @AuthenticationPrincipal UserDetailsImpl admin) {

        log.info("Admin {} approving refund {}", admin.getId(), refundId);
        AdminRefundApproveResponse response = refundService.approveRefund(refundId, admin.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(response, "Refund approved successfully"));
    }

    @PostMapping("/{refundId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> rejectRefund(
            @PathVariable Long refundId,
            @Valid @RequestBody AdminRefundRejectRequest request,
            @AuthenticationPrincipal UserDetailsImpl admin) {

        log.info("Admin {} rejecting refund {}", admin.getId(), refundId);
        refundService.rejectRefund(refundId, admin.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(null, "Refund rejected"));
    }
}
