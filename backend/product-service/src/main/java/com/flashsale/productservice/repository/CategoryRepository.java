package com.flashsale.productservice.repository;

import com.flashsale.productservice.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Optional<Category> findBySlug(String slug);

    List<Category> findByParentIdIsNullAndDeletedAtIsNull();

    List<Category> findByParentIdAndDeletedAtIsNull(UUID parentId);

    List<Category> findByParentIdIsNotNullAndDeletedAtIsNull();

    @Query("SELECT COUNT(p) FROM Product p WHERE p.categoryId = :categoryId AND p.deletedAt IS NULL")
    Long countProductsByCategoryId(@Param("categoryId") UUID categoryId);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);
}
