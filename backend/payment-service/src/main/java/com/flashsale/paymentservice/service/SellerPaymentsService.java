package com.flashsale.paymentservice.service;

import com.flashsale.commonlib.dto.PageResponse;
import com.flashsale.paymentservice.config.StripeConfig;
import com.flashsale.paymentservice.domain.model.SellerStripeAccount;
import com.flashsale.paymentservice.domain.model.SellerTransfer;
import com.flashsale.paymentservice.domain.repository.SellerStripeAccountRepository;
import com.flashsale.paymentservice.domain.repository.SellerTransferRepository;
import com.flashsale.paymentservice.dto.response.SellerBalanceResponse;
import com.flashsale.paymentservice.dto.response.SellerEarningsResponse;
import com.flashsale.paymentservice.dto.response.SellerStripeDashboardResponse;
import com.flashsale.paymentservice.dto.response.SellerEarningsResponse.SellerTransferItem;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.LoginLink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SellerPaymentsService {

    private final SellerTransferRepository sellerTransferRepository;
    private final SellerStripeAccountRepository sellerStripeAccountRepository;
    private final StripeConfig stripeConfig;

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final Set<String> PENDING_BALANCE_STATUSES = Set.of(
            "PENDING", "AWAITING_DELIVERY", "RETURN_WINDOW", "READY_FOR_PAYOUT"
    );
    private static final Set<String> EXCLUDED_EARNED_STATUSES = Set.of(
            "REFUNDED", "CANCELLED", "FAILED", "SKIPPED"
    );

    @Transactional(readOnly = true)
    public SellerEarningsResponse getSellerEarnings(Long sellerId) {
        List<SellerTransfer> transfers = sellerTransferRepository.findAllBySellerIdOrderByCreatedAtDesc(sellerId);

        BigDecimal totalEarnings = BigDecimal.ZERO;
        BigDecimal availableBalance = BigDecimal.ZERO;
        BigDecimal pendingBalance = BigDecimal.ZERO;

        for (SellerTransfer t : transfers) {
            BigDecimal net = netAmount(t);

            if (!EXCLUDED_EARNED_STATUSES.contains(statusOf(t))) {
                totalEarnings = totalEarnings.add(net);
            }
            if ("PAID_OUT".equals(statusOf(t))) {
                availableBalance = availableBalance.add(net);
            } else if (PENDING_BALANCE_STATUSES.contains(statusOf(t))) {
                pendingBalance = pendingBalance.add(net);
            }
        }

        List<SellerTransferItem> items = transfers.stream()
                .map(this::toTransferItem)
                .collect(Collectors.toList());

        return SellerEarningsResponse.builder()
                .totalEarnings(totalEarnings)
                .availableBalance(availableBalance)
                .pendingBalance(pendingBalance)
                .platformFeePercentage(BigDecimal.valueOf(stripeConfig.getPlatformFeePercentage()))
                .totalOrders((long) transfers.size())
                .transfers(items)
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<SellerTransferItem> getSellerTransfers(
            Long sellerId,
            String status,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Page<SellerTransferItem> result = sellerTransferRepository
                .findAllBySellerIdWithFilters(sellerId, normalizeStatus(status), fromDate, toDate, pageable)
                .map(this::toTransferItem);
        return PageResponse.of(result);
    }

    @Transactional(readOnly = true)
    public SellerBalanceResponse getSellerBalance(Long sellerId) {
        BigDecimal pendingBalance = BigDecimal.ZERO;
        BigDecimal availableBalance = BigDecimal.ZERO;
        BigDecimal totalEarned = BigDecimal.ZERO;

        for (SellerTransfer transfer : sellerTransferRepository.findAllBySellerIdOrderByCreatedAtDesc(sellerId)) {
            BigDecimal net = netAmount(transfer);
            String status = statusOf(transfer);

            if (!EXCLUDED_EARNED_STATUSES.contains(status)) {
                totalEarned = totalEarned.add(net);
            }
            if ("PAID_OUT".equals(status)) {
                availableBalance = availableBalance.add(net);
            } else if (PENDING_BALANCE_STATUSES.contains(status)) {
                pendingBalance = pendingBalance.add(net);
            }
        }

        return SellerBalanceResponse.builder()
                .sellerId(sellerId)
                .pendingBalance(pendingBalance)
                .availableBalance(availableBalance)
                .totalEarned(totalEarned)
                .build();
    }

    @Transactional(readOnly = true)
    public SellerStripeDashboardResponse getStripeDashboardUrl(Long sellerId) {
        SellerStripeAccount account = sellerStripeAccountRepository.findBySellerId(sellerId)
                .orElseThrow(() -> new com.flashsale.commonlib.exception.AppException(
                        com.flashsale.commonlib.exception.ErrorCode.NOT_FOUND,
                        "Seller chưa kết nối Stripe"));

        if (!Boolean.TRUE.equals(account.getDetailsSubmitted())) {
            throw new com.flashsale.commonlib.exception.AppException(
                    com.flashsale.commonlib.exception.ErrorCode.VALIDATION_FAILED,
                    "Seller chưa hoàn tất onboarding Stripe");
        }

        String dashboardUrl;
        try {
            Account stripeAccount = Account.retrieve(account.getStripeAccountId());
            LoginLink loginLink = LoginLink.createOnAccount(account.getStripeAccountId());
            dashboardUrl = loginLink.getUrl();
        } catch (StripeException e) {
            log.error("Failed to create Stripe dashboard login link for seller {}: {}", sellerId, e.getMessage());
            dashboardUrl = "https://dashboard.stripe.com";
        }

        return SellerStripeDashboardResponse.builder()
                .dashboardUrl(dashboardUrl)
                .stripeAccountId(account.getStripeAccountId())
                .accountStatus(account.getAccountStatus())
                .build();
    }

    private SellerTransferItem toTransferItem(SellerTransfer t) {
        return SellerTransferItem.builder()
                .id(t.getId())
                .orderId(t.getOrderId())
                .transferAmount(t.getTransferAmount())
                .stripeTransferId(t.getStripeTransferId())
                .status(t.getStatus())
                .createdAt(t.getCreatedAt() != null ? t.getCreatedAt().atOffset(ZoneOffset.UTC).format(ISO_FMT) : null)
                .updatedAt(t.getUpdatedAt() != null ? t.getUpdatedAt().atOffset(ZoneOffset.UTC).format(ISO_FMT) : null)
                .build();
    }

    private BigDecimal netAmount(SellerTransfer transfer) {
        BigDecimal gross = transfer.getTransferAmount() != null ? transfer.getTransferAmount() : BigDecimal.ZERO;
        BigDecimal commission = transfer.getPlatformCommissionAmount();
        if (commission == null) {
            commission = gross
                    .multiply(BigDecimal.valueOf(stripeConfig.getPlatformFeePercentage() / 100.0))
                    .setScale(0, RoundingMode.HALF_UP);
        }
        return gross.subtract(commission);
    }

    private String statusOf(SellerTransfer transfer) {
        return transfer.getStatus() != null ? transfer.getStatus().toUpperCase() : "";
    }

    private String normalizeStatus(String status) {
        return status == null || status.isBlank() ? null : status.trim().toUpperCase();
    }
}
