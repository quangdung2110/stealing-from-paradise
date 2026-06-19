package com.flashsale.identityservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerPublicResponse {
    private Long sellerId;
    private String sellerName;
    private String avatarUrl;
    private LocalDateTime joinedAt;
    private int productCount;
}
