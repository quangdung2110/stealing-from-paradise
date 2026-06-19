package com.flashsale.productservice.dto.checkout;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutSubmitRequest {

    @NotBlank(message = "preview_token là bắt buộc")
    private String previewToken;

    @NotNull(message = "address_id là bắt buộc")
    private Long addressId;

    private Long provinceId;

    private Long districtId;

    private String fullAddress;
}
