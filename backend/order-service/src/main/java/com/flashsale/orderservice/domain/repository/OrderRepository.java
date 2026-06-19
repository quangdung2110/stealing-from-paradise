package com.flashsale.orderservice.domain.repository;

import com.flashsale.orderservice.domain.model.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    // NOTE: DO NOT add @PostAuthorize to findById() — it is called from
    // Kafka consumer threads and Axon saga/deadline threads where
    // SecurityContext is absent, causing IllegalArgumentException.
    @Override
    Optional<Order> findById(Long id);

    Optional<Order> findByOrderCode(String orderCode);

    Optional<Order> findByIdAndCustomerId(Long id, Long customerId);

    Optional<Order> findByIdAndSellerId(Long id, Long sellerId);

    // Buyer: lấy danh sách đơn hàng, lọc theo status và ngày
    @Query("""
        SELECT o FROM Order o
        WHERE o.customerId = :customerId
          AND (CAST(:status AS string) IS NULL OR o.status = :status)
          AND (CAST(:fromDate AS timestamp) IS NULL OR o.createdAt >= :fromDate)
          AND (CAST(:toDate   AS timestamp) IS NULL OR o.createdAt <= :toDate)
        ORDER BY o.createdAt DESC
        """)
    Page<Order> findByCustomerIdWithFilters(
            @Param("customerId")   Long customerId,
            @Param("status")   String status,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate")   LocalDateTime toDate,
            Pageable pageable);

    // Payment service: cập nhật trạng thái theo parent_order_id
    List<Order> findAllByParentOrderIdAndStatus(Long parentOrderId, String status);

    /**
     * Pessimistic lock on sub-orders during payment confirmation.
     * Prevents concurrent saga instances from reading stale data while
     * another transaction is modifying the same parent_order's sub-orders.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.parentOrderId = :parentOrderId AND o.status = :status")
    List<Order> findAllByParentOrderIdAndStatusWithLock(
            @Param("parentOrderId") Long parentOrderId,
            @Param("status") String status);

    List<Order> findAllByParentOrderId(Long parentOrderId);

    // Seller: lấy danh sách đơn hàng
    @Query("""
        SELECT o FROM Order o
        WHERE o.sellerId = :sellerId
          AND (CAST(:status AS string) IS NULL OR o.status = :status)
          AND (CAST(:fromDate AS timestamp) IS NULL OR o.createdAt >= :fromDate)
          AND (CAST(:toDate   AS timestamp) IS NULL OR o.createdAt <= :toDate)
        ORDER BY o.createdAt DESC
        """)
    Page<Order> findBySellerIdWithFilters(
            @Param("sellerId") Long sellerId,
            @Param("status")   String status,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate")   LocalDateTime toDate,
            Pageable pageable);

    // ─── Dashboard stats ───────────────────────────────────────────────────────

    long countBySellerIdAndCreatedAtAfter(Long sellerId, LocalDateTime after);

    long countBySellerIdAndStatus(Long sellerId, String status);

    // ─── Lifecycle scheduler (JOB-13 / JOB-22) ────────────────────────────────

    List<Order> findAllByStatusAndCreatedAtBefore(String status, LocalDateTime cutoff);

    List<Order> findAllByStatusAndUpdatedAtBefore(String status, LocalDateTime cutoff);

    @Query("""
        SELECT COALESCE(SUM(o.finalAmt), 0) FROM Order o
        WHERE o.sellerId = :sellerId
          AND o.status <> 'CANCELLED'
          AND o.createdAt >= :since
        """)
    BigDecimal sumRevenueForSellerSince(@Param("sellerId") Long sellerId, @Param("since") LocalDateTime since);
}
