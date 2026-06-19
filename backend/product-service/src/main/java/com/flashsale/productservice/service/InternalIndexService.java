package com.flashsale.productservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.payload.SearchIndexDocumentPayload;
import com.flashsale.productservice.entity.Category;
import com.flashsale.productservice.entity.Product;
import com.flashsale.productservice.entity.ProductImage;
import com.flashsale.productservice.entity.ProductStatus;
import com.flashsale.productservice.entity.ProductVariant;
import com.flashsale.productservice.repository.CategoryRepository;
import com.flashsale.productservice.repository.ProductImageRepository;
import com.flashsale.productservice.repository.ProductRepository;
import com.flashsale.productservice.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds the SearchDocument feed consumed by search-service's Kafka indexing pipeline.
 *
 * <p>Strategy: page by VARIANT (one variant = one SearchDocument), then bulk-load
 * the related products / categories / primary images so total queries per page is
 * O(4) regardless of page size.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InternalIndexService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final CategoryRepository categoryRepository;
    private final ProductImageRepository productImageRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<SearchIndexDocumentPayload> buildActiveSearchDocuments(Pageable pageable) {
        Page<ProductVariant> variants = variantRepository.findVariantsOfSearchVisibleProducts(pageable);
        if (variants.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        List<SearchIndexDocumentPayload> docs = buildDocuments(variants.getContent(), (int) pageable.getOffset(), true);
        return new PageImpl<>(docs, pageable, variants.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<SearchIndexDocumentPayload> buildProductSearchDocuments(UUID productId) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElse(null);
        if (product == null || !isSearchVisible(product)) {
            return List.of();
        }

        List<ProductVariant> variants = variantRepository.findByProductIdAndDeletedAtIsNull(productId);
        return buildDocuments(variants, 0, true);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buildProductSearchFields(UUID productId) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElse(null);
        if (product == null) {
            return Collections.emptyMap();
        }

        Category category = product.getCategoryId() != null
                ? categoryRepository.findById(product.getCategoryId()).orElse(null)
                : null;
        Map<UUID, Category> categoriesById = loadCategoriesById();
        String thumbnailUrl = productImageRepository.findByProductIdOrderBySortOrderAsc(product.getId())
                .stream()
                .findFirst()
                .map(ProductImage::getUrl)
                .orElse(null);

        Map<String, Object> fields = new HashMap<>();
        fields.put("productName", product.getName());
        fields.put("productSlug", product.getSlug());
        fields.put("productDescription", product.getDescription());
        fields.put("categoryId", product.getCategoryId() != null ? product.getCategoryId().toString() : null);
        fields.put("categoryName", category != null ? category.getName() : null);
        fields.put("categorySlug", category != null ? category.getSlug() : null);
        fields.put("categoryPath", buildCategoryNamePath(category, categoriesById));
        fields.put("categorySlugPath", buildCategorySlugPath(category, categoriesById));
        fields.put("productAttributes", parseJsonToMap(product.getAttributes()));
        fields.put("thumbnailUrl", thumbnailUrl);
        fields.put("sellerName", product.getSellerName() != null && !product.getSellerName().isBlank()
                ? product.getSellerName()
                : (product.getSellerId() != null ? "Seller " + product.getSellerId() : null));
        fields.put("productStatus", product.getStatus() != null ? product.getStatus().name() : null);
        fields.entrySet().removeIf(entry -> entry.getValue() == null);
        return fields;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buildCategorySearchFields(UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .filter(c -> c.getDeletedAt() == null)
                .orElse(null);
        if (category == null) {
            return Collections.emptyMap();
        }
        Map<UUID, Category> categoriesById = loadCategoriesById();

        Map<String, Object> fields = new HashMap<>();
        fields.put("categoryName", category.getName());
        fields.put("categorySlug", category.getSlug());
        fields.put("categoryPath", buildCategoryNamePath(category, categoriesById));
        fields.put("categorySlugPath", buildCategorySlugPath(category, categoriesById));
        return fields;
    }

    private List<SearchIndexDocumentPayload> buildDocuments(
            List<ProductVariant> variants,
            int initialSortCursor,
            boolean onlySearchVisibleProducts) {
        // ---- Bulk-load related entities (avoid N+1) ----
        Set<UUID> productIds = variants.stream()
                .map(ProductVariant::getProductId)
                .collect(Collectors.toSet());
        Map<UUID, Product> productsById = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        Map<UUID, Category> categoriesById = loadCategoriesById();

        // Primary image per product (sort_order=0 first). One query per product but
        // dataset is small; if perf matters, replace with a batched native query.
        Map<UUID, String> thumbnailByProduct = new HashMap<>();
        for (UUID pid : productIds) {
            List<ProductImage> imgs = productImageRepository.findByProductIdOrderBySortOrderAsc(pid);
            if (!imgs.isEmpty()) {
                thumbnailByProduct.put(pid, imgs.get(0).getUrl());
            }
        }

        // ---- Map ----
        List<SearchIndexDocumentPayload> docs = new ArrayList<>(variants.size());
        int sortCursor = initialSortCursor; // stable per-page sortId
        for (ProductVariant v : variants) {
            Product p = productsById.get(v.getProductId());
            if (p == null) continue;  // product was deleted between queries
            if (onlySearchVisibleProducts && !isSearchVisible(p)) continue;
            Category cat = (p.getCategoryId() != null) ? categoriesById.get(p.getCategoryId()) : null;

            BigDecimal price = v.getPrice() != null ? v.getPrice() : BigDecimal.ZERO;
            BigDecimal original = v.getOriginalPrice();
            boolean hasDiscount = original != null && original.compareTo(price) > 0;

            String stockStatus = (v.getStockQuantity() != null && v.getStockQuantity() > 0)
                    ? "in_stock" : "out_of_stock";

            docs.add(SearchIndexDocumentPayload.builder()
                    .skuId(v.getId().toString())
                    .productId(p.getId().toString())
                    .sellerId(p.getSellerId())
                    .productName(p.getName())
                    .productSlug(p.getSlug())
                    .productDescription(p.getDescription())
                    .productAttributes(parseJsonToMap(p.getAttributes()))
                    .categoryId(p.getCategoryId() != null ? p.getCategoryId().toString() : null)
                    .categoryName(cat != null ? cat.getName() : null)
                    .categorySlug(cat != null ? cat.getSlug() : null)
                    .categoryPath(buildCategoryNamePath(cat, categoriesById))
                    .categorySlugPath(buildCategorySlugPath(cat, categoriesById))
                    .variantAttributes(parseJsonToMap(v.getVariantAttributes()))
                    .skuCode(v.getVariantCode())
                    .price(price.doubleValue())
                    .originalPrice(original != null ? original.doubleValue() : null)
                    .hasDiscount(hasDiscount)
                    .stockStatus(stockStatus)
                    .productStatus(p.getStatus() != null ? p.getStatus().name() : null)
                    .skuStatus(v.getStatus() != null ? v.getStatus().name() : null)
                    .isActive(true)
                    .thumbnailUrl(thumbnailByProduct.get(p.getId()))
                    .skuImageUrl(v.getImageUrl())
                    .sellerName(p.getSellerName() != null && !p.getSellerName().isBlank()
                            ? p.getSellerName()
                            : (p.getSellerId() != null ? "Seller " + p.getSellerId() : null))
                    .sortId(sortCursor++)
                    .build());
        }

        return docs;
    }

    private boolean isSearchVisible(Product product) {
        return product.getStatus() == ProductStatus.ACTIVE
                || product.getStatus() == ProductStatus.OUT_OF_STOCK;
    }

    private Map<UUID, Category> loadCategoriesById() {
        return categoryRepository.findAll().stream()
                .filter(category -> category.getDeletedAt() == null)
                .collect(Collectors.toMap(Category::getId, category -> category));
    }

    private List<Category> buildCategoryLineage(Category category, Map<UUID, Category> categoriesById) {
        if (category == null) {
            return List.of();
        }

        List<Category> lineage = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        Category cursor = category;
        while (cursor != null && visited.add(cursor.getId())) {
            lineage.add(cursor);
            cursor = cursor.getParentId() != null ? categoriesById.get(cursor.getParentId()) : null;
        }
        Collections.reverse(lineage);
        return lineage;
    }

    private String buildCategoryNamePath(Category category, Map<UUID, Category> categoriesById) {
        List<Category> lineage = buildCategoryLineage(category, categoriesById);
        if (lineage.isEmpty()) {
            return null;
        }
        return lineage.stream()
                .map(Category::getName)
                .collect(Collectors.joining(" > "));
    }

    private List<String> buildCategorySlugPath(Category category, Map<UUID, Category> categoriesById) {
        return buildCategoryLineage(category, categoriesById).stream()
                .map(Category::getSlug)
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<String, Object> parseJsonToMap(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.debug("Cannot parse JSON attributes (returning empty): {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
