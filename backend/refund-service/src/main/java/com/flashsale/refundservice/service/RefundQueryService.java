package com.flashsale.refundservice.service;

import com.flashsale.commonlib.dto.PageResponse;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.refundservice.domain.model.Refund;
import com.flashsale.refundservice.domain.model.RefundItem;
import com.flashsale.refundservice.domain.repository.RefundItemRepository;
import com.flashsale.refundservice.domain.repository.RefundRepository;
import com.flashsale.refundservice.dto.response.RefundDetailResponse;
import com.flashsale.refundservice.dto.response.RefundListResponse;
import com.flashsale.refundservice.support.RefundCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundQueryService {

    private final RefundRepository refundRepository;
    private final RefundItemRepository refundItemRepository;

    @Transactional(readOnly = true)
    public PageResponse<RefundListResponse> listAllRefunds(
            String status, String type,
            String fromDate, String toDate,
            int page, int size) {

        LocalDateTime from = fromDate != null ? LocalDateTime.parse(fromDate + "T00:00:00") : null;
        LocalDateTime to   = toDate   != null ? LocalDateTime.parse(toDate   + "T23:59:59") : null;

        Page<Refund> result = refundRepository.findAllWithFilters(
                status, type, from, to,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        return PageResponse.<RefundListResponse>builder()
                .content(result.getContent().stream().map(this::toListResponse).collect(Collectors.toList()))
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public RefundDetailResponse getRefundById(Long refundId) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Refund không tồn tại: " + refundId));

        List<RefundItem> items = refundItemRepository.findAllByRefundId(refundId);
        List<String> evidenceImages = refund.getEvidenceImages();

        List<RefundDetailResponse.ReturnEvidence> returnEvidence = items.stream()
                .filter(ri -> ri.getReturnTrackingNumber() != null)
                .map(ri -> RefundDetailResponse.ReturnEvidence.builder()
                        .type("tracking")
                        .trackingNumber(ri.getReturnTrackingNumber())
                        .recordedAt(ri.getReturnedAt() != null
                                ? ri.getReturnedAt().toInstant(ZoneOffset.UTC) : null)
                        .build())
                .collect(Collectors.toList());

        String trackingNumber = items.stream()
                .map(RefundItem::getReturnTrackingNumber)
                .filter(t -> t != null && !t.isBlank())
                .findFirst().orElse(null);

        List<RefundDetailResponse.RefundItemInfo> itemInfos = items.stream()
                .map(ri -> RefundDetailResponse.RefundItemInfo.builder()
                        .itemId(ri.getItemId())
                        .productName(ri.getProductName())
                        .imageSnapshot(ri.getImageSnapshot())
                        .quantity(ri.getQuantity())
                        .refundAmount(ri.getRefundAmount())
                        .itemReason(ri.getItemReason())
                        .status(ri.getStatus())
                        .returnTrackingNumber(ri.getReturnTrackingNumber())
                        .returnedAt(ri.getReturnedAt() != null
                                ? ri.getReturnedAt().toInstant(ZoneOffset.UTC) : null)
                        .build())
                .collect(Collectors.toList());

        return RefundDetailResponse.builder()
                .refundId(refund.getId())
                .refundCode(RefundCodes.buildRefundCode(refund))
                .orderId(refund.getOrderId())
                .groupRef(refund.getGroupRef() != null ? refund.getGroupRef().toString() : null)
                .type(refund.getType())
                .status(refund.getStatus())
                .amount(refund.getAmount())
                .reason(refund.getReason())
                .initiatedBy(refund.getInitiatedBy())
                .refundReasonType(refund.getRefundReasonType())
                .evidenceImages(evidenceImages)
                .adminNote(refund.getAdminNote())
                .rejectReason(refund.getRejectReason())
                .trackingNumber(trackingNumber)
                .returnEvidence(returnEvidence.isEmpty() ? null : returnEvidence)
                .reviewedBy(refund.getReviewedBy())
                .reviewedAt(refund.getReviewedAt() != null
                        ? refund.getReviewedAt().toInstant(ZoneOffset.UTC) : null)
                .stripeRefundId(refund.getRefundRef())
                .items(itemInfos)
                .createdAt(refund.getCreatedAt().toInstant(ZoneOffset.UTC))
                .updatedAt(refund.getUpdatedAt().toInstant(ZoneOffset.UTC))
                .build();
    }

    private RefundListResponse toListResponse(Refund r) {
        return RefundListResponse.builder()
                .refundId(r.getId())
                .refundCode(RefundCodes.buildRefundCode(r))
                .orderId(r.getOrderId())
                .groupRef(r.getGroupRef() != null ? r.getGroupRef().toString() : null)
                .type(r.getType())
                .status(r.getStatus())
                .amount(r.getAmount())
                .initiatedBy(r.getInitiatedBy())
                .refundReasonType(r.getRefundReasonType())
                .adminNote(r.getAdminNote())
                .rejectReason(r.getRejectReason())
                .reviewedBy(r.getReviewedBy())
                .reviewedAt(r.getReviewedAt() != null ? r.getReviewedAt().toInstant(ZoneOffset.UTC) : null)
                .refundRef(r.getRefundRef())
                .createdAt(r.getCreatedAt().toInstant(ZoneOffset.UTC))
                .build();
    }
}
