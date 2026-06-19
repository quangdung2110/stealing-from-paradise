package com.flashsale.paymentservice.stripe.webhook.handler;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.paymentservice.domain.repository.SellerTransferRepository;
import com.flashsale.paymentservice.stripe.webhook.StripeEventHandler;
import com.flashsale.paymentservice.support.KafkaPublisher;
import com.flashsale.paymentservice.support.StripeEvents;
import com.flashsale.paymentservice.support.StripeMetadata;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.Transfer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransferEventHandler implements StripeEventHandler {

    private final SellerTransferRepository sellerTransferRepository;
    private final KafkaPublisher kafkaPublisher;

    @Override
    @Transactional
    public void handle(Event event) {
        log.info("TransferEventHandler handling event type: {}", event.getType());
        switch (event.getType()) {
            case "transfer.created" -> handleTransferCreated(event);
            case "transfer.updated" -> handleTransferUpdated(event);
            case "transfer.reversed" -> handleTransferReversed(event);
            default -> log.warn("Unhandled Transfer event type: {}", event.getType());
        }
    }

    private void handleTransferCreated(Event event) {
        StripeObject stripeObject = StripeEvents.deserialize(event);
        if (!(stripeObject instanceof Transfer transfer)) return;

        Long orderId = StripeMetadata.extractOrderId(transfer.getMetadata());
        if (orderId == null) return;

        sellerTransferRepository.findByOrderId(orderId).ifPresent(st -> {
            st.setStripeTransferId(transfer.getId());
            st.setStatus("SUCCEEDED");
            sellerTransferRepository.save(st);
            log.info("Seller transfer recorded: orderId={}, transferId={}", orderId, transfer.getId());
        });
    }

    private void handleTransferUpdated(Event event) {
        StripeObject stripeObject = StripeEvents.deserialize(event);
        if (!(stripeObject instanceof Transfer transfer)) return;

        Long orderId = StripeMetadata.extractOrderId(transfer.getMetadata());
        if (orderId == null) return;

        sellerTransferRepository.findByOrderId(orderId).ifPresent(st -> {
            if (st.getStripeTransferId() == null) {
                st.setStripeTransferId(transfer.getId());
                sellerTransferRepository.save(st);
                log.info("SellerTransfer stripe_transfer_id backfilled: orderId={}, transferId={}", orderId, transfer.getId());
            }
        });
    }

    private void handleTransferReversed(Event event) {
        StripeObject stripeObject = StripeEvents.deserialize(event);
        if (!(stripeObject instanceof Transfer transfer)) return;

        Long orderId = StripeMetadata.extractOrderId(transfer.getMetadata());
        if (orderId == null) {
            log.warn("transfer.reversed without order_id metadata: transferId={}", transfer.getId());
            return;
        }

        sellerTransferRepository.findByOrderId(orderId).ifPresent(st -> {
            st.setStatus("REVERSED");
            sellerTransferRepository.save(st);
            kafkaPublisher.publish(KafkaTopics.STRIPE_TRANSFER_REVERSED, String.valueOf(orderId), Map.of(
                    "order_id",       orderId,
                    "seller_id",      st.getSellerId(),
                    "transfer_id",    transfer.getId(),
                    "amount_reversed", transfer.getAmountReversed(),
                    "timestamp",      Instant.now().toString()
            ));
            log.warn("Transfer REVERSED: orderId={}, sellerId={}, transferId={}, amountReversed={}",
                    orderId, st.getSellerId(), transfer.getId(), transfer.getAmountReversed());
        });
    }
}
