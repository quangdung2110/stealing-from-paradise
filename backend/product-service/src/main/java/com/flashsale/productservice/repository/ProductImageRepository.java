package com.flashsale.productservice.repository;

import com.flashsale.productservice.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    List<ProductImage> findByProductIdOrderBySortOrderAsc(UUID productId);

    List<ProductImage> findByVariantIdOrderBySortOrderAsc(UUID variantId);

    boolean existsByUrl(String url);
}
