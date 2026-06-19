package com.flashsale.searchservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;

import java.util.List;
import java.util.Map;

@Document(indexName = "skus", createIndex = false)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchDocument {

    @Id
    private String skuId;

    @Field(type = FieldType.Keyword)
    private String productId;

    @Field(type = FieldType.Long)
    private Long sellerId;

    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "vietnamese_analyzer"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword)
        }
    )
    private String productName;

    @Field(type = FieldType.Keyword)
    private String productSlug;

    @Field(type = FieldType.Text, analyzer = "vietnamese_analyzer")
    private String productDescription;

    @Field(type = FieldType.Object, enabled = true)
    private Map<String, Object> productAttributes;

    @Field(type = FieldType.Keyword)
    private String categoryId;

    @Field(type = FieldType.Keyword)
    private String categoryPath;

    @Field(type = FieldType.Keyword)
    private String categoryName;

    @Field(type = FieldType.Keyword)
    private String categorySlug;

    @Field(type = FieldType.Keyword)
    private List<String> categorySlugPath;

    @Field(type = FieldType.Object, enabled = true)
    private Map<String, Object> variantAttributes;

    @Field(type = FieldType.Keyword)
    private String skuCode;

    @Field(type = FieldType.Double)
    private Double price;

    @Field(type = FieldType.Double)
    private Double originalPrice;

    @Field(type = FieldType.Boolean)
    private Boolean hasDiscount;

    @Field(type = FieldType.Keyword)
    private String flashSessionId;

    @Field(type = FieldType.Keyword)
    private String stockStatus;

    @Field(type = FieldType.Keyword)
    private String productStatus;

    @Field(type = FieldType.Keyword)
    private String skuStatus;

    @Field(type = FieldType.Boolean)
    private Boolean isActive;

    @Field(type = FieldType.Keyword, index = false)
    private String thumbnailUrl;

    @Field(type = FieldType.Keyword, index = false)
    private String skuImageUrl;

    @MultiField(
        mainField = @Field(type = FieldType.Text),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword)
        }
    )
    private String sellerName;

    @Field(type = FieldType.Integer)
    private Integer sortId;
}
