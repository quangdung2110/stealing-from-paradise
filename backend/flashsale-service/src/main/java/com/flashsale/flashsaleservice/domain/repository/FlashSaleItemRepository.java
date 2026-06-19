package com.flashsale.flashsaleservice.domain.repository;

import com.flashsale.flashsaleservice.domain.model.FlashSaleItem;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface FlashSaleItemRepository extends ReactiveCrudRepository<FlashSaleItem, Long> {
    Flux<FlashSaleItem> findBySessionId(Long sessionId);
    Mono<FlashSaleItem> findBySkuCode(String skuCode);
    Mono<Void> deleteBySessionId(Long sessionId);
}
