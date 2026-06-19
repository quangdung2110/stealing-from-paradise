package com.flashsale.paymentservice.service;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.event.payload.SellerStripeRequirementPayload;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.paymentservice.config.StripeConfig;
import com.flashsale.paymentservice.domain.model.SellerStripeAccount;
import com.flashsale.paymentservice.domain.repository.SellerStripeAccountRepository;
import com.flashsale.paymentservice.dto.response.StripeOnboardingResponse;
import com.flashsale.paymentservice.dto.response.StripeOnboardingStatusResponse;
import com.flashsale.paymentservice.dto.response.AdminSellerStripeAccountsResponse;
import com.flashsale.paymentservice.dto.response.AdminSellerStripeAccountItem;
import com.flashsale.paymentservice.dto.response.AdminSellerStripeSummary;
import com.flashsale.paymentservice.support.KafkaPublisher;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;

import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeOnboardingService {

    private final SellerStripeAccountRepository sellerStripeAccountRepository;
    private final StripeConfig stripeConfig;
    private final KafkaPublisher kafkaPublisher;

    private static final String DASHBOARD_URL_FMT =
            "https://dashboard.stripe.com/%s/connect/view-as/%s/test/dashboard";

    // ── Admin ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminSellerStripeAccountsResponse getAllSellersOnboardingStatus() {
        List<SellerStripeAccount> accounts = sellerStripeAccountRepository.findAll();

        long total = accounts.size();
        long complete = 0, pending = 0, inProgress = 0, suspended = 0;

        List<AdminSellerStripeAccountItem> items = new ArrayList<>();
        for (SellerStripeAccount acc : accounts) {
            String status = acc.getOnboardingStatus();
            switch (status) {
                case "COMPLETE"  -> complete++;
                case "IN_PROGRESS" -> inProgress++;
                case "SUSPENDED"   -> suspended++;
                default            -> pending++;
            }

            items.add(AdminSellerStripeAccountItem.builder()
                    .sellerId(acc.getSellerId())
                    .stripeAccountId(acc.getStripeAccountId())
                    .accountStatus(acc.getAccountStatus())
                    .detailsSubmitted(acc.getDetailsSubmitted())
                    .chargesEnabled(acc.getChargesEnabled())
                    .payoutsEnabled(acc.getPayoutsEnabled())
                    .onboardingStatus(status)
                    .expressDashboardUrl(expressDashboardUrl(acc))
                    .createdAt(acc.getCreatedAt())
                    .updatedAt(acc.getUpdatedAt())
                    .build());
        }

        AdminSellerStripeSummary summary = AdminSellerStripeSummary.builder()
                .totalSellers(total)
                .completedSellers(complete)
                .pendingSellers(pending)
                .inProgressSellers(inProgress)
                .suspendedSellers(suspended)
                .build();

        return AdminSellerStripeAccountsResponse.builder()
                .summary(summary)
                .accounts(items)
                .build();
    }

    // ── Start onboarding ───────────────────────────────────────────────────────

    @Transactional
    public StripeOnboardingResponse startOnboarding(Long sellerId) {
        // Sync from Stripe first so we don't block a seller whose account is
        // already complete on Stripe but DB is stale.
        sellerStripeAccountRepository.findBySellerId(sellerId).ifPresent(existing -> {
            syncFromStripe(existing);
            if (Boolean.TRUE.equals(existing.getDetailsSubmitted())) {
                throw new AppException(ErrorCode.ALREADY_EXISTS,
                        "Seller đã có Stripe account hoàn chỉnh");
            }
        });

        SellerStripeAccount account = sellerStripeAccountRepository
                .findBySellerId(sellerId)
                .orElseGet(() -> createStripeExpressAccount(sellerId));

        // Create Stripe AccountLink (valid 24h) — seller fills Stripe-hosted form.
        AccountLink accountLink = createAccountLink(account.getStripeAccountId());
        String onboardingUrl = accountLink.getUrl();
        Instant expiresAt = Instant.now().plusSeconds(86400);

        account.setOnboardingUrl(onboardingUrl);
        account.setOnboardingUrlExpiresAt(LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
        sellerStripeAccountRepository.save(account);

        // Publish link via Kafka so notification-service can deliver it to seller.
        publishOnboardingLink(sellerId, account.getStripeAccountId(), onboardingUrl, expiresAt);

        log.info("Stripe onboarding started for seller {}: account={}", sellerId, account.getStripeAccountId());

        return StripeOnboardingResponse.builder()
                .onboardingUrl(onboardingUrl)
                .expressDashboardUrl(expressDashboardUrl(account))
                .stripeAccountId(account.getStripeAccountId())
                .expiresAt(expiresAt)
                .build();
    }

    // ── Check status ───────────────────────────────────────────────────────────

    @Transactional
    public StripeOnboardingStatusResponse getOnboardingStatus(Long sellerId) {
        SellerStripeAccount account = sellerStripeAccountRepository.findBySellerId(sellerId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Seller chưa bắt đầu onboarding Stripe"));

        // Sync live state from Stripe every time — this is the primary sync path.
        syncFromStripe(account);

        return StripeOnboardingStatusResponse.builder()
                .stripeAccountId(account.getStripeAccountId())
                .accountStatus(account.getAccountStatus())
                .detailsSubmitted(account.getDetailsSubmitted())
                .chargesEnabled(account.getChargesEnabled())
                .payoutsEnabled(account.getPayoutsEnabled())
                .onboardingStatus(account.getOnboardingStatus())
                .onboardingUrl(Boolean.TRUE.equals(account.getDetailsSubmitted())
                        ? null : account.getOnboardingUrl())
                .expressDashboardUrl(expressDashboardUrl(account))
                .build();
    }

    // ── Refresh link ───────────────────────────────────────────────────────────

    @Transactional
    public StripeOnboardingResponse refreshOnboardingLink(Long sellerId) {
        SellerStripeAccount account = sellerStripeAccountRepository.findBySellerId(sellerId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Seller chưa bắt đầu onboarding Stripe"));

        syncFromStripe(account);

        if (Boolean.TRUE.equals(account.getDetailsSubmitted())) {
            throw new AppException(ErrorCode.ALREADY_EXISTS,
                    "Seller đã hoàn tất KYC, không cần refresh link");
        }

        AccountLink accountLink = createAccountLink(account.getStripeAccountId());
        String onboardingUrl = accountLink.getUrl();
        Instant expiresAt = Instant.now().plusSeconds(86400);

        account.setOnboardingUrl(onboardingUrl);
        account.setOnboardingUrlExpiresAt(LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
        sellerStripeAccountRepository.save(account);

        publishOnboardingLink(sellerId, account.getStripeAccountId(), onboardingUrl, expiresAt);

        log.info("Stripe onboarding link refreshed for seller {}", sellerId);

        return StripeOnboardingResponse.builder()
                .onboardingUrl(onboardingUrl)
                .expressDashboardUrl(expressDashboardUrl(account))
                .stripeAccountId(account.getStripeAccountId())
                .expiresAt(expiresAt)
                .build();
    }

    // ── Stripe helpers ─────────────────────────────────────────────────────────

    private SellerStripeAccount createStripeExpressAccount(Long sellerId) {
        try {
            AccountCreateParams params = AccountCreateParams.builder()
                    .setType(AccountCreateParams.Type.EXPRESS)
                    .setCountry(stripeConfig.getDefaultCountry())
                    .setCapabilities(AccountCreateParams.Capabilities.builder()
                            .setCardPayments(AccountCreateParams.Capabilities.CardPayments.builder()
                                    .setRequested(true).build())
                            .setTransfers(AccountCreateParams.Capabilities.Transfers.builder()
                                    .setRequested(true).build())
                            .build())
                    .build();

            Account stripeAccount = Account.create(params);

            SellerStripeAccount entity = SellerStripeAccount.builder()
                    .sellerId(sellerId)
                    .stripeAccountId(stripeAccount.getId())
                    .accountStatus("PENDING")
                    .chargesEnabled(false)
                    .payoutsEnabled(false)
                    .detailsSubmitted(false)
                    .build();

            return sellerStripeAccountRepository.save(entity);
        } catch (StripeException e) {
            log.error("Failed to create Stripe Express account for seller {}: {}", sellerId, e.getMessage());
            throw new AppException(ErrorCode.INTERNAL_ERROR,
                    "Stripe API error: " + e.getMessage());
        }
    }

    private AccountLink createAccountLink(String stripeAccountId) {
        try {
            AccountLinkCreateParams params = AccountLinkCreateParams.builder()
                    .setAccount(stripeAccountId)
                    .setRefreshUrl(stripeConfig.getOnboardingRefreshUrl())
                    .setReturnUrl(stripeConfig.getOnboardingReturnUrl())
                    .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                    .setCollectionOptions(AccountLinkCreateParams.CollectionOptions.builder()
                            .setFields(AccountLinkCreateParams.CollectionOptions.Fields.EVENTUALLY_DUE)
                            .setFutureRequirements(
                                    AccountLinkCreateParams.CollectionOptions.FutureRequirements.INCLUDE)
                            .build())
                    .build();

            return AccountLink.create(params);
        } catch (StripeException e) {
            log.error("Failed to create AccountLink for {}: {}", stripeAccountId, e.getMessage());
            throw new AppException(ErrorCode.INTERNAL_ERROR,
                    "Stripe API error: " + e.getMessage());
        }
    }

    // ── Sync ───────────────────────────────────────────────────────────────────

    /**
     * Sync DB state with live Stripe data. Called before any decision that
     * depends on details_submitted / charges_enabled / payouts_enabled.
     */
    private void syncFromStripe(SellerStripeAccount account) {
        try {
            Account sa = Account.retrieve(account.getStripeAccountId());

            boolean needsUpdate =
                    !Boolean.TRUE.equals(sa.getDetailsSubmitted())
                            != !Boolean.TRUE.equals(account.getDetailsSubmitted())
                    || !Boolean.TRUE.equals(sa.getChargesEnabled())
                            != !Boolean.TRUE.equals(account.getChargesEnabled())
                    || !Boolean.TRUE.equals(sa.getPayoutsEnabled())
                            != !Boolean.TRUE.equals(account.getPayoutsEnabled());

            if (!needsUpdate) return;

            account.setDetailsSubmitted(Boolean.TRUE.equals(sa.getDetailsSubmitted()));
            account.setChargesEnabled(Boolean.TRUE.equals(sa.getChargesEnabled()));
            account.setPayoutsEnabled(Boolean.TRUE.equals(sa.getPayoutsEnabled()));

            if (Boolean.TRUE.equals(sa.getDetailsSubmitted())) {
                account.setAccountStatus("ACTIVE");
                account.setOnboardingUrl(null);
                account.setOnboardingUrlExpiresAt(null);
            } else if (sa.getRequirements() != null
                    && "restricted".equals(sa.getRequirements().getDisabledReason())) {
                account.setAccountStatus("SUSPENDED");
            }

            sellerStripeAccountRepository.save(account);
            log.info("Stripe synced for seller {}: details={}, charges={}, payouts={}",
                    account.getSellerId(), account.getDetailsSubmitted(),
                    account.getChargesEnabled(), account.getPayoutsEnabled());
        } catch (StripeException e) {
            log.warn("Failed to sync Stripe for seller {}: {}",
                    account.getSellerId(), e.getMessage());
        }
    }

    // ── Kafka ──────────────────────────────────────────────────────────────────

    private void publishOnboardingLink(Long sellerId, String stripeAccountId,
                                        String onboardingUrl, Instant expiresAt) {
        try {
            SellerStripeRequirementPayload payload = SellerStripeRequirementPayload.builder()
                    .sellerId(sellerId)
                    .stripeAccountId(stripeAccountId)
                    .requirementType("onboarding_link")
                    .requirementReason("Complete Stripe Connect onboarding to receive payments")
                    .accountLinkUrl(onboardingUrl)
                    .accountLinkExpiresAt(expiresAt.toEpochMilli())
                    .build();
            kafkaPublisher.publish(KafkaTopics.SELLER_STRIPE_REQUIREMENT,
                    String.valueOf(sellerId), payload);
            log.info("Published onboarding link to Kafka for seller {}", sellerId);
        } catch (Exception e) {
            log.error("Failed to publish onboarding link to Kafka for seller {}: {}",
                    sellerId, e.getMessage(), e);
        }
    }

    // ── Util ───────────────────────────────────────────────────────────────────

    /**
     * Build view-as URL: admin sees seller's Stripe test dashboard in the
     * platform's Stripe Dashboard.
     * <p>
     * Format: https://dashboard.stripe.com/{platform_acct}/connect/view-as/{seller_acct}/test/dashboard
     */
    private String expressDashboardUrl(SellerStripeAccount account) {
        String platformAcct = stripeConfig.getPlatformAccountId();
        if (platformAcct == null) {
            // Fallback: Express Dashboard (seller logs in directly)
            return "https://connect.stripe.com/express/" + account.getStripeAccountId();
        }
        return String.format(DASHBOARD_URL_FMT, platformAcct, account.getStripeAccountId());
    }
}
