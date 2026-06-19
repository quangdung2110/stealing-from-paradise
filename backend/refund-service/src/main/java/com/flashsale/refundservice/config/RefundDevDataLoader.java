package com.flashsale.refundservice.config;

import com.flashsale.commonlib.config.DevDataProperties;
import com.flashsale.refundservice.domain.repository.RefundItemRepository;
import com.flashsale.refundservice.domain.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "dev-data.enabled", havingValue = "true", matchIfMissing = false)
public class RefundDevDataLoader implements CommandLineRunner {

    private final RefundRepository refundRepository;
    private final RefundItemRepository refundItemRepository;
    private final DevDataProperties devDataProperties;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("[RefundDevDataLoader] Starting dev data seed for refund-service...");

        if (devDataProperties.isReset()) {
            log.warn("[RefundDevDataLoader] RESET=true — wiping all refund data...");
            refundItemRepository.deleteAllInBatch();
            refundRepository.deleteAllInBatch();
            log.info("[RefundDevDataLoader] All refund data wiped.");
        } else if (refundRepository.count() > 0) {
            log.info("[RefundDevDataLoader] Data already exists, skipping seed.");
            return;
        }

        seedFeData();

        log.info("[RefundDevDataLoader] Dev data seed complete.");
    }

    private void seedFeData() {
        log.info("[RefundDevDataLoader] Seeding FE test-dataset...");

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM refund.refunds WHERE id >= 900201", Integer.class);
        if (count != null && count > 0) {
            log.info("[RefundDevDataLoader] FE data already exists, skipping.");
            return;
        }

        // ---- Refunds (7 total: 5 existing + 2 new) ----
        jdbcTemplate.update("INSERT INTO refund.refunds (id, transaction_id, order_id, user_id, group_ref, type, initiated_by, refund_reason_type, amount, reason, status, evidence_images, reject_reason, admin_note, reviewed_by, reviewed_at, refund_ref, raw_response, created_at, updated_at) VALUES " +
            "(900201, 900106, 900106, 900001, '90000000-0000-4000-c001-000000000201', 'PARTIAL', 'BUYER', 'MISSING_ITEM', 1990000, 'One accessory was missing from the package.', 'PENDING', '[\"https://picsum.photos/seed/fe-refund-pending/600/400\"]'::jsonb, null, null, null, null, null, '{}'::jsonb, now() - interval '6 hours', now()), " +
            "(900202, 900107, 900107, 900001, '90000000-0000-4000-c001-000000000202', 'FULL', 'BUYER', 'ITEM_BROKEN', 4990000, 'Product stopped working after delivery.', 'COMPLETED', '[\"https://picsum.photos/seed/fe-refund-completed/600/400\"]'::jsonb, null, 'Approved and refunded by admin fixture.', 900003, now() - interval '2 days', 're_fe_completed_900202', '{\"status\":\"succeeded\"}'::jsonb, now() - interval '3 days', now()), " +
            "(900203, 900104, 900104, 900001, '90000000-0000-4000-c001-000000000203', 'PARTIAL', 'BUYER', 'ITEM_NOT_AS_DESCRIBED', 390000, 'Requested refund after normal usage.', 'REJECTED', '[\"https://picsum.photos/seed/fe-refund-rejected/600/400\"]'::jsonb, 'Evidence does not show seller fault.', 'Reject fixture for admin screen.', 900003, now() - interval '1 day', null, '{}'::jsonb, now() - interval '2 days', now()), " +
            "(900204, 900108, 900108, 900001, '90000000-0000-4000-c001-000000000204', 'FULL', 'SYSTEM', 'RETURN_TO_SENDER', 27990000, 'Carrier returned package to seller.', 'PROCESSING', '[\"https://picsum.photos/seed/fe-refund-processing/600/400\"]'::jsonb, null, 'RTS automatic refund is processing.', 900003, now() - interval '12 hours', null, '{}'::jsonb, now() - interval '1 day', now()), " +
            "(900205, 900111, 900111, 900001, '90000000-0000-4000-c001-000000000205', 'FULL', 'BUYER', 'ITEM_BROKEN', 23990000, 'Item arrived damaged.', 'PENDING', '[\"https://picsum.photos/seed/fe-refund-pending-205/600/400\"]'::jsonb, null, null, null, null, null, '{}'::jsonb, now() - interval '3 hours', now()), " +
            "(900206, 900112, 900112, 900001, '90000000-0000-4000-c001-000000000206', 'FULL', 'SELLER', 'ITEM_FAULTY', 1990000, 'Seller accepted fault for Bluetooth Earbuds Pro connectivity issue.', 'APPROVED', '[\"https://picsum.photos/seed/fe-refund-approved-206/600/400\"]'::jsonb, null, 'Approved by admin — refund initiated.', 900003, now() - interval '1 hour', 're_fe_approved_900206', '{\"status\":\"pending_refund\"}'::jsonb, now() - interval '6 hours', now()), " +
            "(900207, 900110, 900110, 900001, '90000000-0000-4000-c001-000000000207', 'PARTIAL', 'BUYER', 'CUSTOMER_REQUEST', 149000, 'Summer T-Shirt size S was too small.', 'PENDING', '[\"https://picsum.photos/seed/fe-refund-pending-207/600/400\"]'::jsonb, null, null, null, null, null, '{}'::jsonb, now() - interval '2 hours', now()) " +
            "ON CONFLICT (id) DO UPDATE SET transaction_id=EXCLUDED.transaction_id,order_id=EXCLUDED.order_id,user_id=EXCLUDED.user_id,group_ref=EXCLUDED.group_ref,type=EXCLUDED.type,initiated_by=EXCLUDED.initiated_by,refund_reason_type=EXCLUDED.refund_reason_type,amount=EXCLUDED.amount,reason=EXCLUDED.reason,status=EXCLUDED.status,evidence_images=EXCLUDED.evidence_images,reject_reason=EXCLUDED.reject_reason,admin_note=EXCLUDED.admin_note,reviewed_by=EXCLUDED.reviewed_by,reviewed_at=EXCLUDED.reviewed_at,refund_ref=EXCLUDED.refund_ref,raw_response=EXCLUDED.raw_response,updated_at=now()");

        // ---- Refund Items (7 total) ----
        jdbcTemplate.update("INSERT INTO refund.refund_items (id, refund_id, item_id, quantity, refund_amount, item_reason, status, return_tracking_number, return_evidence_images, returned_at) VALUES " +
            "(900201, 900201, 900106, 1, 1990000, 'Missing item in box', 'PENDING', null, null, null), " +
            "(900202, 900202, 900107, 1, 4990000, 'Broken item returned', 'COMPLETED', 'FE-RTS-900202', '[\"https://picsum.photos/seed/fe-return-completed/600/400\"]'::jsonb, now() - interval '2 days'), " +
            "(900203, 900203, 900104, 1, 390000, 'Evidence rejected', 'REJECTED', null, null, null), " +
            "(900204, 900204, 900108, 1, 27990000, 'Carrier returned to sender', 'PROCESSING', 'FE-RTS-900204', '[\"https://picsum.photos/seed/fe-return-processing/600/400\"]'::jsonb, now() - interval '1 day'), " +
            "(900205, 900205, 900111, 2, 23990000, 'Item arrived damaged', 'PENDING', null, null, null), " +
            "(900206, 900206, 900112, 2, 1990000, 'Both items faulty — earbuds + charger pad', 'PENDING', 'FE-RTS-900206', null, now() - interval '5 hours'), " +
            "(900207, 900207, 900110, 1, 149000, 'Size S too small — requesting refund', 'PENDING', null, null, null) " +
            "ON CONFLICT (id) DO UPDATE SET refund_id=EXCLUDED.refund_id,item_id=EXCLUDED.item_id,quantity=EXCLUDED.quantity,refund_amount=EXCLUDED.refund_amount,item_reason=EXCLUDED.item_reason,status=EXCLUDED.status,return_tracking_number=EXCLUDED.return_tracking_number,return_evidence_images=EXCLUDED.return_evidence_images,returned_at=EXCLUDED.returned_at");

        jdbcTemplate.queryForObject("SELECT setval('refund.refunds_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM refund.refunds), 900207))", Long.class);
        jdbcTemplate.queryForObject("SELECT setval('refund.refund_items_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM refund.refund_items), 900207))", Long.class);

        log.info("[RefundDevDataLoader] FE test-dataset seeded (7 refunds, 7 refund_items).");
    }
}
