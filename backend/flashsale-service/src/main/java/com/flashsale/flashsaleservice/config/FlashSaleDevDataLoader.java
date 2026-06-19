package com.flashsale.flashsaleservice.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.config.DevDataProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Seeds flash-sale sessions, items, and reminders for local dev.
 *
 * <p>flashsale-service is R2DBC reactive — we use blocking {@code .block()} since
 * this only runs once at startup. All data references FE product catalog entities
 * (seller=900002, FE-SKU-* variant codes).</p>
 *
 * <p>Three FE sessions:</p>
 * <ul>
 *   <li>900001 — <b>FE Live Flash Sale</b> (LIVE, started 30 min ago, ends in 2h)</li>
 *   <li>900002 — <b>FE Upcoming Weekend Sale</b> (UPCOMING, starts in 1 day, 6h duration)</li>
 *   <li>900003 — <b>FE Ended Morning Sale</b> (ENDED, ended 2 days ago)</li>
 * </ul>
 *
 * <p>All inserts use {@code ON CONFLICT DO UPDATE} for idempotency.</p>
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "dev-data.enabled", havingValue = "true", matchIfMissing = false)
public class FlashSaleDevDataLoader implements CommandLineRunner {

    private final DevDataProperties devDataProperties;
    private final DatabaseClient databaseClient;

    private static final String JSON_PATH = "test-data/flashsales.json";

    @Override
    public void run(String... args) {
        log.info("[FlashSaleDevDataLoader] Starting dev data seed for flashsale-service...");

        if (devDataProperties.isReset()) {
            log.warn("[FlashSaleDevDataLoader] RESET=true — wiping flashsale data...");
            databaseClient.sql("DELETE FROM fs_reminders").then().block();
            databaseClient.sql("DELETE FROM fs_items").then().block();
            databaseClient.sql("DELETE FROM fs_sessions").then().block();
            log.info("[FlashSaleDevDataLoader] All flashsale data wiped.");
        }

        seedFeData();
        seedFromJsonDataset();

        log.info("[FlashSaleDevDataLoader] Dev data seed complete.");
    }

    private void seedFeData() {
        log.info("[FlashSaleDevDataLoader] Seeding FE test-dataset...");

        Long count = databaseClient.sql("SELECT COUNT(*) FROM fs_sessions WHERE id = 900001")
            .map((row, meta) -> row.get(0, Long.class)).first().block();
        if (count != null && count > 0) {
            log.info("[FlashSaleDevDataLoader] FE data already exists, skipping.");
            return;
        }

        databaseClient.sql("""
            INSERT INTO fs_sessions (id, name, start_time, end_time, registration_deadline, status, deleted_at, created_at, updated_at) VALUES
            (900001, 'FE Live Flash Sale', now() - interval '30 minutes', now() + interval '2 hours', now() - interval '1 hour', 'LIVE', null, now() - interval '1 day', now()),
            (900002, 'FE Upcoming Weekend Sale', now() + interval '1 day', now() + interval '1 day 6 hours', now() + interval '1 day', 'UPCOMING', null, now() - interval '1 day', now()),
            (900003, 'FE Ended Morning Sale', now() - interval '2 days', now() - interval '1 day 20 hours', now() - interval '2 days', 'ENDED', null, now() - interval '3 days', now())
            ON CONFLICT (id) DO UPDATE SET
                name=EXCLUDED.name, start_time=EXCLUDED.start_time, end_time=EXCLUDED.end_time,
                registration_deadline=EXCLUDED.registration_deadline, status=EXCLUDED.status,
                deleted_at=EXCLUDED.deleted_at, updated_at=now()
            """).then().block();

        databaseClient.sql("""
            INSERT INTO fs_items (id, session_id, seller_id, sku_code, flash_price, flash_stock, limit_per_user, sold_qty, status, version, created_at, updated_at) VALUES
            (900001, 900001, 900002, 'FE-SKU-AIRPODS-COMBO', 3990000, 30, 2, 6, 'LIVE', 0, now() - interval '1 day', now()),
            (900002, 900001, 900002, 'FE-SKU-PHONE-15PRO', 21990000, 10, 1, 2, 'LIVE', 0, now() - interval '1 day', now()),
            (900003, 900002, 900002, 'FE-SKU-LAPTOP-M3', 25990000, 8, 1, 0, 'APPROVED', 0, now() - interval '1 day', now()),
            (900004, 900002, 900002, 'FE-SKU-HUB-8IN1', 590000, 100, 3, 0, 'APPROVED', 0, now() - interval '1 day', now()),
            (900005, 900003, 900002, 'FE-SKU-AIRPODS-COMBO', 3790000, 20, 2, 20, 'SOLD_OUT', 0, now() - interval '3 days', now()),
            (900006, 900001, 900002, 'FE-SKU-EARBUDS-PRO', 1290000, 25, 2, 8, 'LIVE', 0, now() - interval '1 day', now()),
            (900007, 900002, 900002, 'FE-SKU-CHARGER-PAD', 390000, 50, 3, 0, 'APPROVED', 0, now() - interval '1 day', now()),
            (900008, 900003, 900002, 'FE-SKU-PHONE-15PRO', 19990000, 8, 1, 8, 'SOLD_OUT', 0, now() - interval '3 days', now()),
            (900009, 900001, 900002, 'FE-SKU-YOGA-MAT', 250000, 30, 2, 12, 'LIVE', 0, now() - interval '1 day', now()),
            (900010, 900002, 900002, 'FE-SKU-TRAVEL-BACKPACK', 750000, 20, 1, 0, 'APPROVED', 0, now() - interval '1 day', now()),
            -- Session 900001 (+6 → 10 items)
            (900011, 900001, 900002, 'FE-SKU-AIRPODS-LIGHTNING', 3190000, 22, 2, 4, 'LIVE', 0, now() - interval '1 day', now()),
            (900012, 900001, 900002, 'FE-SKU-HUB-BLACK', 550000, 60, 3, 10, 'LIVE', 0, now() - interval '1 day', now()),
            (900013, 900001, 900002, 'FE-SKU-TSHIRT-M', 99000, 40, 3, 15, 'LIVE', 0, now() - interval '1 day', now()),
            (900014, 900001, 900002, 'FE-SKU-CHARGER-BLACK', 290000, 35, 2, 7, 'LIVE', 0, now() - interval '1 day', now()),
            (900015, 900001, 900002, 'FE-SKU-VACUUM-PRO', 3990000, 12, 1, 1, 'LIVE', 0, now() - interval '1 day', now()),
            (900016, 900001, 900002, 'FE-SKU-EARBUDS-WHITE', 1190000, 28, 2, 5, 'LIVE', 0, now() - interval '1 day', now()),
            -- Session 900002 (+6 → 10 items)
            (900017, 900002, 900002, 'FE-SKU-PHONE-SILVER', 24990000, 6, 1, 0, 'APPROVED', 0, now() - interval '1 day', now()),
            (900018, 900002, 900002, 'FE-SKU-AIRPODS-PROMAX', 5490000, 18, 2, 0, 'APPROVED', 0, now() - interval '1 day', now()),
            (900019, 900002, 900002, 'FE-SKU-TSHIRT-L', 99000, 30, 2, 0, 'APPROVED', 0, now() - interval '1 day', now()),
            (900020, 900002, 900002, 'FE-SKU-YOGA-BLUE', 280000, 40, 3, 0, 'APPROVED', 0, now() - interval '1 day', now()),
            (900021, 900002, 900002, 'FE-SKU-DESK-OAK', 1690000, 10, 1, 0, 'APPROVED', 0, now() - interval '1 day', now()),
            (900022, 900002, 900002, 'FE-SKU-LAMP-COOL', 350000, 22, 2, 0, 'APPROVED', 0, now() - interval '1 day', now()),
            -- Session 900003 (+8 → 10 items)
            (900023, 900003, 900002, 'FE-SKU-LAPTOP-SILVER', 22990000, 4, 1, 4, 'SOLD_OUT', 0, now() - interval '3 days', now()),
            (900024, 900003, 900002, 'FE-SKU-HUB-WHITE', 790000, 40, 3, 40, 'SOLD_OUT', 0, now() - interval '3 days', now()),
            (900025, 900003, 900002, 'FE-SKU-TSHIRT-S', 99000, 25, 3, 25, 'SOLD_OUT', 0, now() - interval '3 days', now()),
            (900026, 900003, 900002, 'FE-SKU-YOGA-GREEN', 350000, 20, 2, 20, 'SOLD_OUT', 0, now() - interval '3 days', now()),
            (900027, 900003, 900002, 'FE-SKU-CHARGER-BLUE', 390000, 25, 2, 25, 'SOLD_OUT', 0, now() - interval '3 days', now()),
            (900028, 900003, 900002, 'FE-SKU-EARBUDS-RED', 790000, 20, 2, 20, 'SOLD_OUT', 0, now() - interval '3 days', now()),
            (900029, 900003, 900002, 'FE-SKU-BAG-BLACK', 490000, 18, 2, 18, 'SOLD_OUT', 0, now() - interval '3 days', now()),
            (900030, 900003, 900002, 'FE-SKU-DESK-WALNUT', 1790000, 6, 1, 6, 'SOLD_OUT', 0, now() - interval '3 days', now())
            ON CONFLICT (id) DO UPDATE SET
                session_id=EXCLUDED.session_id, seller_id=EXCLUDED.seller_id, sku_code=EXCLUDED.sku_code,
                flash_price=EXCLUDED.flash_price, flash_stock=EXCLUDED.flash_stock,
                limit_per_user=EXCLUDED.limit_per_user, sold_qty=EXCLUDED.sold_qty,
                status=EXCLUDED.status, version=EXCLUDED.version, updated_at=now()
            """).then().block();

        databaseClient.sql("""
            INSERT INTO fs_reminders (id, customer_id, session_id, created_at) VALUES
            (900001, 900001, 900002, now() - interval '12 hours'),
            (900002, 900001, 900001, now() - interval '2 hours'),
            (900003, 900001, 900003, now() - interval '3 days'),
            (900004, 900001, 900002, now() - interval '1 day'),
            (900005, 900002, 900001, now() - interval '4 hours'),
            (900006, 900001, 900001, now() - interval '30 minutes')
            ON CONFLICT (customer_id, session_id) DO NOTHING
            """).then().block();

        log.info("[FlashSaleDevDataLoader] FE test-dataset seeded (3 sessions, 30 items, 6 reminders).");
    }

    /**
     * Seeds flash-sales from {@code test-data/flashsales.json}.
     * Uses raw SQL string concatenation for time expressions (R2DBC bind params
     * treat values as literals, so now() - interval cannot be bound).
     * Only predefined status-based time expressions are constructed.
     * Idempotent via ON CONFLICT DO UPDATE.
     */
    private void seedFromJsonDataset() {
        List<Map<String, Object>> sessions;
        try (InputStream is = new ClassPathResource(JSON_PATH).getInputStream()) {
            sessions = new ObjectMapper().readValue(is, new TypeReference<List<Map<String, Object>>>() {});
        } catch (IOException e) {
            log.warn("[FlashSaleDevDataLoader] Could not read {} — skipping JSON seed: {}", JSON_PATH, e.getMessage());
            return;
        }

        log.info("[FlashSaleDevDataLoader] Seeding {} flash-sales from {}", sessions.size(), JSON_PATH);

        // Check if JSON data already exists
        Map<String, Object> first = sessions.getFirst();
        int firstId = ((Number) first.get("sessionId")).intValue();
        Long count = databaseClient.sql("SELECT COUNT(*) FROM fs_sessions WHERE id = " + firstId)
            .map((row, meta) -> row.get(0, Long.class)).first().block();
        if (count != null && count > 0) {
            log.info("[FlashSaleDevDataLoader] JSON dataset already seeded, skipping.");
            return;
        }

        int maxId = 0;
        int itemSeq = 900031;
        int reminderSeq = 900007;

        for (Map<String, Object> session : sessions) {
            int sessionId = ((Number) session.get("sessionId")).intValue();
            String name = (String) session.get("name");
            String status = (String) session.get("status");
            maxId = Math.max(maxId, sessionId);

            // Build time expressions based on status (same pattern as seedFeData)
            String startTime, endTime, regDeadline;
            startTime = switch (status != null ? status : "") {
                case "LIVE" -> "now() - interval '30 minutes'";
                case "UPCOMING" -> "now() + interval '1 day'";
                case "ENDED" -> "now() - interval '2 days'";
                default -> "now()";
            };
            endTime = switch (status != null ? status : "") {
                case "LIVE" -> "now() + interval '2 hours'";
                case "UPCOMING" -> "now() + interval '1 day 6 hours'";
                case "ENDED" -> "now() - interval '1 day 20 hours'";
                default -> "now() + interval '1 hour'";
            };
            regDeadline = switch (status != null ? status : "") {
                case "LIVE" -> "now() - interval '1 hour'";
                case "UPCOMING" -> "now() + interval '1 day'";
                case "ENDED" -> "now() - interval '2 days'";
                default -> "now()";
            };

            // Insert session — inline SQL for time expressions (R2DBC limitation)
            databaseClient.sql(String.format("""
                INSERT INTO fs_sessions (id, name, start_time, end_time, registration_deadline, status, deleted_at, created_at, updated_at) VALUES
                (%d, '%s', %s, %s, %s, '%s', null, now(), now())
                ON CONFLICT (id) DO UPDATE SET
                    name=EXCLUDED.name, status=EXCLUDED.status, updated_at=now()
                """, sessionId, escapeSql(name), startTime, endTime, regDeadline, status))
                .then().block();

            // Items
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) session.get("items");
            if (items != null) {
                for (Map<String, Object> item : items) {
                    String skuCode = (String) item.get("skuCode");
                    double flashPrice = ((Number) item.get("flashPrice")).doubleValue();
                    int flashStock = ((Number) item.get("flashStock")).intValue();
                    int soldQty = ((Number) item.get("soldQty")).intValue();
                    int limitPerUser = ((Number) item.get("limitPerUser")).intValue();
                    String itemStatus = (String) item.get("status");

                    databaseClient.sql(String.format("""
                        INSERT INTO fs_items (id, session_id, seller_id, sku_code, flash_price, flash_stock, limit_per_user, sold_qty, status, version, created_at, updated_at) VALUES
                        (%d, %d, 1, '%s', %.2f, %d, %d, %d, '%s', 0, now(), now())
                        ON CONFLICT (id) DO UPDATE SET
                            session_id=EXCLUDED.session_id, sku_code=EXCLUDED.sku_code,
                            flash_price=EXCLUDED.flash_price, flash_stock=EXCLUDED.flash_stock,
                            sold_qty=EXCLUDED.sold_qty, status=EXCLUDED.status, updated_at=now()
                        """, itemSeq, sessionId, escapeSql(skuCode), flashPrice,
                        flashStock, limitPerUser, soldQty, itemStatus != null ? itemStatus : "APPROVED"))
                        .then().block();
                    maxId = Math.max(maxId, itemSeq);
                    itemSeq++;
                }
            }

            // Subscribed reminders
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> reminders = (List<Map<String, Object>>) session.get("subscribedReminders");
            if (reminders != null) {
                for (Map<String, Object> rem : reminders) {
                    int customerId = ((Number) rem.get("customerId")).intValue();

                    databaseClient.sql(String.format("""
                        INSERT INTO fs_reminders (id, customer_id, session_id, created_at) VALUES
                        (%d, %d, %d, now())
                        ON CONFLICT (customer_id, session_id) DO NOTHING
                        """, reminderSeq, customerId, sessionId))
                        .then().block();
                    maxId = Math.max(maxId, reminderSeq);
                    reminderSeq++;
                }
            }
        }

        // Reset sequence
        databaseClient.sql("SELECT setval('fs_sessions_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM fs_sessions), " + maxId + "))")
            .then().block();

        log.info("[FlashSaleDevDataLoader] JSON dataset seeded ({} sessions).", sessions.size());
    }

    /** Minimal SQL string escaping (single quotes). */
    private static String escapeSql(String s) {
        return s != null ? s.replace("'", "''") : "";
    }
}
