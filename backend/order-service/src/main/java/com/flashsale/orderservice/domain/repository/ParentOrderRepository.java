package com.flashsale.orderservice.domain.repository;

import com.flashsale.orderservice.domain.model.ParentOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ParentOrderRepository extends JpaRepository<ParentOrder, Long> {

    // NOTE: DO NOT add @PostAuthorize to findById() — it is called from
    // Kafka consumer threads where SecurityContext is absent.
    @Override
    Optional<ParentOrder> findById(Long id);

    Optional<ParentOrder> findByIdAndCustomerId(Long id, Long customerId);

    /** Lookup the parent order by its checkout session id (used by stock-reservation-expired handling). */
    Optional<ParentOrder> findBySessionId(String sessionId);

    /**
     * Pessimistic lock on ParentOrder during payment confirmation/failure.
     * Required because ParentOrder has @Version (optimistic locking) and may be
     * modified concurrently by other transactions (e.g. payment timeout, another
     * saga instance). Without this lock, an ObjectOptimisticLockingFailureException
     * is thrown when the saga's transaction commits after another transaction has
     * already incremented the version.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT po FROM ParentOrder po WHERE po.id = :id")
    Optional<ParentOrder> findByIdWithPessimisticLock(Long id);

    @Query("SELECT po FROM ParentOrder po LEFT JOIN FETCH po.orders WHERE po.id = :id AND po.customerId = :customerId")
    Optional<ParentOrder> findByIdAndCustomerIdWithOrders(Long id, Long customerId);
}
