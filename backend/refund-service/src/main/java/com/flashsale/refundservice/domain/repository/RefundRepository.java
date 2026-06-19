package com.flashsale.refundservice.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.flashsale.refundservice.domain.model.Refund;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {
    Optional<Refund> findByOrderId(Long orderId);
    List<Refund> findAllByOrderId(Long orderId);

    @Query("""
        SELECT r FROM Refund r
        WHERE (CAST(:status AS string) IS NULL OR r.status = :status)
          AND (CAST(:type AS string) IS NULL OR r.type = :type)
          AND (CAST(:fromDate AS timestamp) IS NULL OR r.createdAt >= :fromDate)
          AND (CAST(:toDate AS timestamp) IS NULL OR r.createdAt <= :toDate)
        ORDER BY r.createdAt DESC
        """)
    Page<Refund> findAllWithFilters(
        @Param("status") String status,
        @Param("type") String type,
        @Param("fromDate") LocalDateTime fromDate,
        @Param("toDate") LocalDateTime toDate,
        Pageable pageable
    );

    @Query("""
        SELECT r FROM Refund r
        WHERE r.userId = :userId
          AND (CAST(:status AS string) IS NULL OR r.status = :status)
          AND (CAST(:type AS string) IS NULL OR r.type = :type)
          AND (CAST(:fromDate AS timestamp) IS NULL OR r.createdAt >= :fromDate)
          AND (CAST(:toDate AS timestamp) IS NULL OR r.createdAt <= :toDate)
        ORDER BY r.createdAt DESC
        """)
    Page<Refund> findAllByUserIdWithFilters(
        @Param("userId") Long userId,
        @Param("status") String status,
        @Param("type") String type,
        @Param("fromDate") LocalDateTime fromDate,
        @Param("toDate") LocalDateTime toDate,
        Pageable pageable
    );

    boolean existsByOrderIdAndStatus(Long orderId, String status);

    boolean existsByOrderIdAndStatusIn(Long orderId, List<String> statuses);

    Optional<Refund> findByRefundRef(String refundRef);

    /** Find refunds stuck in a status since before a date (for timeout schedulers). */
    @Query("SELECT r FROM Refund r WHERE r.status = :status AND r.createdAt <= :cutoff ORDER BY r.createdAt ASC")
    List<Refund> findByStatusAndCreatedAtBefore(
        @Param("status") String status,
        @Param("cutoff") LocalDateTime cutoff,
        org.springframework.data.domain.Pageable pageable
    );
}
