package com.flashsale.commonlib.event.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchIndexResponse {
    private String correlationId;
    private SearchIndexRequestType requestType;
    private Boolean success;
    private String errorMessage;
    private List<SearchIndexDocumentPayload> documents;
    private Map<String, Object> fields;
    private Integer page;
    private Integer size;
    private Long totalElements;
    private Boolean hasNext;
}
