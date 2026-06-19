package com.flashsale.flashsaleservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("fs_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashSaleItem {

    @Id
    private Long id;

    @Column("session_id")
    private Long sessionId;

    @Column("seller_id")
    private Long sellerId;

    @Column("sku_code")
    private String skuCode;

    @Column("flash_price")
    private BigDecimal flashPrice;

    @Column("flash_stock")
    private Integer flashStock;

    @Default
    @Column("limit_per_user")
    private Integer limitPerUser = 1;

    @Default
    @Column("sold_qty")
    private Integer soldQty = 0;

    @Default
    @Column("status")
    private String status = "APPROVED";

    @Version
    private Integer version;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;
}
