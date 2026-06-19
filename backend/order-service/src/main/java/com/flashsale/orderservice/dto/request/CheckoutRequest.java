package com.flashsale.orderservice.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CheckoutRequest {

    @NotNull(message = "address_id là bắt buộc")
    private Long addressId;

    @NotEmpty(message = "item_ids không được rỗng")
    @Size(min = 1, max = 50, message = "item_ids phải từ 1 đến 50 phần tử")
    private List<String> itemIds;

    private String previewToken;
}
