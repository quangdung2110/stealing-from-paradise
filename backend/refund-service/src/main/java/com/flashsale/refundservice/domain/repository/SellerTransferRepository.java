package com.flashsale.refundservice.domain.repository;

import com.flashsale.refundservice.domain.model.SellerTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SellerTransferRepository extends JpaRepository<SellerTransfer, Long> {
    Optional<SellerTransfer> findByOrderId(Long orderId);
}
