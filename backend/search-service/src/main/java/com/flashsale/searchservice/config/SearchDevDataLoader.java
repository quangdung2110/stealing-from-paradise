package com.flashsale.searchservice.config;

import com.flashsale.commonlib.config.DevDataProperties;
import com.flashsale.searchservice.domain.model.SearchDocument;
import com.flashsale.searchservice.service.ElasticsearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "dev-data.enabled", havingValue = "true", matchIfMissing = false)
@Order(2)
public class SearchDevDataLoader implements CommandLineRunner {

    private final ElasticsearchService esService;
    private final DevDataProperties devDataProperties;

    @Override
    public void run(String... args) {
        log.info("[SearchDevDataLoader] Starting dev data seed for search-service...");

        if (devDataProperties.isReset()) {
            log.warn("[SearchDevDataLoader] RESET=true -- clearing FE documents from skus index...");
            try {
                esService.deleteByProductId("90000000-0000-4000-8001-000000000101");
            } catch (IOException e) {
                log.warn("[SearchDevDataLoader] Could not clear index: {}", e.getMessage());
            }
            log.info("[SearchDevDataLoader] ES FE documents cleared.");
        }

        seedFeData();
        log.info("[SearchDevDataLoader] Dev data seed complete.");
    }

    private void seedFeData() {
        log.info("[SearchDevDataLoader] Seeding FE test-dataset...");

        try {
            var response = esService.search("FE Phone Pro Camera Kit", null, null,
                    null, null, null, null, 0, 1);
            if (response.getTotalResults() > 0) {
                log.info("[SearchDevDataLoader] FE data already exists, skipping.");
                return;
            }
        } catch (Exception e) {
            log.warn("[SearchDevDataLoader] Search check failed, proceeding: {}", e.getMessage());
        }

        List<SearchDocument> docs = new ArrayList<>();
        String sellerName = "Frontend Seller";
        Long sellerId = 900002L;

        String catElectronics = "90000000-0000-4000-8000-000000000001";
        String catPhones     = "90000000-0000-4000-8000-000000000002";
        String catAudio      = "90000000-0000-4000-8000-000000000003";
        String catLaptops    = "90000000-0000-4000-8000-000000000004";
        String catHome       = "90000000-0000-4000-8000-000000000005";
        String catFashion    = "90000000-0000-4000-8000-000000000006";

        // --- 101: FE Phone Pro Camera Kit (Phones) ---
        docs.add(SearchDocument.builder()
                .skuId("90000000-0000-4000-9001-000000000101")
                .productId("90000000-0000-4000-8001-000000000101").sellerId(sellerId)
                .productName("FE Phone Pro Camera Kit").productSlug("fe-phone-pro-camera-kit")
                .productDescription("Active catalog product for search, detail, cart, checkout, and flash-sale tests.")
                .productAttributes(Map.of("brand", "FE", "screen", "6.1 inch"))
                .categoryId(catPhones).categoryPath("FE Electronics > FE Phones")
                .categoryName("FE Phones").categorySlug("fe-phones")
                .categorySlugPath(List.of("fe-electronics", "fe-phones"))
                .variantAttributes(Map.of("color", "black", "storage", "256GB"))
                .skuCode("FE-SKU-PHONE-15PRO").price(23990000.0).originalPrice(25990000.0)
                .hasDiscount(true).flashSessionId("900001").stockStatus("in_stock")
                .productStatus("ACTIVE").skuStatus("ACTIVE").isActive(true)
                .thumbnailUrl("https://picsum.photos/seed/fe-phone-hero/800/800")
                .skuImageUrl("https://picsum.photos/seed/fe-phone-15pro/500/500")
                .sellerName(sellerName).sortId(900101).build());

        // --- 102: FE MacBook Air M3 Demo (Laptops) ---
        docs.add(SearchDocument.builder()
                .skuId("90000000-0000-4000-9001-000000000102")
                .productId("90000000-0000-4000-8001-000000000102").sellerId(sellerId)
                .productName("FE MacBook Air M3 Demo").productSlug("fe-macbook-air-m3-demo")
                .productDescription("High-value seller product for payment and payout screens.")
                .productAttributes(Map.of("brand", "FE", "ram", "16GB"))
                .categoryId(catLaptops).categoryPath("FE Electronics > FE Laptops")
                .categoryName("FE Laptops").categorySlug("fe-laptops")
                .categorySlugPath(List.of("fe-electronics", "fe-laptops"))
                .variantAttributes(Map.of("color", "gray", "ram", "16GB"))
                .skuCode("FE-SKU-LAPTOP-M3").price(27990000.0).originalPrice(31990000.0)
                .hasDiscount(true).stockStatus("in_stock")
                .productStatus("ACTIVE").skuStatus("ACTIVE").isActive(true)
                .thumbnailUrl("https://picsum.photos/seed/fe-laptop-hero/800/800")
                .skuImageUrl("https://picsum.photos/seed/fe-laptop-m3/500/500")
                .sellerName(sellerName).sortId(900102).build());

        // --- 103: FE AirPods Flash Combo (Audio, flash sale) ---
        docs.add(SearchDocument.builder()
                .skuId("90000000-0000-4000-9001-000000000103")
                .productId("90000000-0000-4000-8001-000000000103").sellerId(sellerId)
                .productName("FE AirPods Flash Combo").productSlug("fe-airpods-flash-combo")
                .productDescription("Active product with live flash-sale mapping.")
                .productAttributes(Map.of("brand", "FE", "noiseCancellation", true))
                .categoryId(catAudio).categoryPath("FE Electronics > FE Audio")
                .categoryName("FE Audio").categorySlug("fe-audio")
                .categorySlugPath(List.of("fe-electronics", "fe-audio"))
                .variantAttributes(Map.of("connector", "USB-C"))
                .skuCode("FE-SKU-AIRPODS-COMBO").price(4990000.0).originalPrice(6490000.0)
                .hasDiscount(true).flashSessionId("900001").stockStatus("in_stock")
                .productStatus("ACTIVE").skuStatus("ACTIVE").isActive(true)
                .thumbnailUrl("https://picsum.photos/seed/fe-airpods-hero/800/800")
                .skuImageUrl("https://picsum.photos/seed/fe-airpods-combo/500/500")
                .sellerName(sellerName).sortId(900103).build());

        // --- 104: FE USB-C Hub 8-in-1 (Electronics) ---
        docs.add(SearchDocument.builder()
                .skuId("90000000-0000-4000-9001-000000000104")
                .productId("90000000-0000-4000-8001-000000000104").sellerId(sellerId)
                .productName("FE USB-C Hub 8-in-1").productSlug("fe-usb-c-hub-8-in-1")
                .productDescription("Low-price add-to-cart product for quantity update and remove tests.")
                .productAttributes(Map.of("brand", "FE", "ports", 8))
                .categoryId(catElectronics).categoryPath("FE Electronics")
                .categoryName("FE Electronics").categorySlug("fe-electronics")
                .categorySlugPath(List.of("fe-electronics"))
                .variantAttributes(Map.of("color", "silver", "ports", 8))
                .skuCode("FE-SKU-HUB-8IN1").price(790000.0).originalPrice(990000.0)
                .hasDiscount(true).stockStatus("in_stock")
                .productStatus("ACTIVE").skuStatus("ACTIVE").isActive(true)
                .thumbnailUrl("https://picsum.photos/seed/fe-hub-hero/800/800")
                .skuImageUrl("https://picsum.photos/seed/fe-hub-8in1/500/500")
                .sellerName(sellerName).sortId(900104).build());

        // --- 105: FE Pending Review Backpack (Fashion) ---
        docs.add(SearchDocument.builder()
                .skuId("90000000-0000-4000-9001-000000000105")
                .productId("90000000-0000-4000-8001-000000000105").sellerId(sellerId)
                .productName("FE Pending Review Backpack").productSlug("fe-pending-review-backpack")
                .productDescription("Pending product for admin moderation list and approve flow.")
                .productAttributes(Map.of("brand", "FE"))
                .categoryId(catFashion).categoryPath("FE Fashion")
                .categoryName("FE Fashion").categorySlug("fe-fashion")
                .categorySlugPath(List.of("fe-fashion"))
                .variantAttributes(Map.of()).skuCode("FE-SKU-PENDING-BACKPACK")
                .price(690000.0).originalPrice(890000.0).hasDiscount(true)
                .stockStatus("in_stock").productStatus("PENDING").skuStatus("ACTIVE")
                .isActive(false)
                .thumbnailUrl("https://picsum.photos/seed/fe-pending-hero/800/800")
                .skuImageUrl("https://picsum.photos/seed/fe-pending-backpack/500/500")
                .sellerName(sellerName).sortId(900105).build());

        // --- 106: FE Rejected Sample Bag (Fashion) ---
        docs.add(SearchDocument.builder()
                .skuId("90000000-0000-4000-9001-000000000106")
                .productId("90000000-0000-4000-8001-000000000106").sellerId(sellerId)
                .productName("FE Rejected Sample Bag").productSlug("fe-rejected-sample-bag")
                .productDescription("Rejected product for seller edit/resubmit and admin reject display.")
                .productAttributes(Map.of("brand", "FE"))
                .categoryId(catFashion).categoryPath("FE Fashion")
                .categoryName("FE Fashion").categorySlug("fe-fashion")
                .categorySlugPath(List.of("fe-fashion"))
                .variantAttributes(Map.of()).skuCode("FE-SKU-REJECTED-BAG")
                .price(590000.0).originalPrice(790000.0).hasDiscount(true)
                .stockStatus("in_stock").productStatus("REJECTED").skuStatus("ACTIVE")
                .isActive(false)
                .thumbnailUrl("https://picsum.photos/seed/fe-rejected-hero/800/800")
                .skuImageUrl("https://picsum.photos/seed/fe-rejected-bag/500/500")
                .sellerName(sellerName).sortId(900106).build());

        // --- 107: FE Draft Smart Lamp (Home) ---
        docs.add(SearchDocument.builder()
                .skuId("90000000-0000-4000-9001-000000000107")
                .productId("90000000-0000-4000-8001-000000000107").sellerId(sellerId)
                .productName("FE Draft Smart Lamp").productSlug("fe-draft-smart-lamp")
                .productDescription("Draft seller product for submit-for-review flow.")
                .productAttributes(Map.of("brand", "FE"))
                .categoryId(catHome).categoryPath("FE Home")
                .categoryName("FE Home").categorySlug("fe-home")
                .categorySlugPath(List.of("fe-home"))
                .variantAttributes(Map.of("color", "warm-white")).skuCode("FE-SKU-DRAFT-LAMP")
                .price(450000.0).originalPrice(550000.0).hasDiscount(true)
                .stockStatus("in_stock").productStatus("DRAFT").skuStatus("ACTIVE")
                .isActive(false)
                .thumbnailUrl("https://picsum.photos/seed/fe-draft-hero/800/800")
                .skuImageUrl("https://picsum.photos/seed/fe-draft-lamp/500/500")
                .sellerName(sellerName).sortId(900107).build());

        // --- 108: FE Approved Robot Vacuum (Home) ---
        docs.add(SearchDocument.builder()
                .skuId("90000000-0000-4000-9001-000000000108")
                .productId("90000000-0000-4000-8001-000000000108").sellerId(sellerId)
                .productName("FE Approved Robot Vacuum").productSlug("fe-approved-robot-vacuum")
                .productDescription("Approved but unpublished seller product for publish flow.")
                .productAttributes(Map.of("brand", "FE"))
                .categoryId(catHome).categoryPath("FE Home")
                .categoryName("FE Home").categorySlug("fe-home")
                .categorySlugPath(List.of("fe-home"))
                .variantAttributes(Map.of()).skuCode("FE-SKU-APPROVED-VACUUM")
                .price(3890000.0).originalPrice(4590000.0).hasDiscount(true)
                .stockStatus("in_stock").productStatus("APPROVED").skuStatus("ACTIVE")
                .isActive(false)
                .thumbnailUrl("https://picsum.photos/seed/fe-vacuum-hero/800/800")
                .skuImageUrl("https://picsum.photos/seed/fe-approved-vacuum/500/500")
                .sellerName(sellerName).sortId(900108).build());

        // --- 109: FE Out Of Stock Headphone (Audio) ---
        docs.add(SearchDocument.builder()
                .skuId("90000000-0000-4000-9001-000000000109")
                .productId("90000000-0000-4000-8001-000000000109").sellerId(sellerId)
                .productName("FE Out Of Stock Headphone").productSlug("fe-out-of-stock-headphone")
                .productDescription("Out-of-stock product for inventory/restock tests.")
                .productAttributes(Map.of("brand", "FE"))
                .categoryId(catAudio).categoryPath("FE Electronics > FE Audio")
                .categoryName("FE Audio").categorySlug("fe-audio")
                .categorySlugPath(List.of("fe-electronics", "fe-audio"))
                .variantAttributes(Map.of("color", "midnight")).skuCode("FE-SKU-OOS-HEADPHONE")
                .price(1290000.0).originalPrice(1590000.0).hasDiscount(true)
                .stockStatus("out_of_stock").productStatus("ACTIVE").skuStatus("OUT_OF_STOCK")
                .isActive(true)
                .thumbnailUrl("https://picsum.photos/seed/fe-oos-hero/800/800")
                .skuImageUrl("https://picsum.photos/seed/fe-oos-headphone/500/500")
                .sellerName(sellerName).sortId(900109).build());

        // --- 110: FE Inactive Desk Setup (Home) ---
        docs.add(SearchDocument.builder()
                .skuId("90000000-0000-4000-9001-000000000110")
                .productId("90000000-0000-4000-8001-000000000110").sellerId(sellerId)
                .productName("FE Inactive Desk Setup").productSlug("fe-inactive-desk-setup")
                .productDescription("Inactive product for seller unpublish/publish regression.")
                .productAttributes(Map.of("brand", "FE"))
                .categoryId(catHome).categoryPath("FE Home")
                .categoryName("FE Home").categorySlug("fe-home")
                .categorySlugPath(List.of("fe-home"))
                .variantAttributes(Map.of()).skuCode("FE-SKU-INACTIVE-DESK")
                .price(1990000.0).originalPrice(2490000.0).hasDiscount(true)
                .stockStatus("in_stock").productStatus("INACTIVE").skuStatus("INACTIVE")
                .isActive(false)
                .thumbnailUrl("https://picsum.photos/seed/fe-desk-hero/800/800")
                .skuImageUrl("https://picsum.photos/seed/fe-inactive-desk/500/500")
                .sellerName(sellerName).sortId(900110).build());

        // --- 111-114: FE Summer T-Shirt (Fashion, 4 sizes) ---
        for (var entry : List.<Map.Entry<String, Map<String, Object>>>of(
            Map.entry("90000000-0000-4000-9001-000000000111", Map.of("size", "S")),
            Map.entry("90000000-0000-4000-9001-000000000112", Map.of("size", "M")),
            Map.entry("90000000-0000-4000-9001-000000000113", Map.of("size", "L")),
            Map.entry("90000000-0000-4000-9001-000000000114", Map.of("size", "XL"))
        )) {
            docs.add(SearchDocument.builder()
                    .skuId(entry.getKey())
                    .productId("90000000-0000-4000-8001-000000000111").sellerId(sellerId)
                    .productName("FE Summer T-Shirt").productSlug("fe-summer-t-shirt")
                    .productDescription("Multi-variant fashion product with sizes S/M/L/XL for cart and order tests.")
                    .productAttributes(Map.of("brand", "FE", "material", "cotton"))
                    .categoryId(catFashion).categoryPath("FE Fashion")
                    .categoryName("FE Fashion").categorySlug("fe-fashion")
                    .categorySlugPath(List.of("fe-fashion"))
                    .variantAttributes(entry.getValue())
                    .skuCode("FE-SKU-TSHIRT-" + entry.getValue().get("size"))
                    .price(149000.0).originalPrice(199000.0).hasDiscount(true)
                    .stockStatus("in_stock").productStatus("ACTIVE").skuStatus("ACTIVE")
                    .isActive(true)
                    .thumbnailUrl("https://picsum.photos/seed/fe-tshirt-hero/800/800")
                    .skuImageUrl("https://picsum.photos/seed/fe-tshirt-" + entry.getValue().get("size").toString().toLowerCase() + "/500/500")
                    .sellerName(sellerName).sortId(900111).build());
        }

        // --- 115: FE Wireless Charger Pad (Electronics) ---
        docs.add(SearchDocument.builder()
                .skuId("90000000-0000-4000-9001-000000000115")
                .productId("90000000-0000-4000-8001-000000000112").sellerId(sellerId)
                .productName("FE Wireless Charger Pad").productSlug("fe-wireless-charger-pad")
                .productDescription("Standard active electronics product for search and detail tests.")
                .productAttributes(Map.of("brand", "FE", "power", "15W"))
                .categoryId(catElectronics).categoryPath("FE Electronics")
                .categoryName("FE Electronics").categorySlug("fe-electronics")
                .categorySlugPath(List.of("fe-electronics"))
                .variantAttributes(Map.of("color", "white", "power", "15W"))
                .skuCode("FE-SKU-CHARGER-PAD").price(450000.0).originalPrice(590000.0)
                .hasDiscount(true).stockStatus("in_stock")
                .productStatus("ACTIVE").skuStatus("ACTIVE").isActive(true)
                .thumbnailUrl("https://picsum.photos/seed/fe-charger-hero/800/800")
                .skuImageUrl("https://picsum.photos/seed/fe-charger-pad/500/500")
                .sellerName(sellerName).sortId(900112).build());

        // --- 201: FE Yoga Mat Premium (Home) ---
        docs.add(SearchDocument.builder()
                .skuId("90000000-0000-4000-9001-000000000201")
                .productId("90000000-0000-4000-8001-000000000113").sellerId(sellerId)
                .productName("FE Yoga Mat Premium").productSlug("fe-yoga-mat-premium")
                .productDescription("Active home product for category browsing and stock validation.")
                .productAttributes(Map.of("brand", "FE", "thickness", "6mm"))
                .categoryId(catHome).categoryPath("FE Home")
                .categoryName("FE Home").categorySlug("fe-home")
                .categorySlugPath(List.of("fe-home"))
                .variantAttributes(Map.of("color", "purple", "thickness", "6mm"))
                .skuCode("FE-SKU-YOGA-MAT").price(350000.0).originalPrice(490000.0)
                .hasDiscount(true).stockStatus("in_stock")
                .productStatus("ACTIVE").skuStatus("ACTIVE").isActive(true)
                .thumbnailUrl("https://picsum.photos/seed/fe-yoga-hero/800/800")
                .skuImageUrl("https://picsum.photos/seed/fe-yoga-mat/500/500")
                .sellerName(sellerName).sortId(900113).build());

        // --- 202: FE Travel Backpack (Fashion) ---
        docs.add(SearchDocument.builder()
                .skuId("90000000-0000-4000-9001-000000000202")
                .productId("90000000-0000-4000-8001-000000000114").sellerId(sellerId)
                .productName("FE Travel Backpack").productSlug("fe-travel-backpack")
                .productDescription("Active fashion product for search and compare features.")
                .productAttributes(Map.of("brand", "FE", "capacity", "40L"))
                .categoryId(catFashion).categoryPath("FE Fashion")
                .categoryName("FE Fashion").categorySlug("fe-fashion")
                .categorySlugPath(List.of("fe-fashion"))
                .variantAttributes(Map.of("color", "gray", "capacity", "40L"))
                .skuCode("FE-SKU-TRAVEL-BACKPACK").price(890000.0).originalPrice(1090000.0)
                .hasDiscount(true).stockStatus("in_stock")
                .productStatus("ACTIVE").skuStatus("ACTIVE").isActive(true)
                .thumbnailUrl("https://picsum.photos/seed/fe-backpack-hero/800/800")
                .skuImageUrl("https://picsum.photos/seed/fe-travel-backpack/500/500")
                .sellerName(sellerName).sortId(900114).build());

        // --- 203: FE Bluetooth Earbuds Pro (Audio, flash sale) ---
        docs.add(SearchDocument.builder()
                .skuId("90000000-0000-4000-9001-000000000203")
                .productId("90000000-0000-4000-8001-000000000115").sellerId(sellerId)
                .productName("FE Bluetooth Earbuds Pro").productSlug("fe-bluetooth-earbuds-pro")
                .productDescription("Premium audio product for flash-sale and recommendation tests.")
                .productAttributes(Map.of("brand", "FE", "battery", "8h"))
                .categoryId(catAudio).categoryPath("FE Electronics > FE Audio")
                .categoryName("FE Audio").categorySlug("fe-audio")
                .categorySlugPath(List.of("fe-electronics", "fe-audio"))
                .variantAttributes(Map.of("color", "black", "anc", true))
                .skuCode("FE-SKU-EARBUDS-PRO").price(1590000.0).originalPrice(1990000.0)
                .hasDiscount(true).flashSessionId("900001").stockStatus("in_stock")
                .productStatus("ACTIVE").skuStatus("ACTIVE").isActive(true)
                .thumbnailUrl("https://picsum.photos/seed/fe-earbuds-hero/800/800")
                .skuImageUrl("https://picsum.photos/seed/fe-earbuds-pro/500/500")
                .sellerName(sellerName).sortId(900115).build());

        try {
            esService.bulkIndex(docs);
            log.info("[SearchDevDataLoader] FE test-dataset seeded ({} documents into skus index).", docs.size());
        } catch (IOException e) {
            log.error("[SearchDevDataLoader] Failed to bulk-index FE documents: {}", e.getMessage(), e);
        }
    }
}