package com.flashsale.productservice.repository;

import com.flashsale.productservice.entity.Product;
import com.flashsale.productservice.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findBySlug(String slug);

    Page<Product> findBySellerIdAndDeletedAtIsNull(Long sellerId, Pageable pageable);

    Page<Product> findAllByDeletedAtIsNull(Pageable pageable);

    Page<Product> findByCategoryIdAndDeletedAtIsNull(UUID categoryId, Pageable pageable);

    Page<Product> findByStatusAndDeletedAtIsNull(ProductStatus status, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = :status AND p.deletedAt IS NULL")
    Page<Product> findByStatus(@Param("status") ProductStatus status, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = 'PENDING' AND p.deletedAt IS NULL " +
           "AND (:categoryId IS NULL OR p.categoryId = :categoryId) " +
           "AND (:sellerId IS NULL OR p.sellerId = :sellerId)")
    Page<Product> findPendingProducts(
            @Param("categoryId") UUID categoryId,
            @Param("sellerId") Long sellerId,
            Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = :status AND p.deletedAt IS NULL " +
           "AND (:categoryId IS NULL OR p.categoryId = :categoryId) " +
           "AND (:sellerId IS NULL OR p.sellerId = :sellerId)")
    Page<Product> findForModeration(
            @Param("status") ProductStatus status,
            @Param("categoryId") UUID categoryId,
            @Param("sellerId") Long sellerId,
            Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status IN :statuses AND p.deletedAt IS NULL")
    Page<Product> findByStatusIn(@Param("statuses") List<ProductStatus> statuses, Pageable pageable);

    // ─── Public listing (GET /v1/products) ────────────────────────────────────
    @Query(value = """
        SELECT DISTINCT p FROM Product p
        LEFT JOIN ProductVariant v ON v.productId = p.id AND v.deletedAt IS NULL
        WHERE p.status = com.flashsale.productservice.entity.ProductStatus.ACTIVE
          AND p.deletedAt IS NULL
          AND (:categoryId IS NULL OR p.categoryId = :categoryId)
          AND (:sellerId IS NULL OR p.sellerId = :sellerId)
          AND (:minPrice IS NULL OR v.price >= :minPrice)
          AND (:maxPrice IS NULL OR v.price <= :maxPrice)
        """,
        countQuery = """
        SELECT COUNT(DISTINCT p) FROM Product p
        LEFT JOIN ProductVariant v ON v.productId = p.id AND v.deletedAt IS NULL
        WHERE p.status = com.flashsale.productservice.entity.ProductStatus.ACTIVE
          AND p.deletedAt IS NULL
          AND (:categoryId IS NULL OR p.categoryId = :categoryId)
          AND (:sellerId IS NULL OR p.sellerId = :sellerId)
          AND (:minPrice IS NULL OR v.price >= :minPrice)
          AND (:maxPrice IS NULL OR v.price <= :maxPrice)
        """)
    Page<Product> searchPublic(
            @Param("categoryId") UUID categoryId,
            @Param("sellerId") Long sellerId,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable);

    // ─── Maintenance jobs (JOB-10 / JOB-16) ───────────────────────────────────
    List<Product> findAllByDeletedAtBefore(LocalDateTime cutoff);

    List<Product> findAllByStatusAndUpdatedAtBeforeAndDeletedAtIsNull(
            ProductStatus status, LocalDateTime cutoff);

    // ─── Seller info denormalization (synced via SellerInfoConsumer) ──────────
    @Modifying
    @Query("UPDATE Product p SET p.sellerName = :sellerName WHERE p.sellerId = :sellerId AND p.deletedAt IS NULL")
    int updateSellerNameForAllProducts(@Param("sellerId") Long sellerId, @Param("sellerName") String sellerName);
}
