package com.flashsale.paymentservice.scheduler;

import com.flashsale.paymentservice.domain.model.SellerStripeAccount;
import com.flashsale.paymentservice.domain.repository.SellerStripeAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * JOB-15: nullify expired Stripe onboarding URLs.
 * Stripe Account Link URLs expire after ~24h server-side; clearing them
 * forces the UI to request a fresh link instead of presenting a dead URL.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StripeOnboardingUrlScheduler {

    private final SellerStripeAccountRepository accountRepository;

    @Scheduled(cron = "${payment.scheduler.onboarding-url-cron:0 0 * * * *}")
    @SchedulerLock(name = "payment-nullify-expired-onboarding-urls", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    @Transactional
    public void nullifyExpiredOnboardingUrls() {
        LocalDateTime now = LocalDateTime.now();
        List<SellerStripeAccount> expired =
                accountRepository.findByOnboardingUrlIsNotNullAndOnboardingUrlExpiresAtBefore(now);
        if (expired.isEmpty()) return;

        for (SellerStripeAccount account : expired) {
            account.setOnboardingUrl(null);
            account.setOnboardingUrlExpiresAt(null);
        }
        accountRepository.saveAll(expired);
        log.info("JOB-15: nullified {} expired Stripe onboarding URLs", expired.size());
    }
}
