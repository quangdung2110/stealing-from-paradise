package com.flashsale.productservice.repository;

import com.flashsale.productservice.entity.ProductVariant;
import com.flashsale.productservice.entity.VariantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    Optional<ProductVariant> findByVariantCode(String code);

    List<ProductVariant> findByProductIdAndDeletedAtIsNull(UUID productId);

    @Modifying
    @Query("UPDATE ProductVariant v SET v.stockQuantity = v.stockQuantity + :delta WHERE v.id = :variantId")
    int incrementStock(@Param("variantId") UUID variantId, @Param("delta") int delta);

    /**
     * Atomic compare-and-decrement stock at the database level.
     * Replaces the previous pessimistic-lock pattern. Returns 1 if the row was
     * updated, 0 if the variant was deleted or did not have enough stock.
     */
    @Modifying
    @Query("UPDATE ProductVariant v SET v.stockQuantity = v.stockQuantity - :qty " +
           "WHERE v.id = :id AND v.stockQuantity >= :qty AND v.deletedAt IS NULL")
    int decrementIfEnough(@Param("id") UUID id, @Param("qty") int qty);

    /**
     * Atomic increment used for release / return / restock compensation.
     * Returns 1 if the row was updated, 0 if the variant was deleted / missing.
     */
    @Modifying
    @Query("UPDATE ProductVariant v SET v.stockQuantity = v.stockQuantity + :qty " +
           "WHERE v.id = :id AND v.deletedAt IS NULL")
    int incrementBy(@Param("id") UUID id, @Param("qty") int qty);

    @Query("SELECT SUM(v.stockQuantity) FROM ProductVariant v WHERE v.productId = :productId AND v.deletedAt IS NULL")
    Integer getTotalStockByProductId(@Param("productId") UUID productId);

    @Query("SELECT COUNT(v) FROM ProductVariant v WHERE v.productId = :productId AND v.deletedAt IS NULL")
    Integer countByProductId(@Param("productId") UUID productId);

    @Query("SELECT MIN(v.price) FROM ProductVariant v WHERE v.productId = :productId AND v.deletedAt IS NULL")
    BigDecimal findMinPriceByProductId(@Param("productId") UUID productId);

    List<ProductVariant> findByProductIdAndStatus(UUID productId, VariantStatus status);

    @Query("SELECT v FROM ProductVariant v WHERE v.productId = :productId AND v.status = 'OUT_OF_STOCK' AND v.deletedAt IS NULL")
    List<ProductVariant> findOutOfStockByProductId(@Param("productId") UUID productId);

    @Query("SELECT v FROM ProductVariant v WHERE v.originalPrice IS NOT NULL AND v.deletedAt IS NULL")
    List<ProductVariant> findByOriginalPriceNotNull();

    /**
     * Returns variants belonging to marketplace-visible, non-deleted products.
     * Used by search-service's reindex pipeline to build the ES catalog
     * (one SKU = one doc).
     */
    @Query("SELECT v FROM ProductVariant v " +
           "WHERE v.deletedAt IS NULL " +
           "  AND v.productId IN (SELECT p.id FROM Product p " +
           "                       WHERE p.status IN ('ACTIVE', 'OUT_OF_STOCK') " +
           "                         AND p.deletedAt IS NULL)")
    Page<ProductVariant> findVariantsOfSearchVisibleProducts(Pageable pageable);
}
