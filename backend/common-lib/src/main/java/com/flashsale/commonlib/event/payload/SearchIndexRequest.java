package com.flashsale.commonlib.event.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchIndexRequest {
    private String correlationId;
    private SearchIndexRequestType requestType;
    private String productId;
    private String categoryId;
    private Integer page;
    private Integer size;
}
