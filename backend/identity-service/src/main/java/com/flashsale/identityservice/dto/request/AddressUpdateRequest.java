package com.flashsale.identityservice.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddressUpdateRequest {
    @JsonProperty("province_id")
    private Integer provinceId;
    @JsonProperty("district_id")
    private Integer districtId;
    @JsonProperty("full_address")
    private String fullAddress;
    @JsonProperty("is_default")
    private Boolean isDefault;
}
