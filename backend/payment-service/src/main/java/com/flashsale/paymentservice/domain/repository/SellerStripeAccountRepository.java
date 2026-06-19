package com.flashsale.paymentservice.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.flashsale.paymentservice.domain.model.SellerStripeAccount;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SellerStripeAccountRepository extends JpaRepository<SellerStripeAccount, Long> {
    Optional<SellerStripeAccount> findBySellerId(Long sellerId);
    Optional<SellerStripeAccount> findByStripeAccountId(String stripeAccountId);

    List<SellerStripeAccount> findByOnboardingUrlIsNotNullAndOnboardingUrlExpiresAtBefore(LocalDateTime cutoff);
}

