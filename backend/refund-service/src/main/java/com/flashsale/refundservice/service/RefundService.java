package com.flashsale.refundservice.service;

import com.flashsale.commonlib.dto.PageResponse;
import com.flashsale.refundservice.dto.request.AdminRefundApproveRequest;
import com.flashsale.refundservice.dto.request.AdminRefundRejectRequest;
import com.flashsale.refundservice.dto.response.AdminRefundApproveResponse;
import com.flashsale.refundservice.dto.response.RefundDetailResponse;
import com.flashsale.refundservice.dto.response.RefundListResponse;
import com.flashsale.refundservice.refund.flow.FullRefundHandler;
import com.flashsale.refundservice.refund.flow.PartialRefundHandler;
import com.flashsale.refundservice.refund.flow.RtsRefundHandler;
import com.flashsale.refundservice.refund.flow.StripeAutoRefundHandler;
import com.flashsale.refundservice.reply.RefundReplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * RefundService acts as a facade delegating administration, queries, event flow
 * handling, and reply logic to specialized services.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {

    private final RefundQueryService refundQueryService;
    private final RefundAdminService refundAdminService;
    private final PartialRefundHandler partialRefundHandler;
    private final FullRefundHandler fullRefundHandler;
    private final RtsRefundHandler rtsRefundHandler;
    private final StripeAutoRefundHandler stripeAutoRefundHandler;
    private final RefundReplyService refundReplyService;

    // ─── Query ──────────────────────────────────────────────────────────────

    public PageResponse<RefundListResponse> listAllRefunds(
            String status, String type,
            String fromDate, String toDate,
            int page, int size) {
        return refundQueryService.listAllRefunds(status, type, fromDate, toDate, page, size);
    }

    public RefundDetailResponse getRefundById(Long refundId) {
        return refundQueryService.getRefundById(refundId);
    }

    // ─── Admin Operations ───────────────────────────────────────────────────

    public AdminRefundApproveResponse approveRefund(Long refundId, Long adminId, AdminRefundApproveRequest req) {
        return refundAdminService.approveRefund(refundId, adminId, req);
    }

    public void rejectRefund(Long refundId, Long adminId, AdminRefundRejectRequest req) {
        refundAdminService.rejectRefund(refundId, adminId, req);
    }

    // ─── Kafka Flows ────────────────────────────────────────────────────────

    public void onRefundRequested(String message) {
        partialRefundHandler.onRefundRequested(message);
    }

    public void onRefundFullRequested(String message) {
        fullRefundHandler.onRefundFullRequested(message);
    }

    public void onOrderReturnedRts(String message) {
        rtsRefundHandler.onOrderReturnedRts(message);
    }

    public void onRefundStripeAuto(String message) {
        stripeAutoRefundHandler.onRefundStripeAuto(message);
    }

    // ─── Kafka Request/Reply ────────────────────────────────────────────────

    public void onOrderRefundsRequest(String message) {
        refundReplyService.onOrderRefundsRequest(message);
    }

    public void onRefundPresignedUrlRequest(String message) {
        refundReplyService.onRefundPresignedUrlRequest(message);
    }

    public void onOrderPaymentStatusRequest(String message) {
        refundReplyService.onOrderPaymentStatusRequest(message);
    }
}
