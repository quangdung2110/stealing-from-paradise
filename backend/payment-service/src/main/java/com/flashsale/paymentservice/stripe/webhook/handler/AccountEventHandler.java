package com.flashsale.paymentservice.stripe.webhook.handler;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.event.payload.SellerStripeRequirementPayload;
import com.flashsale.paymentservice.config.StripeConfig;
import com.flashsale.paymentservice.domain.model.SellerStripeAccount;
import com.flashsale.paymentservice.domain.repository.SellerStripeAccountRepository;
import com.flashsale.paymentservice.stripe.webhook.StripeEventHandler;
import com.flashsale.paymentservice.support.KafkaPublisher;
import com.flashsale.paymentservice.support.StripeEvents;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.param.AccountLinkCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountEventHandler implements StripeEventHandler {

    private final SellerStripeAccountRepository sellerStripeAccountRepository;
    private final StripeConfig stripeConfig;
    private final KafkaPublisher kafkaPublisher;

    @Override
    @Transactional
    public void handle(Event event) {
        log.info("AccountEventHandler handling event type: {}", event.getType());
        switch (event.getType()) {
            case "account.updated" -> handleAccountUpdated(event);
            case "account.external_account.created",
                 "account.external_account.updated",
                 "account.external_account.deleted" -> handleExternalAccountChanged(event);
            default -> log.warn("Unhandled Account event type: {}", event.getType());
        }
    }

    private void handleAccountUpdated(Event event) {
        StripeObject stripeObject = StripeEvents.deserialize(event);
        if (!(stripeObject instanceof Account account)) return;

        sellerStripeAccountRepository.findByStripeAccountId(account.getId()).ifPresent(seller -> {
            seller.setDetailsSubmitted(Boolean.TRUE.equals(account.getDetailsSubmitted()));
            seller.setChargesEnabled(Boolean.TRUE.equals(account.getChargesEnabled()));
            seller.setPayoutsEnabled(Boolean.TRUE.equals(account.getPayoutsEnabled()));

            var requirements = account.getRequirements();
            String disabledReason = requirements != null ? requirements.getDisabledReason() : null;
            boolean hasDisabledReason = disabledReason != null && !disabledReason.isBlank();
            boolean wasSuspended = "SUSPENDED".equals(seller.getAccountStatus());

            if (hasDisabledReason) {
                seller.setAccountStatus("SUSPENDED");
                if (!wasSuspended) {
                    kafkaPublisher.publish(KafkaTopics.STRIPE_ACCOUNT_SUSPENDED, account.getId(), Map.of(
                            "seller_id",         seller.getSellerId(),
                            "stripe_account_id", account.getId(),
                            "disabled_reason",   disabledReason,
                            "charges_enabled",   Boolean.TRUE.equals(account.getChargesEnabled()),
                            "payouts_enabled",   Boolean.TRUE.equals(account.getPayoutsEnabled()),
                            "timestamp",         Instant.now().toString()
                    ));
                }
            } else if (Boolean.TRUE.equals(account.getDetailsSubmitted())
                    && Boolean.TRUE.equals(account.getChargesEnabled())) {
                seller.setAccountStatus("ACTIVE");
                seller.setOnboardingUrl(null);
                seller.setOnboardingUrlExpiresAt(null);
            }

            // Check requirements: if seller needs to complete additional Stripe requirements,
            // create a fresh Account Link and notify them via Kafka.
            if (requirements != null
                    && requirements.getCurrentlyDue() != null
                    && !requirements.getCurrentlyDue().isEmpty()) {
                try {
                    AccountLink accountLink = AccountLink.create(AccountLinkCreateParams.builder()
                            .setAccount(account.getId())
                            .setRefreshUrl(stripeConfig.getOnboardingRefreshUrl())
                            .setReturnUrl(stripeConfig.getOnboardingReturnUrl())
                            .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                            .setCollectionOptions(
                                AccountLinkCreateParams.CollectionOptions.builder()
                                    .setFields(AccountLinkCreateParams.CollectionOptions.Fields.EVENTUALLY_DUE)
                                    .setFutureRequirements(AccountLinkCreateParams.CollectionOptions.FutureRequirements.INCLUDE)
                                    .build()
                            )
                            .build());

                    Instant expiresAt = Instant.now().plusSeconds(86400);
                    seller.setOnboardingUrl(accountLink.getUrl());
                    seller.setOnboardingUrlExpiresAt(LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC));

                    // Publish to notification service so seller receives the link
                    SellerStripeRequirementPayload notification = SellerStripeRequirementPayload.builder()
                            .sellerId(seller.getSellerId())
                            .stripeAccountId(account.getId())
                            .requirementType("verification_needed")
                            .requirementReason(String.join(", ", requirements.getCurrentlyDue()))
                            .accountLinkUrl(accountLink.getUrl())
                            .accountLinkExpiresAt(expiresAt.toEpochMilli())
                            .build();
                    kafkaPublisher.publish(KafkaTopics.SELLER_STRIPE_REQUIREMENT, String.valueOf(seller.getSellerId()), notification);

                    log.info("Stripe requirements detected for seller {}: {}", seller.getSellerId(), requirements.getCurrentlyDue());
                } catch (StripeException e) {
                    log.error("Failed to create AccountLink for seller {} requirements: {}", seller.getSellerId(), e.getMessage());
                }
            }

            // Sync Express Dashboard URL (view-as link for admin)
            String platformAcct = stripeConfig.getPlatformAccountId();
            if (platformAcct != null) {
                seller.setExpressDashboardUrl(
                        String.format("https://dashboard.stripe.com/%s/connect/view-as/%s/test/dashboard",
                                platformAcct, account.getId()));
            } else {
                seller.setExpressDashboardUrl("https://connect.stripe.com/express/" + account.getId());
            }
            sellerStripeAccountRepository.save(seller);
            log.info("Seller Stripe account synced: sellerId={}, status={}", seller.getSellerId(), seller.getAccountStatus());
        });
    }

    private void handleExternalAccountChanged(Event event) {
        StripeObject stripeObject = StripeEvents.deserialize(event);
        if (stripeObject == null) return;

        String accountId = null;
        String bankAccountId = null;

        if (stripeObject instanceof BankAccount bankAccount) {
            accountId    = bankAccount.getAccount();
            bankAccountId = bankAccount.getId();
        } else if (stripeObject instanceof Card card) {
            accountId    = card.getAccount();
            bankAccountId = card.getId();
        }

        if (accountId == null) return;

        final String finalAccountId    = accountId;
        final String finalBankAccountId = bankAccountId;
        sellerStripeAccountRepository.findByStripeAccountId(finalAccountId).ifPresent(seller ->
            log.info("External bank account changed [{}]: sellerId={}, stripeAccountId={}, bankAccountId={}",
                    event.getType(), seller.getSellerId(), finalAccountId, finalBankAccountId)
        );
    }
}
