package com.flashsale.productservice.repository;

import com.flashsale.productservice.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, CartItem.CartItemId> {

    Optional<CartItem> findByCustomerIdAndVariantId(Long customerId, UUID variantId);

    List<CartItem> findByCustomerId(Long customerId);

    @Query("SELECT ci FROM CartItem ci WHERE ci.customerId = :customerId AND ci.variantId IN :variantIds")
    List<CartItem> findByCustomerIdAndVariantIds(
            @Param("customerId") Long customerId,
            @Param("variantIds") List<UUID> variantIds);

    @Modifying
    @Query("DELETE FROM CartItem ci WHERE ci.customerId = :customerId AND ci.variantId = :variantId")
    void deleteByCustomerIdAndVariantId(
            @Param("customerId") Long customerId,
            @Param("variantId") UUID variantId);

    @Modifying
    @Query("DELETE FROM CartItem ci WHERE ci.customerId = :customerId")
    void deleteAllByCustomerId(@Param("customerId") Long customerId);

    @Modifying
    @Query("DELETE FROM CartItem ci WHERE ci.customerId = :customerId AND ci.variantId IN :variantIds")
    void deleteAllByCustomerIdAndVariantIds(
            @Param("customerId") Long customerId,
            @Param("variantIds") List<UUID> variantIds);
}
