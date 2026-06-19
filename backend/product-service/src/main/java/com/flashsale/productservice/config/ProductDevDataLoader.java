package com.flashsale.productservice.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.config.DevDataProperties;
import com.flashsale.productservice.entity.*;
import com.flashsale.productservice.repository.*;
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
import java.util.*;

/**
 * Seeds the FE test-dataset (categories, products, variants, images, wishlist, cart)
 * for frontend E2E and integration testing.
 *
 * <p>All products belong to seller 900002 (fe_seller). All admin actions
 * (reviewed_by) reference 900003 (fe_admin). The fe_buyer (900001) receives
 * wishlist and cart items at the end.</p>
 *
 * <p>Idempotent via ON CONFLICT DO UPDATE — safe to run repeatedly.</p>
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "dev-data.enabled", havingValue = "true", matchIfMissing = false)
public class ProductDevDataLoader implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final StockReservationRepository stockReservationRepository;
    private final DevDataProperties devDataProperties;
    private final JdbcTemplate jdbcTemplate;

    private static final String JSON_PATH = "test-data/products.json";

    @Override
    @Transactional
    public void run(String... args) {
        log.info("[ProductDevDataLoader] Starting dev data seed for product-service...");

        if (devDataProperties.isReset()) {
            log.warn("[ProductDevDataLoader] RESET=true -- wiping all product data...");
            stockReservationRepository.deleteAllInBatch();
            productImageRepository.deleteAllInBatch();
            productVariantRepository.deleteAllInBatch();
            productRepository.deleteAllInBatch();
            categoryRepository.deleteAllInBatch();
            log.info("[ProductDevDataLoader] All product data wiped.");
        } else if (productRepository.count() > 0) {
            log.info("[ProductDevDataLoader] Data already exists, skipping main seed.");
            seedFeData();
            seedFromJsonDataset();
            log.info("[ProductDevDataLoader] Dev data seed complete.");
            return;
        }

        seedFeData();
        seedFromJsonDataset();

        log.info("[ProductDevDataLoader] Dev data seed complete.");
    }

    private void seedFeData() {
        log.info("[ProductDevDataLoader] Seeding FE test-dataset...");

        // Check if FE data already exists
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM product.categories WHERE id = '90000000-0000-4000-8000-000000000001'", Integer.class);
        if (count != null && count > 0) {
            log.info("[ProductDevDataLoader] FE data already exists, skipping.");
            return;
        }

        // ========================================================================
        // 1. Categories
        // ========================================================================
        jdbcTemplate.update("INSERT INTO product.categories (id, parent_id, name, slug, description, image_url, sort_order, is_active, created_at, updated_at) VALUES " +
            "('90000000-0000-4000-8000-000000000001', null, 'FE Electronics', 'fe-electronics', 'Frontend fixture electronics root category', 'https://picsum.photos/seed/fe-electronics/600/400', 10, true, now() - interval '16 days', now()), " +
            "('90000000-0000-4000-8000-000000000002', '90000000-0000-4000-8000-000000000001', 'FE Phones', 'fe-phones', 'Frontend fixture phone category', 'https://picsum.photos/seed/fe-phones/600/400', 11, true, now() - interval '16 days', now()), " +
            "('90000000-0000-4000-8000-000000000003', '90000000-0000-4000-8000-000000000001', 'FE Audio', 'fe-audio', 'Frontend fixture audio category', 'https://picsum.photos/seed/fe-audio/600/400', 12, true, now() - interval '16 days', now()), " +
            "('90000000-0000-4000-8000-000000000004', '90000000-0000-4000-8000-000000000001', 'FE Laptops', 'fe-laptops', 'Frontend fixture laptop category', 'https://picsum.photos/seed/fe-laptops/600/400', 13, true, now() - interval '16 days', now()), " +
            "('90000000-0000-4000-8000-000000000005', null, 'FE Home', 'fe-home', 'Frontend fixture home category', 'https://picsum.photos/seed/fe-home/600/400', 14, true, now() - interval '16 days', now()), " +
            "('90000000-0000-4000-8000-000000000006', null, 'FE Fashion', 'fe-fashion', 'Frontend fixture fashion category', 'https://picsum.photos/seed/fe-fashion/600/400', 15, true, now() - interval '16 days', now()) " +
            "ON CONFLICT (id) DO UPDATE SET parent_id=EXCLUDED.parent_id,name=EXCLUDED.name,slug=EXCLUDED.slug,description=EXCLUDED.description,image_url=EXCLUDED.image_url,sort_order=EXCLUDED.sort_order,is_active=EXCLUDED.is_active,updated_at=now()");

        // ========================================================================
        // 2. Products (15 total)
        // ========================================================================
        jdbcTemplate.update("INSERT INTO product.products (id, category_id, seller_id, name, slug, description, attributes, status, reject_reason, reviewed_at, reviewed_by, reject_count, submitted_at, created_at, updated_at, published_at) VALUES " +
            // --- Existing 10 (keep as-is, status changes noted) ---
            "('90000000-0000-4000-8001-000000000101', '90000000-0000-4000-8000-000000000002', 900002, 'FE Phone Pro Camera Kit', 'fe-phone-pro-camera-kit', 'Active catalog product for search, detail, cart, checkout, and flash-sale tests.', '{\"brand\":\"FE\",\"screen\":\"6.1 inch\",\"coverage\":[\"catalog\",\"search\",\"cart\",\"checkout\"]}'::jsonb, 'ACTIVE', null, now() - interval '14 days', 900003, 0, now() - interval '15 days', now() - interval '15 days', now(), now() - interval '14 days'), " +
            "('90000000-0000-4000-8001-000000000102', '90000000-0000-4000-8000-000000000004', 900002, 'FE MacBook Air M3 Demo', 'fe-macbook-air-m3-demo', 'High-value seller product for payment and payout screens.', '{\"brand\":\"FE\",\"ram\":\"16GB\",\"coverage\":[\"seller-payments\",\"search\"]}'::jsonb, 'ACTIVE', null, now() - interval '13 days', 900003, 0, now() - interval '14 days', now() - interval '14 days', now(), now() - interval '13 days'), " +
            "('90000000-0000-4000-8001-000000000103', '90000000-0000-4000-8000-000000000003', 900002, 'FE AirPods Flash Combo', 'fe-airpods-flash-combo', 'Active product with live flash-sale mapping.', '{\"brand\":\"FE\",\"noiseCancellation\":true,\"coverage\":[\"flash-sale\",\"search\"]}'::jsonb, 'ACTIVE', null, now() - interval '12 days', 900003, 0, now() - interval '13 days', now() - interval '13 days', now(), now() - interval '12 days'), " +
            "('90000000-0000-4000-8001-000000000104', '90000000-0000-4000-8000-000000000001', 900002, 'FE USB-C Hub 8-in-1', 'fe-usb-c-hub-8-in-1', 'Low-price add-to-cart product for quantity update and remove tests.', '{\"brand\":\"FE\",\"ports\":8,\"coverage\":[\"cart-update\",\"cart-remove\"]}'::jsonb, 'ACTIVE', null, now() - interval '11 days', 900003, 0, now() - interval '12 days', now() - interval '12 days', now(), now() - interval '11 days'), " +
            "('90000000-0000-4000-8001-000000000105', '90000000-0000-4000-8000-000000000006', 900002, 'FE Pending Review Backpack', 'fe-pending-review-backpack', 'Pending product for admin moderation list and approve flow.', '{\"brand\":\"FE\",\"coverage\":[\"admin-products-pending\",\"approve-product\"]}'::jsonb, 'PENDING', null, null, null, 0, now() - interval '2 days', now() - interval '5 days', now(), null), " +
            "('90000000-0000-4000-8001-000000000106', '90000000-0000-4000-8000-000000000006', 900002, 'FE Rejected Sample Bag', 'fe-rejected-sample-bag', 'Rejected product for seller edit/resubmit and admin reject display.', '{\"brand\":\"FE\",\"coverage\":[\"rejected-product\",\"resubmit-product\"]}'::jsonb, 'REJECTED', 'Missing real product images and warranty details.', now() - interval '3 days', 900003, 1, now() - interval '4 days', now() - interval '6 days', now(), null), " +
            "('90000000-0000-4000-8001-000000000107', '90000000-0000-4000-8000-000000000005', 900002, 'FE Draft Smart Lamp', 'fe-draft-smart-lamp', 'Draft seller product for submit-for-review flow.', '{\"brand\":\"FE\",\"coverage\":[\"submit-product-review\"]}'::jsonb, 'DRAFT', null, null, null, 0, null, now() - interval '3 days', now(), null), " +
            "('90000000-0000-4000-8001-000000000108', '90000000-0000-4000-8000-000000000005', 900002, 'FE Approved Robot Vacuum', 'fe-approved-robot-vacuum', 'Approved but unpublished seller product for publish flow.', '{\"brand\":\"FE\",\"coverage\":[\"publish-product\"]}'::jsonb, 'APPROVED', null, now() - interval '2 days', 900003, 0, now() - interval '3 days', now() - interval '4 days', now(), null), " +
            "('90000000-0000-4000-8001-000000000109', '90000000-0000-4000-8000-000000000003', 900002, 'FE Out Of Stock Headphone', 'fe-out-of-stock-headphone', 'Out-of-stock product for inventory/restock tests.', '{\"brand\":\"FE\",\"coverage\":[\"inventory\",\"restock\"]}'::jsonb, 'OUT_OF_STOCK', null, now() - interval '8 days', 900003, 0, now() - interval '9 days', now() - interval '9 days', now(), now() - interval '8 days'), " +
            "('90000000-0000-4000-8001-000000000110', '90000000-0000-4000-8000-000000000005', 900002, 'FE Inactive Desk Setup', 'fe-inactive-desk-setup', 'Inactive product for seller unpublish/publish regression.', '{\"brand\":\"FE\",\"coverage\":[\"unpublish-product\",\"inactive-product\"]}'::jsonb, 'INACTIVE', null, now() - interval '7 days', 900003, 0, now() - interval '8 days', now() - interval '8 days', now(), null), " +
            // --- New 5 ---
            "('90000000-0000-4000-8001-000000000111', '90000000-0000-4000-8000-000000000006', 900002, 'FE Summer T-Shirt', 'fe-summer-t-shirt', 'Multi-variant fashion product with sizes S/M/L/XL for cart and order tests.', '{\"brand\":\"FE\",\"material\":\"cotton\",\"coverage\":[\"multi-variant\",\"cart\",\"order\"]}'::jsonb, 'ACTIVE', null, now() - interval '6 days', 900003, 0, now() - interval '7 days', now() - interval '7 days', now(), now() - interval '6 days'), " +
            "('90000000-0000-4000-8001-000000000112', '90000000-0000-4000-8000-000000000001', 900002, 'FE Wireless Charger Pad', 'fe-wireless-charger-pad', 'Standard active electronics product for search and detail tests.', '{\"brand\":\"FE\",\"power\":\"15W\",\"coverage\":[\"search\",\"product-detail\"]}'::jsonb, 'ACTIVE', null, now() - interval '5 days', 900003, 0, now() - interval '6 days', now() - interval '6 days', now(), now() - interval '5 days'), " +
            "('90000000-0000-4000-8001-000000000113', '90000000-0000-4000-8000-000000000005', 900002, 'FE Yoga Mat Premium', 'fe-yoga-mat-premium', 'Active home product for category browsing and stock validation.', '{\"brand\":\"FE\",\"thickness\":\"6mm\",\"coverage\":[\"category-browse\",\"stock\"]}'::jsonb, 'ACTIVE', null, now() - interval '4 days', 900003, 0, now() - interval '5 days', now() - interval '5 days', now(), now() - interval '4 days'), " +
            "('90000000-0000-4000-8001-000000000114', '90000000-0000-4000-8000-000000000006', 900002, 'FE Travel Backpack', 'fe-travel-backpack', 'Active fashion product for search and compare features.', '{\"brand\":\"FE\",\"capacity\":\"40L\",\"coverage\":[\"search\",\"compare\"]}'::jsonb, 'ACTIVE', null, now() - interval '3 days', 900003, 0, now() - interval '4 days', now() - interval '4 days', now(), now() - interval '3 days'), " +
            "('90000000-0000-4000-8001-000000000115', '90000000-0000-4000-8000-000000000003', 900002, 'FE Bluetooth Earbuds Pro', 'fe-bluetooth-earbuds-pro', 'Premium audio product for flash-sale and recommendation tests.', '{\"brand\":\"FE\",\"battery\":\"8h\",\"coverage\":[\"flash-sale\",\"recommendations\"]}'::jsonb, 'ACTIVE', null, now() - interval '2 days', 900003, 0, now() - interval '3 days', now() - interval '3 days', now(), now() - interval '2 days') " +
            "ON CONFLICT (id) DO UPDATE SET category_id=EXCLUDED.category_id,seller_id=EXCLUDED.seller_id,name=EXCLUDED.name,slug=EXCLUDED.slug,description=EXCLUDED.description,attributes=EXCLUDED.attributes,status=EXCLUDED.status,reject_reason=EXCLUDED.reject_reason,reviewed_at=EXCLUDED.reviewed_at,reviewed_by=EXCLUDED.reviewed_by,reject_count=EXCLUDED.reject_count,submitted_at=EXCLUDED.submitted_at,updated_at=now(),published_at=EXCLUDED.published_at");

        // ========================================================================
        // 3. Variants
        // ========================================================================
        jdbcTemplate.update("INSERT INTO product.product_variants (id, product_id, variant_code, variant_name, variant_attributes, price, original_price, stock_quantity, status, version, image_url, created_at, updated_at) VALUES " +
            // --- Existing 10 variants (101-110) ---
            "('90000000-0000-4000-9001-000000000101', '90000000-0000-4000-8001-000000000101', 'FE-SKU-PHONE-15PRO', 'Black / 256GB', '{\"color\":\"black\",\"storage\":\"256GB\"}'::jsonb, 23990000, 25990000, 25, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-phone-15pro/500/500', now() - interval '15 days', now()), " +
            "('90000000-0000-4000-9001-000000000102', '90000000-0000-4000-8001-000000000102', 'FE-SKU-LAPTOP-M3', 'Space Gray / 16GB', '{\"color\":\"gray\",\"ram\":\"16GB\"}'::jsonb, 27990000, 31990000, 12, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-laptop-m3/500/500', now() - interval '14 days', now()), " +
            "('90000000-0000-4000-9001-000000000103', '90000000-0000-4000-8001-000000000103', 'FE-SKU-AIRPODS-COMBO', 'USB-C Combo', '{\"connector\":\"USB-C\"}'::jsonb, 4990000, 6490000, 60, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-airpods-combo/500/500', now() - interval '13 days', now()), " +
            "('90000000-0000-4000-9001-000000000104', '90000000-0000-4000-8001-000000000104', 'FE-SKU-HUB-8IN1', 'Silver / 8 ports', '{\"color\":\"silver\",\"ports\":8}'::jsonb, 790000, 990000, 150, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-hub-8in1/500/500', now() - interval '12 days', now()), " +
            "('90000000-0000-4000-9001-000000000105', '90000000-0000-4000-8001-000000000105', 'FE-SKU-PENDING-BACKPACK', 'Default', '{}'::jsonb, 690000, 890000, 40, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-pending-backpack/500/500', now() - interval '5 days', now()), " +
            "('90000000-0000-4000-9001-000000000106', '90000000-0000-4000-8001-000000000106', 'FE-SKU-REJECTED-BAG', 'Default', '{}'::jsonb, 590000, 790000, 35, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-rejected-bag/500/500', now() - interval '6 days', now()), " +
            "('90000000-0000-4000-9001-000000000107', '90000000-0000-4000-8001-000000000107', 'FE-SKU-DRAFT-LAMP', 'Warm White', '{\"color\":\"warm-white\"}'::jsonb, 450000, 550000, 20, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-draft-lamp/500/500', now() - interval '3 days', now()), " +
            "('90000000-0000-4000-9001-000000000108', '90000000-0000-4000-8001-000000000108', 'FE-SKU-APPROVED-VACUUM', 'Standard', '{}'::jsonb, 3890000, 4590000, 18, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-approved-vacuum/500/500', now() - interval '4 days', now()), " +
            "('90000000-0000-4000-9001-000000000109', '90000000-0000-4000-8001-000000000109', 'FE-SKU-OOS-HEADPHONE', 'Midnight', '{\"color\":\"midnight\"}'::jsonb, 1290000, 1590000, 0, 'OUT_OF_STOCK', 1, 'https://picsum.photos/seed/fe-oos-headphone/500/500', now() - interval '9 days', now()), " +
            "('90000000-0000-4000-9001-000000000110', '90000000-0000-4000-8001-000000000110', 'FE-SKU-INACTIVE-DESK', 'Default', '{}'::jsonb, 1990000, 2490000, 10, 'INACTIVE', 1, 'https://picsum.photos/seed/fe-inactive-desk/500/500', now() - interval '8 days', now()), " +
            // --- Product 111 (multi-variant: S/M/L/XL) uses 111-114 ---
            "('90000000-0000-4000-9001-000000000111', '90000000-0000-4000-8001-000000000111', 'FE-SKU-TSHIRT-S', 'Size S', '{\"size\":\"S\",\"material\":\"cotton\"}'::jsonb, 149000, 199000, 50, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-tshirt-s/500/500', now() - interval '7 days', now()), " +
            "('90000000-0000-4000-9001-000000000112', '90000000-0000-4000-8001-000000000111', 'FE-SKU-TSHIRT-M', 'Size M', '{\"size\":\"M\",\"material\":\"cotton\"}'::jsonb, 149000, 199000, 80, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-tshirt-m/500/500', now() - interval '7 days', now()), " +
            "('90000000-0000-4000-9001-000000000113', '90000000-0000-4000-8001-000000000111', 'FE-SKU-TSHIRT-L', 'Size L', '{\"size\":\"L\",\"material\":\"cotton\"}'::jsonb, 149000, 199000, 60, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-tshirt-l/500/500', now() - interval '7 days', now()), " +
            "('90000000-0000-4000-9001-000000000114', '90000000-0000-4000-8001-000000000111', 'FE-SKU-TSHIRT-XL', 'Size XL', '{\"size\":\"XL\",\"material\":\"cotton\"}'::jsonb, 149000, 199000, 10, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-tshirt-xl/500/500', now() - interval '7 days', now()), " +
            // --- Additional variants for products 101-110 (301-328: +2 each = 20) ---
            // Product 101 (+2): Phone — Silver & Blue
            "('90000000-0000-4000-9001-000000000301', '90000000-0000-4000-8001-000000000101', 'FE-SKU-PHONE-SILVER', 'Silver / 512GB', '{\"color\":\"silver\",\"storage\":\"512GB\"}'::jsonb, 28990000, 30990000, 15, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-phone-silver/500/500', now() - interval '15 days', now()), " +
            "('90000000-0000-4000-9001-000000000302', '90000000-0000-4000-8001-000000000101', 'FE-SKU-PHONE-BLUE', 'Blue / 128GB', '{\"color\":\"blue\",\"storage\":\"128GB\"}'::jsonb, 20990000, 22990000, 30, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-phone-blue/500/500', now() - interval '15 days', now()), " +
            // Product 102 (+2): Laptop — Silver & Midnight
            "('90000000-0000-4000-9001-000000000303', '90000000-0000-4000-8001-000000000102', 'FE-SKU-LAPTOP-SILVER', 'Silver / 8GB', '{\"color\":\"silver\",\"ram\":\"8GB\"}'::jsonb, 24990000, 27990000, 8, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-laptop-silver/500/500', now() - interval '14 days', now()), " +
            "('90000000-0000-4000-9001-000000000304', '90000000-0000-4000-8001-000000000102', 'FE-SKU-LAPTOP-MIDNIGHT', 'Midnight / 16GB', '{\"color\":\"midnight\",\"ram\":\"16GB\"}'::jsonb, 29990000, 33990000, 5, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-laptop-midnight/500/500', now() - interval '14 days', now()), " +
            // Product 103 (+2): AirPods — Lightning & Pro Max
            "('90000000-0000-4000-9001-000000000305', '90000000-0000-4000-8001-000000000103', 'FE-SKU-AIRPODS-LIGHTNING', 'Lightning / Standard', '{\"connector\":\"Lightning\",\"anc\":false}'::jsonb, 3990000, 5490000, 45, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-airpods-lightning/500/500', now() - interval '13 days', now()), " +
            "('90000000-0000-4000-9001-000000000306', '90000000-0000-4000-8001-000000000103', 'FE-SKU-AIRPODS-PROMAX', 'Pro Max / ANC', '{\"connector\":\"USB-C\",\"anc\":true}'::jsonb, 6990000, 7990000, 25, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-airpods-promax/500/500', now() - interval '13 days', now()), " +
            // Product 104 (+2): Hub — Black & White
            "('90000000-0000-4000-9001-000000000307', '90000000-0000-4000-8001-000000000104', 'FE-SKU-HUB-BLACK', 'Black / 6 ports', '{\"color\":\"black\",\"ports\":6}'::jsonb, 690000, 890000, 120, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-hub-black/500/500', now() - interval '12 days', now()), " +
            "('90000000-0000-4000-9001-000000000308', '90000000-0000-4000-8001-000000000104', 'FE-SKU-HUB-WHITE', 'White / 10 ports', '{\"color\":\"white\",\"ports\":10}'::jsonb, 990000, 1190000, 80, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-hub-white/500/500', now() - interval '12 days', now()), " +
            // Product 105 (+2): Backpack — Green & Blue
            "('90000000-0000-4000-9001-000000000309', '90000000-0000-4000-8001-000000000105', 'FE-SKU-BACKPACK-GREEN', 'Green', '{\"color\":\"green\"}'::jsonb, 690000, 890000, 30, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-backpack-green/500/500', now() - interval '5 days', now()), " +
            "('90000000-0000-4000-9001-000000000310', '90000000-0000-4000-8001-000000000105', 'FE-SKU-BACKPACK-BLUE', 'Blue', '{\"color\":\"blue\"}'::jsonb, 690000, 890000, 25, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-backpack-blue/500/500', now() - interval '5 days', now()), " +
            // Product 106 (+2): Bag — Red & Black
            "('90000000-0000-4000-9001-000000000311', '90000000-0000-4000-8001-000000000106', 'FE-SKU-BAG-RED', 'Red', '{\"color\":\"red\"}'::jsonb, 590000, 790000, 28, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-bag-red/500/500', now() - interval '6 days', now()), " +
            "('90000000-0000-4000-9001-000000000312', '90000000-0000-4000-8001-000000000106', 'FE-SKU-BAG-BLACK', 'Black', '{\"color\":\"black\"}'::jsonb, 590000, 790000, 22, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-bag-black/500/500', now() - interval '6 days', now()), " +
            // Product 107 (+2): Lamp — Cool White & RGB
            "('90000000-0000-4000-9001-000000000313', '90000000-0000-4000-8001-000000000107', 'FE-SKU-LAMP-COOL', 'Cool White', '{\"color\":\"cool-white\"}'::jsonb, 450000, 550000, 18, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-lamp-cool/500/500', now() - interval '3 days', now()), " +
            "('90000000-0000-4000-9001-000000000314', '90000000-0000-4000-8001-000000000107', 'FE-SKU-LAMP-RGB', 'RGB Color', '{\"color\":\"rgb\"}'::jsonb, 590000, 690000, 12, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-lamp-rgb/500/500', now() - interval '3 days', now()), " +
            // Product 108 (+2): Vacuum — Pro & Max
            "('90000000-0000-4000-9001-000000000315', '90000000-0000-4000-8001-000000000108', 'FE-SKU-VACUUM-PRO', 'Pro / Wet+Dry', '{\"model\":\"pro\",\"features\":\"wet-dry\"}'::jsonb, 4890000, 5590000, 10, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-vacuum-pro/500/500', now() - interval '4 days', now()), " +
            "('90000000-0000-4000-9001-000000000316', '90000000-0000-4000-8001-000000000108', 'FE-SKU-VACUUM-MAX', 'Max / Self-clean', '{\"model\":\"max\",\"features\":\"self-clean\"}'::jsonb, 5890000, 6590000, 6, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-vacuum-max/500/500', now() - interval '4 days', now()), " +
            // Product 109 (+2): Headphone — White & Rose Gold (keep OOS status matching parent logic)
            "('90000000-0000-4000-9001-000000000317', '90000000-0000-4000-8001-000000000109', 'FE-SKU-OOS-HP-WHITE', 'White', '{\"color\":\"white\"}'::jsonb, 1290000, 1590000, 5, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-oos-hp-white/500/500', now() - interval '9 days', now()), " +
            "('90000000-0000-4000-9001-000000000318', '90000000-0000-4000-8001-000000000109', 'FE-SKU-OOS-HP-ROSE', 'Rose Gold', '{\"color\":\"rose-gold\"}'::jsonb, 1390000, 1690000, 3, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-oos-hp-rose/500/500', now() - interval '9 days', now()), " +
            // Product 110 (+2): Desk — Oak & Walnut
            "('90000000-0000-4000-9001-000000000319', '90000000-0000-4000-8001-000000000110', 'FE-SKU-DESK-OAK', 'Oak', '{\"material\":\"oak\"}'::jsonb, 1990000, 2490000, 8, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-desk-oak/500/500', now() - interval '8 days', now()), " +
            "('90000000-0000-4000-9001-000000000320', '90000000-0000-4000-8001-000000000110', 'FE-SKU-DESK-WALNUT', 'Walnut', '{\"material\":\"walnut\"}'::jsonb, 2190000, 2690000, 5, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-desk-walnut/500/500', now() - interval '8 days', now()), " +
            // --- Product 112 (+2): Charger — Black & Blue ---
            "('90000000-0000-4000-9001-000000000321', '90000000-0000-4000-8001-000000000112', 'FE-SKU-CHARGER-BLACK', 'Black / 10W', '{\"color\":\"black\",\"power\":\"10W\"}'::jsonb, 399000, 490000, 70, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-charger-black/500/500', now() - interval '6 days', now()), " +
            "('90000000-0000-4000-9001-000000000322', '90000000-0000-4000-8001-000000000112', 'FE-SKU-CHARGER-BLUE', 'Blue / 15W', '{\"color\":\"blue\",\"power\":\"15W\"}'::jsonb, 499000, 590000, 55, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-charger-blue/500/500', now() - interval '6 days', now()), " +
            // --- Product 113 (+2): Yoga Mat — Blue & Green ---
            "('90000000-0000-4000-9001-000000000323', '90000000-0000-4000-8001-000000000113', 'FE-SKU-YOGA-BLUE', 'Blue / 6mm', '{\"color\":\"blue\",\"thickness\":\"6mm\"}'::jsonb, 350000, 490000, 80, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-yoga-blue/500/500', now() - interval '5 days', now()), " +
            "('90000000-0000-4000-9001-000000000324', '90000000-0000-4000-8001-000000000113', 'FE-SKU-YOGA-GREEN', 'Green / 8mm', '{\"color\":\"green\",\"thickness\":\"8mm\"}'::jsonb, 450000, 590000, 55, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-yoga-green/500/500', now() - interval '5 days', now()), " +
            // --- Product 114 (+2): Backpack — Black & Blue ---
            "('90000000-0000-4000-9001-000000000325', '90000000-0000-4000-8001-000000000114', 'FE-SKU-BP-BLACK', 'Black / 30L', '{\"color\":\"black\",\"capacity\":\"30L\"}'::jsonb, 890000, 1090000, 35, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-bp-black/500/500', now() - interval '4 days', now()), " +
            "('90000000-0000-4000-9001-000000000326', '90000000-0000-4000-8001-000000000114', 'FE-SKU-BP-BLUE', 'Blue / 50L', '{\"color\":\"blue\",\"capacity\":\"50L\"}'::jsonb, 990000, 1290000, 25, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-bp-blue/500/500', now() - interval '4 days', now()), " +
            // --- Product 115 (+2): Earbuds — White & Red ---
            "('90000000-0000-4000-9001-000000000327', '90000000-0000-4000-8001-000000000115', 'FE-SKU-EARBUDS-WHITE', 'White / ANC', '{\"color\":\"white\",\"anc\":true}'::jsonb, 1590000, 1990000, 28, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-earbuds-white/500/500', now() - interval '3 days', now()), " +
            "('90000000-0000-4000-9001-000000000328', '90000000-0000-4000-8001-000000000115', 'FE-SKU-EARBUDS-RED', 'Red / Standard', '{\"color\":\"red\",\"anc\":false}'::jsonb, 990000, 1290000, 40, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-earbuds-red/500/500', now() - interval '3 days', now()), " +
            // --- Existing single-variant for products 112-115 (kept as primary variant) ---
            "('90000000-0000-4000-9001-000000000115', '90000000-0000-4000-8001-000000000112', 'FE-SKU-CHARGER-PAD', 'White / 15W', '{\"color\":\"white\",\"power\":\"15W\"}'::jsonb, 450000, 590000, 80, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-charger-pad/500/500', now() - interval '6 days', now()), " +
            "('90000000-0000-4000-9001-000000000201', '90000000-0000-4000-8001-000000000113', 'FE-SKU-YOGA-MAT', 'Purple / 6mm', '{\"color\":\"purple\",\"thickness\":\"6mm\"}'::jsonb, 350000, 490000, 100, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-yoga-mat/500/500', now() - interval '5 days', now()), " +
            "('90000000-0000-4000-9001-000000000202', '90000000-0000-4000-8001-000000000114', 'FE-SKU-TRAVEL-BACKPACK', 'Gray / 40L', '{\"color\":\"gray\",\"capacity\":\"40L\"}'::jsonb, 890000, 1090000, 45, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-travel-backpack/500/500', now() - interval '4 days', now()), " +
            "('90000000-0000-4000-9001-000000000203', '90000000-0000-4000-8001-000000000115', 'FE-SKU-EARBUDS-PRO', 'Black / ANC', '{\"color\":\"black\",\"anc\":true}'::jsonb, 1590000, 1990000, 35, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-earbuds-pro/500/500', now() - interval '3 days', now()) " +
            "ON CONFLICT (id) DO UPDATE SET product_id=EXCLUDED.product_id,variant_code=EXCLUDED.variant_code,variant_name=EXCLUDED.variant_name,variant_attributes=EXCLUDED.variant_attributes,price=EXCLUDED.price,original_price=EXCLUDED.original_price,stock_quantity=EXCLUDED.stock_quantity,status=EXCLUDED.status,version=EXCLUDED.version,image_url=EXCLUDED.image_url,updated_at=now()");

        // ========================================================================
        // 4. Images
        // ========================================================================
        jdbcTemplate.update("INSERT INTO product.product_images (id, product_id, variant_id, url, sort_order, created_at) VALUES " +
            // --- Existing 10 images (101-110) ---
            "('90000000-0000-4000-a001-000000000101', '90000000-0000-4000-8001-000000000101', null, 'https://picsum.photos/seed/fe-phone-hero/800/800', 0, now() - interval '15 days'), " +
            "('90000000-0000-4000-a001-000000000102', '90000000-0000-4000-8001-000000000102', null, 'https://picsum.photos/seed/fe-laptop-hero/800/800', 0, now() - interval '14 days'), " +
            "('90000000-0000-4000-a001-000000000103', '90000000-0000-4000-8001-000000000103', null, 'https://picsum.photos/seed/fe-airpods-hero/800/800', 0, now() - interval '13 days'), " +
            "('90000000-0000-4000-a001-000000000104', '90000000-0000-4000-8001-000000000104', null, 'https://picsum.photos/seed/fe-hub-hero/800/800', 0, now() - interval '12 days'), " +
            "('90000000-0000-4000-a001-000000000105', '90000000-0000-4000-8001-000000000105', null, 'https://picsum.photos/seed/fe-pending-hero/800/800', 0, now() - interval '5 days'), " +
            "('90000000-0000-4000-a001-000000000106', '90000000-0000-4000-8001-000000000106', null, 'https://picsum.photos/seed/fe-rejected-hero/800/800', 0, now() - interval '6 days'), " +
            "('90000000-0000-4000-a001-000000000107', '90000000-0000-4000-8001-000000000107', null, 'https://picsum.photos/seed/fe-draft-hero/800/800', 0, now() - interval '3 days'), " +
            "('90000000-0000-4000-a001-000000000108', '90000000-0000-4000-8001-000000000108', null, 'https://picsum.photos/seed/fe-vacuum-hero/800/800', 0, now() - interval '4 days'), " +
            "('90000000-0000-4000-a001-000000000109', '90000000-0000-4000-8001-000000000109', null, 'https://picsum.photos/seed/fe-oos-hero/800/800', 0, now() - interval '9 days'), " +
            "('90000000-0000-4000-a001-000000000110', '90000000-0000-4000-8001-000000000110', null, 'https://picsum.photos/seed/fe-desk-hero/800/800', 0, now() - interval '8 days'), " +
            // --- New images for products 111-115 ---
            "('90000000-0000-4000-a001-000000000111', '90000000-0000-4000-8001-000000000111', null, 'https://picsum.photos/seed/fe-tshirt-hero/800/800', 0, now() - interval '7 days'), " +
            "('90000000-0000-4000-a001-000000000112', '90000000-0000-4000-8001-000000000112', null, 'https://picsum.photos/seed/fe-charger-hero/800/800', 0, now() - interval '6 days'), " +
            "('90000000-0000-4000-a001-000000000113', '90000000-0000-4000-8001-000000000113', null, 'https://picsum.photos/seed/fe-yoga-hero/800/800', 0, now() - interval '5 days'), " +
            "('90000000-0000-4000-a001-000000000114', '90000000-0000-4000-8001-000000000114', null, 'https://picsum.photos/seed/fe-backpack-hero/800/800', 0, now() - interval '4 days'), " +
            "('90000000-0000-4000-a001-000000000115', '90000000-0000-4000-8001-000000000115', null, 'https://picsum.photos/seed/fe-earbuds-hero/800/800', 0, now() - interval '3 days') " +
            "ON CONFLICT (id) DO UPDATE SET product_id=EXCLUDED.product_id,variant_id=EXCLUDED.variant_id,url=EXCLUDED.url,sort_order=EXCLUDED.sort_order");

        // ========================================================================
        // 5. Wishlist: fe_buyer (900001) wishlists products 101, 103, 104
        // ========================================================================
        jdbcTemplate.update("INSERT INTO product.wishlist_items (customer_id, product_id, created_at) VALUES " +
            "(900001, '90000000-0000-4000-8001-000000000101', now() - interval '10 days'), " +
            "(900001, '90000000-0000-4000-8001-000000000103', now() - interval '8 days'), " +
            "(900001, '90000000-0000-4000-8001-000000000104', now() - interval '6 days') " +
            "ON CONFLICT DO NOTHING");

        // ========================================================================
        // 6. Cart: fe_buyer (900001) has product 102 x1, product 104 x2
        // ========================================================================
        // Look up variant price/name/image snapshots from the seeded variants
        jdbcTemplate.update("INSERT INTO product.cart_items (customer_id, variant_id, quantity, price_snapshot, variant_name_snapshot, variant_image_snapshot, seller_id, created_at, updated_at) " +
            "SELECT 900001, id, 1, price, variant_name, image_url, 900002, now() - interval '5 days', now() " +
            "FROM product.product_variants WHERE id = '90000000-0000-4000-9001-000000000102' " +
            "ON CONFLICT DO NOTHING");
        jdbcTemplate.update("INSERT INTO product.cart_items (customer_id, variant_id, quantity, price_snapshot, variant_name_snapshot, variant_image_snapshot, seller_id, created_at, updated_at) " +
            "SELECT 900001, id, 2, price, variant_name, image_url, 900002, now() - interval '4 days', now() " +
            "FROM product.product_variants WHERE id = '90000000-0000-4000-9001-000000000104' " +
            "ON CONFLICT DO NOTHING");

        log.info("[ProductDevDataLoader] FE test-dataset seeded (6 categories, 15 products, 46 variants, 15 images, 3 wishlist items, 2 cart items).");
    }

    /**
     * Seeds products from {@code test-data/products.json} (the full-coverage dataset).
     * Each JSON product becomes one product + one variant (mapped by SKU).
     * Category names are matched to existing categories; unknown names create a simple mapping.
     * Idempotent via ON CONFLICT DO UPDATE on variant_code.
     */
    private void seedFromJsonDataset() {
        List<Map<String, Object>> products;
        try (InputStream is = new ClassPathResource(JSON_PATH).getInputStream()) {
            products = new ObjectMapper().readValue(is, new TypeReference<List<Map<String, Object>>>() {});
        } catch (IOException e) {
            log.warn("[ProductDevDataLoader] Could not read {} — skipping JSON seed: {}", JSON_PATH, e.getMessage());
            return;
        }

        log.info("[ProductDevDataLoader] Seeding {} products from {}", products.size(), JSON_PATH);

        // Check if JSON data already exists (by first SKU)
        Map<String, Object> first = products.getFirst();
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM product.product_variants WHERE variant_code = ?",
            Integer.class, first.get("sku"));
        if (count != null && count > 0) {
            log.info("[ProductDevDataLoader] JSON dataset already seeded, skipping.");
            return;
        }

        int sortId = 1000;
        for (Map<String, Object> p : products) {
            String sku = (String) p.get("sku");
            String name = (String) p.get("name");
            String slug = (String) p.get("slug");
            String description = (String) p.get("description");
            Number priceNum = (Number) p.get("price");
            Number originalPriceNum = (Number) p.get("originalPrice");
            Number stockNum = (Number) p.get("stock");
            Number sellerIdNum = (Number) p.get("sellerId");
            String categoryName = (String) p.get("category");
            String status = (String) p.get("status");

            int price = priceNum.intValue();
            int originalPrice = originalPriceNum != null ? originalPriceNum.intValue() : price;
            int stock = stockNum != null ? stockNum.intValue() : 0;
            int sellerId = sellerIdNum.intValue();

            // Generate deterministic UUIDs from SKU
            String productId = UUID.nameUUIDFromBytes(("product:" + sku).getBytes()).toString();
            String variantId = UUID.nameUUIDFromBytes(("variant:" + sku).getBytes()).toString();
            String imageId = UUID.nameUUIDFromBytes(("image:" + sku).getBytes()).toString();

            // Map or create category — derive slug from category name
            String catSlug = categoryName.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
            String catId = UUID.nameUUIDFromBytes(("category:" + catSlug).getBytes()).toString();

            // Ensure category exists
            jdbcTemplate.update("""
                INSERT INTO product.categories (id, parent_id, name, slug, description, image_url, sort_order, is_active, created_at, updated_at)
                VALUES (?, null, ?, ?, ?, 'https://picsum.photos/seed/' || ? || '/600/400', ?, true, now(), now())
                ON CONFLICT (id) DO NOTHING
                """, catId, categoryName, catSlug, "Auto-seeded from JSON dataset", catSlug, sortId);

            // Insert product
            jdbcTemplate.update("""
                INSERT INTO product.products (id, category_id, seller_id, name, slug, description, attributes, status, reject_reason, reviewed_at, reviewed_by, reject_count, submitted_at, created_at, updated_at, published_at)
                VALUES (?, ?, ?, ?, ?, ?, '{}'::jsonb, ?, null, null, null, 0, now(), now(), now(), CASE WHEN ? = 'ACTIVE' THEN now() ELSE null END)
                ON CONFLICT (id) DO UPDATE SET
                    name = EXCLUDED.name, slug = EXCLUDED.slug, description = EXCLUDED.description,
                    category_id = EXCLUDED.category_id, status = EXCLUDED.status, updated_at = now()
                """, productId, catId, sellerId, name, slug, description,
                status != null ? status : "ACTIVE", status);

            // Insert variant (one per product)
            jdbcTemplate.update("""
                INSERT INTO product.product_variants (id, product_id, variant_code, variant_name, variant_attributes, price, original_price, stock_quantity, status, version, image_url, created_at, updated_at)
                VALUES (?, ?, ?, 'Default', '{}'::jsonb, ?, ?, ?, 'ACTIVE', 1, 'https://picsum.photos/seed/' || ? || '/500/500', now(), now())
                ON CONFLICT (id) DO UPDATE SET
                    product_id = EXCLUDED.product_id, variant_code = EXCLUDED.variant_code,
                    price = EXCLUDED.price, original_price = EXCLUDED.original_price,
                    stock_quantity = EXCLUDED.stock_quantity, updated_at = now()
                """, variantId, productId, sku, price, originalPrice, stock, slug);

            // Insert image
            jdbcTemplate.update("""
                INSERT INTO product.product_images (id, product_id, variant_id, url, sort_order, created_at)
                VALUES (?, ?, null, 'https://picsum.photos/seed/' || ? || '/800/800', 0, now())
                ON CONFLICT (id) DO NOTHING
                """, imageId, productId, slug);

            sortId++;
        }

        log.info("[ProductDevDataLoader] JSON dataset seeded ({} products, {} variants).", products.size(), products.size());
    }
}
