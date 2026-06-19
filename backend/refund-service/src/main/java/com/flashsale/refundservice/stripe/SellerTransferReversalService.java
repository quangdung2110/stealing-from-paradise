package com.flashsale.refundservice.stripe;

import com.flashsale.refundservice.domain.model.SellerTransfer;
import com.flashsale.refundservice.domain.repository.SellerTransferRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Transfer;
import com.stripe.model.TransferReversal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SellerTransferReversalService {

    private final SellerTransferRepository sellerTransferRepository;

    /**
     * Reverse phần transfer đã gửi cho Seller tương ứng với khoản hoàn tiền.
     */
    @Transactional
    public String reverseSellerTransfer(Long orderId, BigDecimal refundAmount, Long refundId) {
        SellerTransfer st = sellerTransferRepository.findByOrderId(orderId).orElse(null);

        if (st == null) {
            log.warn("reverseSellerTransfer: no SellerTransfer for orderId={}", orderId);
            return null;
        }

        String transferStatus = st.getStatus();
        if ("AWAITING_DELIVERY".equals(transferStatus)
                || "RETURN_WINDOW".equals(transferStatus)
                || "READY_FOR_PAYOUT".equals(transferStatus)) {
            log.info("reverseSellerTransfer: transfer not yet paid (status={}) for orderId={}, marking REFUNDED", transferStatus, orderId);
            st.setStatus("REFUNDED");
            sellerTransferRepository.save(st);
            return null;
        }

        if (st.getStripeTransferId() == null) {
            log.info("reverseSellerTransfer: no stripeTransferId for orderId={} (status={}), skipping", orderId, st.getStatus());
            return null;
        }

        if ("REVERSED".equals(st.getStatus())) {
            log.info("reverseSellerTransfer: transfer already REVERSED for orderId={}", orderId);
            return null;
        }

        BigDecimal transferAmount = st.getTransferAmount() != null ? st.getTransferAmount() : BigDecimal.ZERO;
        BigDecimal netAmount      = transferAmount;

        if (netAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("reverseSellerTransfer: netAmount is zero for orderId={}, skipping", orderId);
            return null;
        }

        boolean fullReversal = transferAmount.compareTo(BigDecimal.ZERO) <= 0
                || refundAmount.compareTo(transferAmount) >= 0;

        long reversalAmount;
        if (fullReversal) {
            reversalAmount = netAmount.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        } else {
            reversalAmount = refundAmount
                    .multiply(netAmount)
                    .divide(transferAmount, 0, java.math.RoundingMode.HALF_UP)
                    .min(netAmount)
                    .longValue();
        }

        if (reversalAmount <= 0) return null;

        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("refund_id", String.valueOf(refundId));
            metadata.put("order_id",  String.valueOf(orderId));

            Map<String, Object> params = new HashMap<>();
            params.put("amount",   reversalAmount);
            params.put("metadata", metadata);

            Transfer transfer = Transfer.retrieve(st.getStripeTransferId());
            TransferReversal reversal = transfer.getReversals().create(params);

            st.setStatus(fullReversal ? "REVERSED" : "PARTIALLY_REVERSED");
            sellerTransferRepository.save(st);

            log.info("Seller transfer reversed: orderId={}, transferId={}, reversalId={}, amount={}, full={}",
                    orderId, st.getStripeTransferId(), reversal.getId(), reversalAmount, fullReversal);
            return reversal.getId();

        } catch (StripeException e) {
            log.error("Failed to reverse seller transfer for orderId={}, transferId={}: {}",
                    orderId, st.getStripeTransferId(), e.getMessage());
            return null;
        }
    }
}
