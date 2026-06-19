package com.flashsale.refundservice.domain.repository;

import com.flashsale.refundservice.domain.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByParentOrderId(Long parentOrderId);

    // raw_response is jsonb in Postgres — use CAST for LIKE compatibility
    @Query(value = "SELECT * FROM payment.transactions t WHERE CAST(t.raw_response AS text) LIKE CONCAT('%', :chargeId, '%')", nativeQuery = true)
    Optional<Transaction> findByRawResponseContaining(@Param("chargeId") String chargeId);
}
