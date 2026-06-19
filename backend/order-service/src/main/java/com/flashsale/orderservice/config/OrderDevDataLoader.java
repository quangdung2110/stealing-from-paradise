package com.flashsale.orderservice.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.config.DevDataProperties;
import com.flashsale.orderservice.domain.repository.ParentOrderRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
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
public class OrderDevDataLoader implements CommandLineRunner {

    private final ParentOrderRepository parentOrderRepository;
    private final DevDataProperties devDataProperties;

    @PersistenceContext
    private EntityManager entityManager;

    // ------------------------------------------------------------------ //
    //  SHIPPING ADDRESS — fe_buyer (900001) address
    // ------------------------------------------------------------------ //

    private static final String FE_SHIPPING_ADDRESS = """
            {"full_address":"123 Frontend Test Street, District 1, Ho Chi Minh City","province_id":79,"district_id":760}""";

    private static final String JSON_PATH = "test-data/orders.json";

    @Override
    @Transactional
    public void run(String... args) {
        log.info("[OrderDevDataLoader] Starting dev data seed for order-service...");

        if (devDataProperties.isReset()) {
            log.warn("[OrderDevDataLoader] RESET=true — wiping all order data...");
            entityManager.createNativeQuery("DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE parent_order_id IN (SELECT id FROM parent_orders))").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM orders WHERE parent_order_id IN (SELECT id FROM parent_orders)").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM parent_orders").executeUpdate();
            entityManager.flush();
            entityManager.clear();
            log.info("[OrderDevDataLoader] All order data wiped.");
        } else if (parentOrderRepository.count() > 0) {
            log.info("[OrderDevDataLoader] Data already exists, skipping seed.");
            log.info("[OrderDevDataLoader] Dev data seed complete.");
            return;
        }

        seedFeData();
        seedFromJsonDataset();

        log.info("[OrderDevDataLoader] Dev data seed complete.");
    }

    /**
     * Seeds FE test-dataset orders (900101-900112) via EntityManager native queries.
     */
    private void seedFeData() {
        log.info("[OrderDevDataLoader] Seeding FE test-dataset...");

        // Check if FE data already exists
        Long count = (Long) entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM orders.parent_orders WHERE id >= 900101")
            .getSingleResult();
        if (count != null && count > 0) {
            log.info("[OrderDevDataLoader] FE data already exists, skipping.");
            return;
        }

        // ========================================================================
        // Parent orders (900101-900112)
        // ========================================================================
        entityManager.createNativeQuery(
            "INSERT INTO orders.parent_orders (id, customer_id, session_id, total_amt, final_amt, created_at, updated_at) VALUES " +
            "(900101, 900001, 'fe-session-pending',      23990000, 23990000, now() - interval '1 hour', now()), " +
            "(900102, 900001, 'fe-session-paid',          23990000, 23990000, now() - interval '1 day', now()), " +
            "(900103, 900001, 'fe-session-shipping',      4990000,  4990000,  now() - interval '2 days', now()), " +
            "(900104, 900001, 'fe-session-delivered',     790000,   790000,   now() - interval '4 days', now()), " +
            "(900105, 900001, 'fe-session-cancelled',     790000,   790000,   now() - interval '5 days', now()), " +
            "(900106, 900001, 'fe-session-partial-refund',4990000,  4990000,  now() - interval '6 days', now()), " +
            "(900107, 900001, 'fe-session-refunded',      4990000,  4990000,  now() - interval '7 days', now()), " +
            "(900108, 900001, 'fe-session-returned',      27990000, 27990000, now() - interval '8 days', now()), " +
            "(900109, 900001, 'fe-session-paid-out',      27990000, 27990000, now() - interval '20 days', now()), " +
            "(900110, 900001, 'fe-session-shipping-2',    1590000,  1590000,  now() - interval '3 days', now()), " +
            "(900111, 900001, 'fe-session-delivered-2',   23990000, 23990000, now() - interval '10 days', now()), " +
            "(900112, 900001, 'fe-session-paid-2',        1990000,  1990000,  now() - interval '12 hours', now()), " +
            "(900113, 900001, 'fe-session-delivered-3',   350000,   350000,   now() - interval '2 days',    now()), " +
            "(900114, 900001, 'fe-session-shipping-3',    890000,   890000,   now() - interval '1 day',     now()) " +
            "ON CONFLICT (id) DO UPDATE SET customer_id=EXCLUDED.customer_id,session_id=EXCLUDED.session_id,total_amt=EXCLUDED.total_amt,final_amt=EXCLUDED.final_amt,updated_at=now()")
            .executeUpdate();

        // ========================================================================
        // Sub-orders (900101-900112)
        // ========================================================================
        entityManager.createNativeQuery(
            "INSERT INTO orders.orders (id, parent_order_id, seller_id, order_code, customer_id, total_amt, final_amt, status, cancelled_by, cancel_reason, is_flash_sale, shipping_address, tracking_number, shipping_deadline, delivered_at, version, created_at, updated_at) VALUES " +
            // 900101: PENDING, not yet paid
            "(900101, 900101, 900002, 'FE-ORD-PENDING-900101',   900001, 23990000, 23990000, 'PENDING',             null, null, false, '" + FE_SHIPPING_ADDRESS + "'::jsonb, null,                           now() + interval '3 days',  null,                            0, now() - interval '1 hour',   now()), " +
            // 900102: PAID, awaiting shipment
            "(900102, 900102, 900002, 'FE-ORD-PAID-900102',     900001, 23990000, 23990000, 'PAID',                null, null, false, '" + FE_SHIPPING_ADDRESS + "'::jsonb, null,                           now() + interval '3 days',  null,                            0, now() - interval '1 day',    now()), " +
            // 900103: SHIPPING, in transit, flash sale
            "(900103, 900103, 900002, 'FE-ORD-SHIPPING-900103', 900001, 4990000,  4990000,  'SHIPPING',            null, null, true,  '" + FE_SHIPPING_ADDRESS + "'::jsonb, 'FE-GHN-900103',               now() + interval '2 days',  null,                            0, now() - interval '2 days',  now()), " +
            // 900104: DELIVERED, delivered 2 days ago
            "(900104, 900104, 900002, 'FE-ORD-DELIVERED-900104',900001, 790000,   790000,   'DELIVERED',           null, null, false, '" + FE_SHIPPING_ADDRESS + "'::jsonb, 'FE-VNPOST-900104',            now() - interval '1 day',   now() - interval '2 days',       0, now() - interval '4 days',  now()), " +
            // 900105: CANCELLED by buyer
            "(900105, 900105, 900002, 'FE-ORD-CANCELLED-900105',900001, 790000,   790000,   'CANCELLED',           'BUYER', 'Changed mind before shipment', false, '" + FE_SHIPPING_ADDRESS + "'::jsonb, null,                           null,                       null,                            0, now() - interval '5 days',  now()), " +
            // 900106: PARTIALLY_REFUNDED
            "(900106, 900106, 900002, 'FE-ORD-PARTIAL-REFUND-900106', 900001, 4990000, 4990000, 'PARTIALLY_REFUNDED', null, null, false, '" + FE_SHIPPING_ADDRESS + "'::jsonb, 'FE-GHTK-900106',              now() - interval '2 days', now() - interval '3 days',       0, now() - interval '6 days',  now()), " +
            // 900107: REFUNDED
            "(900107, 900107, 900002, 'FE-ORD-REFUNDED-900107', 900001, 4990000,  4990000,  'REFUNDED',            null, null, false, '" + FE_SHIPPING_ADDRESS + "'::jsonb, 'FE-GHTK-900107',              now() - interval '3 days', now() - interval '4 days',       0, now() - interval '7 days',  now()), " +
            // 900108: RETURNED
            "(900108, 900108, 900002, 'FE-ORD-RETURNED-900108', 900001, 27990000, 27990000, 'RETURNED',            null, null, false, '" + FE_SHIPPING_ADDRESS + "'::jsonb, 'FE-GHN-900108',               now() - interval '4 days', now() - interval '5 days',       0, now() - interval '8 days',  now()), " +
            // 900109: DELIVERED (payout done, created 20 days ago, delivered 12 days ago)
            "(900109, 900109, 900002, 'FE-ORD-PAIDOUT-900109',  900001, 27990000, 27990000, 'DELIVERED',           null, null, false, '" + FE_SHIPPING_ADDRESS + "'::jsonb, 'FE-GHN-900109',               now() - interval '10 days', now() - interval '12 days',      0, now() - interval '20 days', now()), " +
            // 900110: SHIPPING, second seller product, different items
            "(900110, 900110, 900002, 'FE-ORD-SHIPPING-900110', 900001, 1590000,  1590000,  'SHIPPING',            null, null, false, '" + FE_SHIPPING_ADDRESS + "'::jsonb, 'FE-GHN-900110',               now() + interval '2 days',  null,                            0, now() - interval '3 days',  now()), " +
            // 900111: DELIVERED, within return window, payout_eligible_at in future
            "(900111, 900111, 900002, 'FE-ORD-DELIVERED-900111',900001, 23990000, 23990000, 'DELIVERED',           null, null, false, '" + FE_SHIPPING_ADDRESS + "'::jsonb, 'FE-VNPOST-900111',            now() - interval '5 days', now() - interval '7 days',       0, now() - interval '10 days', now()), " +
            // 900112: PAID, recently paid, different items
            "(900112, 900112, 900002, 'FE-ORD-PAID-900112',     900001, 1990000,  1990000,  'PAID',                null, null, false, '" + FE_SHIPPING_ADDRESS + "'::jsonb, null,                           now() + interval '3 days',  null,                            0, now() - interval '12 hours', now()), " +
            // 900113: DELIVERED, FE Yoga Mat Premium
            "(900113, 900113, 900002, 'FE-ORD-DELIVERED-900113',900001, 350000,   350000,   'DELIVERED',           null, null, false, '" + FE_SHIPPING_ADDRESS + "'::jsonb, 'FE-VNPOST-900113',            now() - interval '1 day',   now() - interval '2 days',       0, now() - interval '2 days',  now()), " +
            // 900114: SHIPPING, FE Travel Backpack
            "(900114, 900114, 900002, 'FE-ORD-SHIPPING-900114', 900001, 890000,   890000,   'SHIPPING',            null, null, false, '" + FE_SHIPPING_ADDRESS + "'::jsonb, 'FE-GHN-900114',               now() + interval '2 days',  null,                            0, now() - interval '1 day',   now()) " +
            "ON CONFLICT (id) DO UPDATE SET parent_order_id=EXCLUDED.parent_order_id,seller_id=EXCLUDED.seller_id,order_code=EXCLUDED.order_code,customer_id=EXCLUDED.customer_id,total_amt=EXCLUDED.total_amt,final_amt=EXCLUDED.final_amt,status=EXCLUDED.status,cancelled_by=EXCLUDED.cancelled_by,cancel_reason=EXCLUDED.cancel_reason,is_flash_sale=EXCLUDED.is_flash_sale,shipping_address=EXCLUDED.shipping_address,tracking_number=EXCLUDED.tracking_number,shipping_deadline=EXCLUDED.shipping_deadline,delivered_at=EXCLUDED.delivered_at,version=EXCLUDED.version,updated_at=now()")
            .executeUpdate();

        // ========================================================================
        // Order items (900101-900112)
        // ========================================================================
        entityManager.createNativeQuery(
            "INSERT INTO orders.order_items (id, order_id, sku_code, variant_id, name_snapshot, image_snapshot, price_snapshot, quantity, refunded_quantity, fs_item_id, created_at) VALUES " +
            "(900101, 900101, 'FE-SKU-PHONE-15PRO',   '90000000-0000-4000-9001-000000000101', 'FE Phone Pro Camera Kit',   'https://picsum.photos/seed/fe-phone-15pro/500/500', 23990000, 1, 0, null, now() - interval '1 hour'), " +
            "(900102, 900102, 'FE-SKU-PHONE-15PRO',   '90000000-0000-4000-9001-000000000101', 'FE Phone Pro Camera Kit',   'https://picsum.photos/seed/fe-phone-15pro/500/500', 23990000, 1, 0, null, now() - interval '1 day'), " +
            "(900103, 900103, 'FE-SKU-AIRPODS-COMBO', '90000000-0000-4000-9001-000000000103', 'FE AirPods Flash Combo',    'https://picsum.photos/seed/fe-airpods-combo/500/500', 4990000, 1, 0, 900001, now() - interval '2 days'), " +
            "(900104, 900104, 'FE-SKU-HUB-8IN1',      '90000000-0000-4000-9001-000000000104', 'FE USB-C Hub 8-in-1',       'https://picsum.photos/seed/fe-hub-8in1/500/500', 790000, 1, 0, null, now() - interval '4 days'), " +
            "(900105, 900105, 'FE-SKU-HUB-8IN1',      '90000000-0000-4000-9001-000000000104', 'FE USB-C Hub 8-in-1',       'https://picsum.photos/seed/fe-hub-8in1/500/500', 790000, 1, 0, null, now() - interval '5 days'), " +
            "(900106, 900106, 'FE-SKU-AIRPODS-COMBO', '90000000-0000-4000-9001-000000000103', 'FE AirPods Flash Combo',    'https://picsum.photos/seed/fe-airpods-combo/500/500', 4990000, 1, 1, null, now() - interval '6 days'), " +
            "(900107, 900107, 'FE-SKU-AIRPODS-COMBO', '90000000-0000-4000-9001-000000000103', 'FE AirPods Flash Combo',    'https://picsum.photos/seed/fe-airpods-combo/500/500', 4990000, 1, 1, null, now() - interval '7 days'), " +
            "(900108, 900108, 'FE-SKU-LAPTOP-M3',     '90000000-0000-4000-9001-000000000102', 'FE MacBook Air M3 Demo',    'https://picsum.photos/seed/fe-laptop-m3/500/500', 27990000, 1, 1, null, now() - interval '8 days'), " +
            "(900109, 900109, 'FE-SKU-LAPTOP-M3',     '90000000-0000-4000-9001-000000000102', 'FE MacBook Air M3 Demo',    'https://picsum.photos/seed/fe-laptop-m3/500/500', 27990000, 1, 0, null, now() - interval '20 days'), " +
            // 900110: 2 items — T-Shirt (size S) + USB-C Hub 8-in-1
            "(9001101, 900110, 'FE-SKU-TSHIRT-S',     '90000000-0000-4000-9001-000000000111', 'FE Summer T-Shirt',         'https://picsum.photos/seed/fe-tshirt-s/500/500', 149000, 1, 0, null, now() - interval '3 days'), " +
            "(9001102, 900110, 'FE-SKU-HUB-8IN1',     '90000000-0000-4000-9001-000000000104', 'FE USB-C Hub 8-in-1',       'https://picsum.photos/seed/fe-hub-8in1/500/500', 790000, 1, 0, null, now() - interval '3 days'), " +
            // 900111: 2 qty of FE-SKU-PHONE-15PRO
            "(9001111, 900111, 'FE-SKU-PHONE-15PRO',  '90000000-0000-4000-9001-000000000101', 'FE Phone Pro Camera Kit',   'https://picsum.photos/seed/fe-phone-15pro/500/500', 23990000, 2, 0, null, now() - interval '10 days'), " +
            // 900112: 2 items — Bluetooth Earbuds Pro + Wireless Charger Pad
            "(9001121, 900112, 'FE-SKU-EARBUDS-PRO',  '90000000-0000-4000-9001-000000000203', 'FE Bluetooth Earbuds Pro',  'https://picsum.photos/seed/fe-earbuds-pro/500/500', 1590000, 1, 0, null, now() - interval '12 hours'), " +
            "(9001122, 900112, 'FE-SKU-CHARGER-PAD',  '90000000-0000-4000-9001-000000000115', 'FE Wireless Charger Pad',   'https://picsum.photos/seed/fe-charger-pad/500/500', 450000,  1, 0, null, now() - interval '12 hours'), " +
            // 900113: 1 item — FE Yoga Mat Premium
            "(900113,  900113, 'FE-SKU-YOGA-MAT',     '90000000-0000-4000-9001-000000000201', 'FE Yoga Mat Premium',       'https://picsum.photos/seed/fe-yoga-mat/500/500', 350000,  1, 0, null, now() - interval '2 days'), " +
            // 900114: 1 item — FE Travel Backpack
            "(900114,  900114, 'FE-SKU-TRAVEL-BACKPACK', '90000000-0000-4000-9001-000000000202', 'FE Travel Backpack',      'https://picsum.photos/seed/fe-travel-backpack/500/500', 890000, 1, 0, null, now() - interval '1 day') " +
            "ON CONFLICT (id) DO UPDATE SET order_id=EXCLUDED.order_id,sku_code=EXCLUDED.sku_code,variant_id=EXCLUDED.variant_id,name_snapshot=EXCLUDED.name_snapshot,image_snapshot=EXCLUDED.image_snapshot,price_snapshot=EXCLUDED.price_snapshot,quantity=EXCLUDED.quantity,refunded_quantity=EXCLUDED.refunded_quantity,fs_item_id=EXCLUDED.fs_item_id")
            .executeUpdate();

        // Reset sequences to highest ID across all seeded data
        entityManager.createNativeQuery("SELECT setval('orders.parent_orders_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM orders.parent_orders), 900114))")
            .getSingleResult();
        entityManager.createNativeQuery("SELECT setval('orders.orders_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM orders.orders), 900114))")
            .getSingleResult();
        entityManager.createNativeQuery("SELECT setval('orders.order_items_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM orders.order_items), 900114))")
            .getSingleResult();

        log.info("[OrderDevDataLoader] FE test-dataset seeded (14 parent_orders, 14 orders, 16 order_items).");
    }

    /**
     * Seeds orders from {@code test-data/orders.json}.
     * Each entry becomes one parent_order + one or more sub-orders + order_items.
     * Relative timestamps (e.g. "7 days ago") are converted to SQL now() - interval expressions.
     * Idempotent via ON CONFLICT DO UPDATE.
     */
    private void seedFromJsonDataset() {
        List<Map<String, Object>> orders;
        try (InputStream is = new ClassPathResource(JSON_PATH).getInputStream()) {
            orders = new ObjectMapper().readValue(is, new TypeReference<List<Map<String, Object>>>() {});
        } catch (IOException e) {
            log.warn("[OrderDevDataLoader] Could not read {} — skipping JSON seed: {}", JSON_PATH, e.getMessage());
            return;
        }

        log.info("[OrderDevDataLoader] Seeding {} orders from {}", orders.size(), JSON_PATH);

        // Check if JSON data already exists (by first parentOrderId)
        Map<String, Object> first = orders.getFirst();
        int firstId = ((Number) first.get("parentOrderId")).intValue();
        Long count = (Long) entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM orders.parent_orders WHERE id = ?")
            .setParameter(1, firstId).getSingleResult();
        if (count != null && count > 0) {
            log.info("[OrderDevDataLoader] JSON dataset already seeded, skipping.");
            return;
        }

        int maxId = 0;
        int itemSeq = 9001001;

        for (Map<String, Object> order : orders) {
            int parentId = ((Number) order.get("parentOrderId")).intValue();
            int customerId = ((Number) order.get("customerId")).intValue();
            double totalAmt = ((Number) order.get("totalAmt")).doubleValue();
            String createdAtOffset = (String) order.get("createdAtOffset");
            maxId = Math.max(maxId, parentId);
            String createdSql = toInterval(createdAtOffset); // e.g. "now() - interval '7 days'"

            // Parent order — inline createdSql (cannot be a bind param, must be raw SQL)
            String parentSql = String.format("""
                INSERT INTO orders.parent_orders (id, customer_id, session_id, total_amt, final_amt, created_at, updated_at)
                VALUES (%d, %d, 'json-dataset', %.2f, %.2f, %s, now())
                ON CONFLICT (id) DO UPDATE SET
                    customer_id = EXCLUDED.customer_id, total_amt = EXCLUDED.total_amt,
                    final_amt = EXCLUDED.final_amt, updated_at = now()
                """, parentId, customerId, totalAmt, totalAmt, createdSql);
            entityManager.createNativeQuery(parentSql).executeUpdate();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> subOrders = (List<Map<String, Object>>) order.get("subOrders");
            if (subOrders == null) continue;

            for (Map<String, Object> sub : subOrders) {
                int orderId = ((Number) sub.get("orderId")).intValue();
                int sellerId = ((Number) sub.get("sellerId")).intValue();
                String orderCode = (String) sub.get("orderCode");
                String subStatus = (String) sub.get("status");
                String tracking = sub.get("trackingNumber") != null ? (String) sub.get("trackingNumber") : "";
                double subTotal = ((Number) sub.get("totalAmt")).doubleValue();
                maxId = Math.max(maxId, orderId);

                String orderSql = String.format("""
                    INSERT INTO orders.orders (id, parent_order_id, seller_id, order_code, customer_id, total_amt, final_amt, status, cancelled_by, cancel_reason, is_flash_sale, shipping_address, tracking_number, shipping_deadline, delivered_at, version, created_at, updated_at)
                    VALUES (%d, %d, %d, '%s', %d, %.2f, %.2f, '%s', null, null, false, '{}'::jsonb, '%s', now() + interval '3 days', null, 0, %s, now())
                    ON CONFLICT (id) DO UPDATE SET
                        parent_order_id = EXCLUDED.parent_order_id, seller_id = EXCLUDED.seller_id,
                        order_code = EXCLUDED.order_code, total_amt = EXCLUDED.total_amt,
                        final_amt = EXCLUDED.final_amt, status = EXCLUDED.status,
                        tracking_number = EXCLUDED.tracking_number, updated_at = now()
                    """, orderId, parentId, sellerId, escapeSql(orderCode), customerId,
                    subTotal, subTotal, escapeSql(subStatus), escapeSql(tracking), createdSql);
                entityManager.createNativeQuery(orderSql).executeUpdate();

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) sub.get("items");
                if (items == null) continue;

                for (Map<String, Object> item : items) {
                    String sku = (String) item.get("sku");
                    String name = (String) item.get("name");
                    double price = ((Number) item.get("price")).doubleValue();
                    int quantity = ((Number) item.get("quantity")).intValue();

                    String itemSql = String.format("""
                        INSERT INTO orders.order_items (id, order_id, sku_code, variant_id, name_snapshot, image_snapshot, price_snapshot, quantity, refunded_quantity, fs_item_id, created_at)
                        VALUES (%d, %d, '%s', 'json-dataset', '%s', 'https://picsum.photos/seed/%s/500/500', %.2f, %d, 0, null, %s)
                        ON CONFLICT (id) DO UPDATE SET
                            order_id = EXCLUDED.order_id, sku_code = EXCLUDED.sku_code,
                            name_snapshot = EXCLUDED.name_snapshot, price_snapshot = EXCLUDED.price_snapshot,
                            quantity = EXCLUDED.quantity
                        """, itemSeq, orderId, escapeSql(sku), escapeSql(name),
                        escapeSql(sku), price, quantity, createdSql);
                    entityManager.createNativeQuery(itemSql).executeUpdate();

                    maxId = Math.max(maxId, itemSeq);
                    itemSeq++;
                }
            }
        }

        // Reset sequences
        entityManager.createNativeQuery(
            "SELECT setval('orders.parent_orders_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM orders.parent_orders), " + Math.max(maxId, 900114) + "))")
            .getSingleResult();
        entityManager.createNativeQuery(
            "SELECT setval('orders.orders_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM orders.orders), " + Math.max(maxId, 900114) + "))")
            .getSingleResult();
        entityManager.createNativeQuery(
            "SELECT setval('orders.order_items_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM orders.order_items), " + Math.max(maxId, 900114) + "))")
            .getSingleResult();

        log.info("[OrderDevDataLoader] JSON dataset seeded ({} parent orders).", orders.size());
    }

    /** Converts a relative time string (e.g. "7 days ago", "2 hours ago") to SQL now() - interval. */
    private static String toInterval(String offset) {
        if (offset == null || offset.isBlank()) return "now()";
        String[] parts = offset.split(" ");
        if (parts.length < 3) return "now()";
        return "now() - interval '" + parts[0] + " " + parts[1] + "'";
    }

    /** Minimal SQL string escaping (single quotes). */
    private static String escapeSql(String s) {
        return s != null ? s.replace("'", "''") : "";
    }
}
