package com.flashsale.identityservice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressResponse {
    @JsonProperty("address_id")
    private Long addressId;
    @JsonProperty("province_id")
    private Integer provinceId;
    @JsonProperty("district_id")
    private Integer districtId;
    @JsonProperty("full_address")
    private String fullAddress;
    @JsonProperty("is_default")
    private Boolean isDefault;
}
