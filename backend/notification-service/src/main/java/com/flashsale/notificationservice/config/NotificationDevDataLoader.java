package com.flashsale.notificationservice.config;

import com.flashsale.commonlib.config.DevDataProperties;
import com.flashsale.notificationservice.domain.model.Notification;
import com.flashsale.notificationservice.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds notifications across the FE test-dataset accounts.
 *
 * <p>Only targets {@code fe_buyer} (900001) and {@code fe_seller} (900002)
 * so the frontend always has realistic data to render every notification
 * template variant.</p>
 *
 * <p>Covers all major notification {@code type} values from the catalog.
 * Mix of READ / UNREAD, multiple priorities ({@code NORMAL}, {@code HIGH},
 * {@code URGENT}), and realistic Vietnamese-language messages referencing
 * FE products and orders.</p>
 *
 * <p>NotificationRepository is a ReactiveMongoRepository — we use
 * {@code .block()} since this runs once at startup.</p>
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "dev-data.enabled", havingValue = "true", matchIfMissing = false)
public class NotificationDevDataLoader implements CommandLineRunner {

    private final NotificationRepository notificationRepository;
    private final DevDataProperties devDataProperties;

    @Override
    public void run(String... args) {
        log.info("[NotificationDevDataLoader] Starting dev data seed for notification-service...");

        if (devDataProperties.isReset()) {
            log.warn("[NotificationDevDataLoader] RESET=true — wiping notifications...");
            notificationRepository.deleteAll().block();
            log.info("[NotificationDevDataLoader] All notifications wiped.");
        } else {
            Long count = notificationRepository.count().block();
            if (count != null && count > 0) {
                log.info("[NotificationDevDataLoader] Data already exists, skipping. Set dev-data.reset=true to reload.");
                return;
            }
        }

        LocalDateTime now = LocalDateTime.now();
        List<Notification> all = new ArrayList<>();

        // ======================================================================
        //  fe_buyer (900001) — 8 notifications
        // ======================================================================

        all.add(notif(900001, "ORDER_CREATED", "Don hang da duoc tao",
                "Don hang FE-ORD-PENDING-900101 (23.990.000d) da duoc ghi nhan. Vui long thanh toan trong 30 phut.",
                "{\"order_id\":900101,\"deeplink\":\"/orders/900101\"}", "NORMAL", true, now.minusHours(1)));

        all.add(notif(900001, "ORDER_PAID", "Thanh toan thanh cong",
                "Don hang FE-ORD-PAID-900102 da thanh toan thanh cong 23.990.000d.",
                "{\"order_id\":900102,\"amount\":23990000}", "NORMAL", true, now.minusDays(1).plusMinutes(10)));

        all.add(notif(900001, "ORDER_SHIPPED", "Don hang dang van chuyen",
                "FE-ORD-SHIPPING-900103 (FE AirPods Flash Combo) da duoc giao cho FE-GHN-900103.",
                "{\"order_id\":900103,\"tracking\":\"FE-GHN-900103\"}", "NORMAL", false, now.minusDays(2)));

        all.add(notif(900001, "ORDER_DELIVERED", "Don hang da duoc giao",
                "FE-ORD-DELIVERED-900104 (FE USB-C Hub 8-in-1) da giao thanh cong. Danh gia ngay!",
                "{\"order_id\":900104}", "HIGH", false, now.minusDays(2)));

        all.add(notif(900001, "REFUND_REQUESTED", "Yeu cau hoan tien dang duoc xem xet",
                "Yeu cau hoan 1.990.000d cho FE-ORD-PARTIAL-REFUND-900106 da duoc gui toi shop.",
                "{\"refund_id\":900201,\"order_id\":900106}", "NORMAL", false, now.minusHours(6)));

        all.add(notif(900001, "REFUND_APPROVED", "Hoan tien thanh cong",
                "Da hoan 4.990.000d cho FE-ORD-REFUNDED-900107. Tien se ve tai khoan trong 3-5 ngay lam viec.",
                "{\"refund_id\":900202,\"order_id\":900107,\"amount\":4990000}", "HIGH", true, now.minusDays(3)));

        all.add(notif(900001, "REFUND_REJECTED", "Yeu cau hoan tien bi tu choi",
                "Yeu cau hoan 390.000d cho don hang 900203 da bi tu choi: bang chung khong cho thay loi tu nguoi ban.",
                "{\"refund_id\":900203,\"order_id\":900104}", "HIGH", false, now.minusDays(1)));

        all.add(notif(900001, "PAYMENT_FAILED", "Thanh toan khong thanh cong",
                "Thanh toan don hang FE-ORD-PENDING-900101 that bai. Vui long thu lai trong 24h.",
                "{\"order_id\":900101}", "URGENT", false, now.minusMinutes(30)));

        all.add(notif(900001, "FLASH_SALE_STARTING", "Flash Sale sap bat dau!",
                "FE AirPods Flash Combo gia chi 4.990.000d — Flash Sale bat dau sau 1 tieng!",
                "{\"session_id\":900001,\"deeplink\":\"/flash-sale/900001\"}", "URGENT", false, now.minusMinutes(50)));

        // ======================================================================
        //  fe_seller (900002) — 6 notifications
        // ======================================================================

        all.add(notif(900002, "ORDER_CREATED", "Co don hang moi",
                "Khach hang da dat FE Phone Pro Camera Kit (23.990.000d). Vui long chuan bi hang.",
                "{\"order_id\":900102,\"deeplink\":\"/seller/orders/900102\"}", "NORMAL", true, now.minusDays(1)));

        all.add(notif(900002, "ORDER_DELIVERED", "Don hang da duoc giao",
                "FE-ORD-DELIVERED-900104 da duoc giao thanh cong. Doanh thu 790.000d se duoc chuyen sau 7 ngay.",
                "{\"order_id\":900104}", "NORMAL", false, now.minusDays(2)));

        all.add(notif(900002, "PRODUCT_APPROVED", "San pham da duoc duyet",
                "FE Approved Robot Vacuum da duoc admin phe duyet. Dang nhap de xuat ban ngay.",
                "{\"product_slug\":\"fe-approved-robot-vacuum\",\"deeplink\":\"/seller/products\"}", "NORMAL", true, now.minusDays(2)));

        all.add(notif(900002, "PRODUCT_REJECTED", "San pham bi tu choi",
                "FE Rejected Sample Bag bi tu choi: thieu hinh anh that va thong tin bao hanh. Vui long cap nhat va gui lai.",
                "{\"product_slug\":\"fe-rejected-sample-bag\",\"reason\":\"Missing real product images and warranty details.\"}",
                "HIGH", false, now.minusDays(3)));

        all.add(notif(900002, "TRANSFER_PAID_OUT", "Da chuyen khoan doanh thu",
                "27.990.000d da duoc chuyen vao tai khoan Stripe cua ban cho don FE-ORD-PAIDOUT-900109.",
                "{\"transfer_id\":\"tr_fe_900109\",\"amount\":27990000,\"order_id\":900109}", "URGENT", false, now.minusDays(12)));

        all.add(notif(900002, "SYSTEM", "Bao tri he thong",
                "He thong se bao tri dinh ky vao 02:00-04:00 ngay mai. Chuc nang ban hang tam ngung trong thoi gian nay.",
                "{\"announcement_id\":\"SYS-2026-001\"}", "HIGH", true, now.minusDays(5)));

        all.add(notif(900002, "ORDER_CREATED", "Don hang moi: FE Yoga Mat Premium",
                "Khach hang da dat FE Yoga Mat Premium (350.000d). Vui long chuan bi hang.",
                "{\"order_id\":900113,\"deeplink\":\"/seller/orders/900113\"}", "NORMAL", false, now.minusDays(2)));

        // ======================================================================
        //  fe_admin (900003) — 4 notifications
        // ======================================================================

        all.add(notif(900003, "PRODUCT_PENDING", "San pham cho phe duyet",
                "FE Pending Review Backpack can admin xem xet va phe duyet.",
                "{\"product_slug\":\"fe-pending-review-backpack\",\"deeplink\":\"/admin/products/pending\"}",
                "NORMAL", false, now.minusDays(2)));

        all.add(notif(900003, "PRODUCT_APPROVED", "Da phe duyet san pham",
                "FE AirPods Flash Combo da duoc admin phe duyet thanh cong.",
                "{\"product_slug\":\"fe-airpods-flash-combo\"}", "NORMAL", true, now.minusDays(12)));

        all.add(notif(900003, "SYSTEM", "Yeu cau hoan tien can xem xet",
                "Khach hang yeu cau hoan 23.990.000d cho REFUND-900205. Vao phan Refund de xu ly.",
                "{\"refund_id\":900205,\"deeplink\":\"/admin/refunds/900205\"}", "HIGH", false, now.minusHours(3)));

        all.add(notif(900003, "SYSTEM", "Bao cao thong ke hang thang",
                "Bao cao doanh thu thang 5 da san sang. Xem tai dashboard.",
                "{\"report_id\":\"REPORT-2026-05\",\"deeplink\":\"/admin/reports\"}", "NORMAL", true, now.minusDays(2)));

        // ======================================================================
        //  fe_buyer (900001) — 1 extra: Yoga Mat delivered
        // ======================================================================

        all.add(notif(900001, "ORDER_DELIVERED", "FE Yoga Mat Premium da duoc giao",
                "FE-ORD-DELIVERED-900113 (FE Yoga Mat Premium) da giao thanh cong. Danh gia ngay!",
                "{\"order_id\":900113}", "HIGH", false, now.minusDays(1)));

        // ======================================================================
        //  Save all
        // ======================================================================

        Long inserted = notificationRepository.saveAll(all).count().block();
        log.info("[NotificationDevDataLoader] Seeded {} notifications.", inserted);
    }

    private Notification notif(long userId, String type, String title, String body,
                                String metadata, String priority, boolean isRead,
                                LocalDateTime createdAt) {
        return Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .body(body)
                .metadata(metadata)
                .priority(priority)
                .isRead(isRead)
                .readAt(isRead ? createdAt.plusMinutes(10) : null)
                .createdAt(createdAt)
                .build();
    }
}
