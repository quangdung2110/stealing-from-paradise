package com.flashsale.paymentservice.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.config.DevDataProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "dev-data.enabled", havingValue = "true", matchIfMissing = false)
public class PaymentDevDataLoader implements CommandLineRunner {

    private final DevDataProperties devDataProperties;
    private final JdbcTemplate jdbcTemplate;

    private static final String JSON_PATH = "test-data/payments.json";

    @Override
    @Transactional
    public void run(String... args) {
        log.info("[PaymentDevDataLoader] Starting dev data seed for payment-service...");

        if (devDataProperties.isReset()) {
            log.warn("[PaymentDevDataLoader] RESET=true -- wiping all payment data...");
            jdbcTemplate.update("DELETE FROM payment.seller_transfers");
            jdbcTemplate.update("DELETE FROM payment.transactions");
            jdbcTemplate.update("DELETE FROM payment.seller_stripe_accounts");
            log.info("[PaymentDevDataLoader] All payment data wiped.");
        }

        seedFeData();
        seedFromJsonDataset();

        log.info("[PaymentDevDataLoader] Dev data seed complete.");
    }

    private void seedFeData() {
        log.info("[PaymentDevDataLoader] Seeding FE test-dataset...");

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM payment.seller_stripe_accounts WHERE id >= 900002", Integer.class);
        if (count != null && count > 0) {
            log.info("[PaymentDevDataLoader] FE data already exists, skipping.");
            return;
        }

        // FE Seller Stripe Accounts
        jdbcTemplate.update("INSERT INTO payment.seller_stripe_accounts (id, seller_id, stripe_account_id, account_status, charges_enabled, payouts_enabled, details_submitted, onboarding_url, onboarding_url_expires_at, express_dashboard_url, created_at, updated_at) VALUES " +
            "(900002, 900002, 'acct_fe_seller_900002', 'ACTIVE', true, true, true, null, null, 'https://dashboard.stripe.com/test/connect/accounts/acct_fe_seller_900002', now() - interval '15 days', now()), " +
            "(900003, 900003, 'acct_fe_admin_900003', 'REQUIREMENTS_DUE', false, false, false, 'https://connect.stripe.com/setup/e/acct_fe_admin_900003', now() + interval '7 days', null, now() - interval '1 day', now()) " +
            "ON CONFLICT (seller_id) DO UPDATE SET stripe_account_id=EXCLUDED.stripe_account_id,account_status=EXCLUDED.account_status,charges_enabled=EXCLUDED.charges_enabled,payouts_enabled=EXCLUDED.payouts_enabled,details_submitted=EXCLUDED.details_submitted,onboarding_url=EXCLUDED.onboarding_url,onboarding_url_expires_at=EXCLUDED.onboarding_url_expires_at,express_dashboard_url=EXCLUDED.express_dashboard_url,updated_at=now()");

        // FE Transactions
        jdbcTemplate.update("INSERT INTO payment.transactions (id, parent_order_id, amount, trans_ref, stripe_transfer_id, application_fee_amount, stripe_connect_mode, status, raw_response, pay_at, created_at, updated_at) VALUES " +
            "(900101, 900101, 23990000, 'FE-TX-PENDING-900101', null, 1199500, 'DESTINATION', 'PENDING', '{\"id\":\"pi_fe_pending_900101\",\"object\":\"payment_intent\",\"client_secret\":\"pi_fe_pending_900101_secret_test\",\"status\":\"requires_payment_method\"}'::jsonb, null, now() - interval '1 hour', now()), " +
            "(900102, 900102, 23990000, 'FE-TX-PAID-900102', null, 1199500, 'DESTINATION', 'PAID', '{\"id\":\"pi_fe_paid_900102\",\"object\":\"payment_intent\",\"status\":\"succeeded\"}'::jsonb, now() - interval '23 hours', now() - interval '1 day', now()), " +
            "(900103, 900103, 4990000, 'FE-TX-PAID-900103', null, 249500, 'DESTINATION', 'PAID', '{\"id\":\"pi_fe_paid_900103\",\"object\":\"payment_intent\",\"status\":\"succeeded\"}'::jsonb, now() - interval '2 days', now() - interval '2 days', now()), " +
            "(900104, 900104, 790000, 'FE-TX-PAID-900104', null, 39500, 'DESTINATION', 'PAID', '{\"id\":\"pi_fe_paid_900104\",\"object\":\"payment_intent\",\"status\":\"succeeded\"}'::jsonb, now() - interval '4 days', now() - interval '4 days', now()), " +
            "(900105, 900105, 790000, 'FE-TX-FAILED-900105', null, 0, 'DESTINATION', 'FAILED', '{\"id\":\"pi_fe_failed_900105\",\"object\":\"payment_intent\",\"status\":\"canceled\"}'::jsonb, null, now() - interval '5 days', now()), " +
            "(900106, 900106, 4990000, 'FE-TX-PARTIAL-900106', null, 249500, 'DESTINATION', 'PARTIALLY_REFUNDED', '{\"id\":\"pi_fe_partial_900106\",\"object\":\"payment_intent\",\"status\":\"succeeded\"}'::jsonb, now() - interval '6 days', now() - interval '6 days', now()), " +
            "(900107, 900107, 4990000, 'FE-TX-REFUNDED-900107', null, 249500, 'DESTINATION', 'REFUNDED', '{\"id\":\"pi_fe_refunded_900107\",\"object\":\"payment_intent\",\"status\":\"succeeded\"}'::jsonb, now() - interval '7 days', now() - interval '7 days', now()), " +
            "(900108, 900108, 27990000, 'FE-TX-RETURNED-900108', null, 1399500, 'DESTINATION', 'REFUNDED', '{\"id\":\"pi_fe_returned_900108\",\"object\":\"payment_intent\",\"status\":\"succeeded\"}'::jsonb, now() - interval '8 days', now() - interval '8 days', now()), " +
            "(900109, 900109, 27990000, 'FE-TX-PAIDOUT-900109', 'tr_fe_paidout_900109', 1399500, 'DESTINATION', 'PAID', '{\"id\":\"pi_fe_paidout_900109\",\"object\":\"payment_intent\",\"status\":\"succeeded\"}'::jsonb, now() - interval '20 days', now() - interval '20 days', now()), " +
            "(900110, 900110, 1590000, 'FE-TX-PAID-900110', null, 79500, 'DESTINATION', 'PAID', '{\"id\":\"pi_fe_paid_900110\",\"object\":\"payment_intent\",\"status\":\"succeeded\"}'::jsonb, now() - interval '3 days', now() - interval '3 days', now()), " +
            "(900111, 900111, 23990000, 'FE-TX-PAID-900111', null, 1199500, 'DESTINATION', 'PAID', '{\"id\":\"pi_fe_paid_900111\",\"object\":\"payment_intent\",\"status\":\"succeeded\"}'::jsonb, now() - interval '7 days', now() - interval '7 days', now()), " +
            "(900112, 900112, 1990000, 'FE-TX-PAID-900112', null, 99500, 'DESTINATION', 'PAID', '{\"id\":\"pi_fe_paid_900112\",\"object\":\"payment_intent\",\"status\":\"succeeded\"}'::jsonb, now() - interval '12 hours', now() - interval '12 hours', now()), " +
            "(900113, 900113, 350000, 'FE-TX-PAID-900113', null, 17500, 'DESTINATION', 'PAID', '{\"id\":\"pi_fe_paid_900113\",\"object\":\"payment_intent\",\"status\":\"succeeded\"}'::jsonb, now() - interval '2 days', now() - interval '2 days', now()), " +
            "(900114, 900114, 890000, 'FE-TX-PAID-900114', null, 44500, 'DESTINATION', 'PAID', '{\"id\":\"pi_fe_paid_900114\",\"object\":\"payment_intent\",\"status\":\"succeeded\"}'::jsonb, now() - interval '1 day', now() - interval '1 day', now()) " +
            "ON CONFLICT (id) DO UPDATE SET parent_order_id=EXCLUDED.parent_order_id,amount=EXCLUDED.amount,trans_ref=EXCLUDED.trans_ref,stripe_transfer_id=EXCLUDED.stripe_transfer_id,application_fee_amount=EXCLUDED.application_fee_amount,stripe_connect_mode=EXCLUDED.stripe_connect_mode,status=EXCLUDED.status,raw_response=EXCLUDED.raw_response,pay_at=EXCLUDED.pay_at,updated_at=now()");

        // FE Seller Transfers
        jdbcTemplate.update("INSERT INTO payment.seller_transfers (id, order_id, parent_order_id, seller_id, transfer_amount, stripe_transfer_id, status, delivered_at, payout_eligible_at, platform_commission_amt, payout_at, payout_retry_count, created_at, updated_at) VALUES " +
            "(900102, 900102, 900102, 900002, 23990000, null, 'AWAITING_DELIVERY', null, null, 1199500, null, 0, now() - interval '23 hours', now()), " +
            "(900103, 900103, 900103, 900002, 4990000, null, 'AWAITING_DELIVERY', null, null, 249500, null, 0, now() - interval '2 days', now()), " +
            "(900104, 900104, 900104, 900002, 790000, null, 'RETURN_WINDOW', now() - interval '2 days', now() + interval '5 days', 39500, null, 0, now() - interval '4 days', now()), " +
            "(900106, 900106, 900106, 900002, 4990000, null, 'READY_FOR_PAYOUT', now() - interval '10 days', now() - interval '3 days', 249500, null, 0, now() - interval '6 days', now()), " +
            "(900107, 900107, 900107, 900002, 4990000, null, 'REFUNDED', now() - interval '4 days', now() + interval '3 days', 249500, null, 0, now() - interval '7 days', now()), " +
            "(900108, 900108, 900108, 900002, 27990000, null, 'SKIPPED', now() - interval '5 days', now() + interval '2 days', 1399500, null, 0, now() - interval '8 days', now()), " +
            "(900109, 900109, 900109, 900002, 27990000, 'tr_fe_paidout_900109', 'PAID_OUT', now() - interval '12 days', now() - interval '5 days', 1399500, now() - interval '4 days', 0, now() - interval '20 days', now()), " +
            "(900110, 900110, 900110, 900002, 1590000, null, 'AWAITING_DELIVERY', null, null, 79500, null, 0, now() - interval '3 days', now()), " +
            "(900111, 900111, 900111, 900002, 23990000, null, 'RETURN_WINDOW', now() - interval '7 days', now() + interval '1 day', 1199500, null, 0, now() - interval '7 days', now()), " +
            "(900112, 900112, 900112, 900002, 1990000, null, 'AWAITING_DELIVERY', null, null, 99500, null, 0, now() - interval '12 hours', now()), " +
            "(900113, 900113, 900113, 900002, 350000, null, 'RETURN_WINDOW', now() - interval '2 days', now() + interval '5 days', 17500, null, 0, now() - interval '2 days', now()), " +
            "(900114, 900114, 900114, 900002, 890000, null, 'AWAITING_DELIVERY', null, null, 44500, null, 0, now() - interval '1 day', now()) " +
            "ON CONFLICT (id) DO UPDATE SET order_id=EXCLUDED.order_id,parent_order_id=EXCLUDED.parent_order_id,seller_id=EXCLUDED.seller_id,transfer_amount=EXCLUDED.transfer_amount,stripe_transfer_id=EXCLUDED.stripe_transfer_id,status=EXCLUDED.status,delivered_at=EXCLUDED.delivered_at,payout_eligible_at=EXCLUDED.payout_eligible_at,platform_commission_amt=EXCLUDED.platform_commission_amt,payout_at=EXCLUDED.payout_at,payout_retry_count=EXCLUDED.payout_retry_count,updated_at=now()");

        // Reset sequences
        jdbcTemplate.queryForObject("SELECT setval('payment.seller_stripe_accounts_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM payment.seller_stripe_accounts), 900003))", Long.class);
        jdbcTemplate.queryForObject("SELECT setval('payment.transactions_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM payment.transactions), 900114))", Long.class);
        jdbcTemplate.queryForObject("SELECT setval('payment.seller_transfers_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM payment.seller_transfers), 900114))", Long.class);

        log.info("[PaymentDevDataLoader] FE test-dataset seeded (2 stripe accounts, 14 transactions, 12 transfers).");
    }

    /**
     * Seeds payments from {@code test-data/payments.json}.
     * Each entry becomes a transaction, optional seller_transfer, optional refund.
     * Idempotent via ON CONFLICT DO UPDATE.
     */
    private void seedFromJsonDataset() {
        List<Map<String, Object>> payments;
        try (InputStream is = new ClassPathResource(JSON_PATH).getInputStream()) {
            payments = new ObjectMapper().readValue(is, new TypeReference<List<Map<String, Object>>>() {});
        } catch (IOException e) {
            log.warn("[PaymentDevDataLoader] Could not read {} — skipping JSON seed: {}", JSON_PATH, e.getMessage());
            return;
        }

        log.info("[PaymentDevDataLoader] Seeding {} payments from {}", payments.size(), JSON_PATH);

        // Check if JSON data already exists
        Map<String, Object> first = payments.getFirst();
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM payment.transactions WHERE trans_ref = ?",
            Integer.class, first.get("transRef"));
        if (count != null && count > 0) {
            log.info("[PaymentDevDataLoader] JSON dataset already seeded, skipping.");
            return;
        }

        int maxTxId = 0;
        int maxTransferId = 0;

        for (Map<String, Object> pmt : payments) {
            int txId = ((Number) pmt.get("transactionId")).intValue();
            int parentOrderId = ((Number) pmt.get("parentOrderId")).intValue();
            double amount = ((Number) pmt.get("amount")).doubleValue();
            double appFee = ((Number) pmt.get("applicationFee")).doubleValue();
            String transRef = (String) pmt.get("transRef");
            String status = (String) pmt.get("status");
            String stripeConnectMode = (String) pmt.get("stripeConnectMode");

            maxTxId = Math.max(maxTxId, txId);
            String stripePiId = "pi_from_json_" + txId;

            // Insert transaction
            jdbcTemplate.update("""
                INSERT INTO payment.transactions (id, parent_order_id, amount, trans_ref, stripe_transfer_id, application_fee_amount, stripe_connect_mode, status, raw_response, pay_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, null, ?, ?, ?, '{"id":"' || ? || '","object":"payment_intent","status":"succeeded"}'::jsonb, now() - interval '2 hours', now(), now())
                ON CONFLICT (id) DO UPDATE SET
                    parent_order_id = EXCLUDED.parent_order_id, amount = EXCLUDED.amount,
                    trans_ref = EXCLUDED.trans_ref, application_fee_amount = EXCLUDED.application_fee_amount,
                    status = EXCLUDED.status, updated_at = now()
                """, txId, parentOrderId, amount, transRef, appFee, stripeConnectMode, status, stripePiId);

            // Seller transfer if present
            @SuppressWarnings("unchecked")
            Map<String, Object> transfer = (Map<String, Object>) pmt.get("sellerTransfer");
            if (transfer != null) {
                int sellerId = (int) transfer.get("sellerId");
                double transferAmount = ((Number) transfer.get("transferAmount")).doubleValue();
                String transferStatus = (String) transfer.get("status");

                maxTransferId = Math.max(maxTransferId, txId);

                jdbcTemplate.update("""
                    INSERT INTO payment.seller_transfers (id, order_id, parent_order_id, seller_id, transfer_amount, stripe_transfer_id, status, delivered_at, payout_eligible_at, platform_commission_amt, payout_at, payout_retry_count, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, null, ?, CASE WHEN ? IN ('RETURN_WINDOW','READY_FOR_PAYOUT','PAID_OUT') THEN now() - interval '5 days' ELSE null END, CASE WHEN ? IN ('READY_FOR_PAYOUT','PAID_OUT') THEN now() - interval '1 day' ELSE null END, ?, null, 0, now(), now())
                    ON CONFLICT (id) DO UPDATE SET
                        order_id = EXCLUDED.order_id, seller_id = EXCLUDED.seller_id,
                        transfer_amount = EXCLUDED.transfer_amount, status = EXCLUDED.status,
                        platform_commission_amt = EXCLUDED.platform_commission_amt, updated_at = now()
                    """, txId, txId, parentOrderId, sellerId, transferAmount,
                    transferStatus, transferStatus, transferStatus, appFee);
            }
        }

        // Reset sequences
        jdbcTemplate.queryForObject("SELECT setval('payment.transactions_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM payment.transactions), ?))",
            Long.class, Math.max(maxTxId, 900114));
        jdbcTemplate.queryForObject("SELECT setval('payment.seller_transfers_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM payment.seller_transfers), ?))",
            Long.class, Math.max(maxTransferId, 900114));

        log.info("[PaymentDevDataLoader] JSON dataset seeded ({} transactions).", payments.size());
    }
}
