package com.flashsale.flashsaleservice.service;

import com.flashsale.flashsaleservice.domain.model.FlashSaleItem;
import com.flashsale.flashsaleservice.dto.response.FlashSaleItemResponse;
import org.springframework.stereotype.Component;

@Component
public class FlashSaleItemMapper {

    public FlashSaleItemResponse toItemResponse(FlashSaleItem i) {
        return FlashSaleItemResponse.builder()
                .id(i.getId())
                .sessionId(i.getSessionId())
                .sellerId(i.getSellerId())
                .skuCode(i.getSkuCode())
                .flashPrice(i.getFlashPrice())
                .flashStock(i.getFlashStock())
                .limitPerUser(i.getLimitPerUser())
                .soldQty(i.getSoldQty())
                .status(i.getStatus())
                .createdAt(i.getCreatedAt())
                .updatedAt(i.getUpdatedAt())
                .build();
    }
}
