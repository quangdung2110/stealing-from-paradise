package com.flashsale.refundservice.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.flashsale.refundservice.domain.model.RefundItem;
import java.util.List;

@Repository
public interface RefundItemRepository extends JpaRepository<RefundItem, Long> {
    List<RefundItem> findAllByRefundId(Long refundId);
}
