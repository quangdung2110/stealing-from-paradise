package com.flashsale.refundservice.service;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.refundservice.domain.model.Refund;
import com.flashsale.refundservice.domain.model.RefundItem;
import com.flashsale.refundservice.domain.repository.RefundItemRepository;
import com.flashsale.refundservice.domain.repository.RefundRepository;
import com.flashsale.refundservice.dto.request.AdminRefundApproveRequest;
import com.flashsale.refundservice.dto.request.AdminRefundRejectRequest;
import com.flashsale.refundservice.dto.response.AdminRefundApproveResponse;
import com.flashsale.refundservice.stripe.SellerTransferReversalService;
import com.flashsale.refundservice.stripe.StripeRefundClient;
import com.flashsale.refundservice.support.KafkaPublisher;
import com.flashsale.refundservice.support.RefundCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundAdminService {

    private final RefundRepository refundRepository;
    private final RefundItemRepository refundItemRepository;
    private final StripeRefundClient stripeRefundClient;
    private final SellerTransferReversalService sellerTransferReversalService;
    private final KafkaPublisher kafkaPublisher;

    @Transactional
    public AdminRefundApproveResponse approveRefund(Long refundId, Long adminId, AdminRefundApproveRequest req) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Refund không tồn tại: " + refundId));

        if (!"PENDING".equals(refund.getStatus()) && !"FAILED".equals(refund.getStatus())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Chỉ có thể approve refund ở trạng thái PENDING hoặc FAILED");
        }

        BigDecimal finalAmount = refund.getAmount();
        if ("PARTIAL".equals(refund.getType())) {
            List<RefundItem> items = refundItemRepository.findAllByRefundId(refundId);
            BigDecimal itemsTotal = items.stream()
                    .map(RefundItem::getRefundAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (itemsTotal.compareTo(BigDecimal.ZERO) > 0) {
                finalAmount = itemsTotal;
                refund.setAmount(finalAmount);
            }
        }
        String stripeRefundId = stripeRefundClient.executeStripeRefund(refund.getTransactionId(), finalAmount);

        // Reverse phần transfer tới Seller tương ứng với khoản hoàn tiền
        sellerTransferReversalService.reverseSellerTransfer(refund.getOrderId(), finalAmount, refundId);

        List<AdminRefundApproveResponse.ReturnEvidence> returnEvidence = new ArrayList<>();
        if (req.getTrackingNumber() != null) {
            List<RefundItem> items = refundItemRepository.findAllByRefundId(refundId);
            Instant now = Instant.now();
            for (RefundItem item : items) {
                item.setReturnTrackingNumber(req.getTrackingNumber());
                item.setReturnedAt(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
            }
            refundItemRepository.saveAll(items);
            returnEvidence.add(AdminRefundApproveResponse.ReturnEvidence.builder()
                    .type("tracking")
                    .trackingNumber(req.getTrackingNumber())
                    .recordedAt(now)
                    .build());
        }

        refund.setStatus("SUCCESS");
        refund.setAdminNote(req.getAdminNote());
        refund.setReviewedBy(adminId);
        refund.setReviewedAt(LocalDateTime.now());
        refund.setRefundRef(stripeRefundId);
        refundRepository.save(refund);

        kafkaPublisher.publish(KafkaTopics.REFUND_ADMIN_APPROVED, String.valueOf(refundId), Map.of(
                "refund_id",       refundId,
                "order_id",        refund.getOrderId(),
                "amount",          finalAmount,
                "type",            refund.getType(),
                "admin_id",        adminId,
                "caused_by",       req.getCausedBy() != null ? req.getCausedBy() : "",
                "tracking_number", req.getTrackingNumber() != null ? req.getTrackingNumber() : "",
                "timestamp",       Instant.now().toString()
        ));

        log.info("Refund approved: refundId={}, adminId={}, amount={}, stripeRefundId={}",
                refundId, adminId, finalAmount, stripeRefundId);

        return AdminRefundApproveResponse.builder()
                .refundId(refund.getId())
                .refundCode(RefundCodes.buildRefundCode(refund))
                .status("SUCCESS")
                .type(refund.getType())
                .amount(finalAmount)
                .trackingNumber(req.getTrackingNumber())
                .returnEvidence(returnEvidence.isEmpty() ? null : returnEvidence)
                .reviewedBy(adminId)
                .adminNote(req.getAdminNote())
                .reviewedAt(refund.getReviewedAt().toInstant(ZoneOffset.UTC))
                .stripeRefundId(stripeRefundId)
                .build();
    }

    @Transactional
    public void rejectRefund(Long refundId, Long adminId, AdminRefundRejectRequest req) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Refund không tồn tại: " + refundId));

        if (!"PENDING".equals(refund.getStatus())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Chỉ có thể reject refund ở trạng thái PENDING");
        }

        refund.setStatus("REJECTED");
        refund.setRejectReason(req.getRejectReason());
        refund.setReviewedBy(adminId);
        refund.setReviewedAt(LocalDateTime.now());
        refundRepository.save(refund);

        kafkaPublisher.publish(KafkaTopics.REFUND_REJECTED, String.valueOf(refundId), Map.of(
                "refund_id",      refundId,
                "order_id",       refund.getOrderId(),
                "user_id",        0L,
                "reject_reason",  req.getRejectReason(),
                "fraud_evidence", Boolean.TRUE.equals(req.getFraudEvidence()),
                "admin_id",       adminId,
                "timestamp",      Instant.now().toString()
        ));

        log.info("Refund rejected: refundId={}, adminId={}", refundId, adminId);
    }
}
