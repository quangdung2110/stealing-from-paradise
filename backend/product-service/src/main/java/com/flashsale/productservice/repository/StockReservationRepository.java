package com.flashsale.productservice.repository;

import com.flashsale.productservice.entity.ReservationStatus;
import com.flashsale.productservice.entity.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

    List<StockReservation> findByVariantIdAndStatusAndExpiresAtBefore(
            UUID variantId, ReservationStatus status, LocalDateTime now);

    List<StockReservation> findByStatusAndExpiresAtBefore(ReservationStatus status, LocalDateTime now);

    @Query("SELECT SUM(sr.quantity) FROM StockReservation sr WHERE sr.variantId = :variantId AND sr.status = :status")
    Integer sumQuantityByVariantIdAndStatus(@Param("variantId") UUID variantId, @Param("status") ReservationStatus status);

    List<StockReservation> findBySessionIdAndStatus(String sessionId, ReservationStatus status);
}
