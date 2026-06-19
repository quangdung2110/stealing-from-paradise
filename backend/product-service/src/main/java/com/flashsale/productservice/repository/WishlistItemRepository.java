package com.flashsale.productservice.repository;

import com.flashsale.productservice.entity.WishlistItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, WishlistItem.WishlistItemId> {

    Page<WishlistItem> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    Optional<WishlistItem> findByCustomerIdAndProductId(Long customerId, UUID productId);

    boolean existsByCustomerIdAndProductId(Long customerId, UUID productId);

    long countByCustomerId(Long customerId);
}
