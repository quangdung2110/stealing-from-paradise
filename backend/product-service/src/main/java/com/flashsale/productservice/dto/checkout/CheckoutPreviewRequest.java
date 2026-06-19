package com.flashsale.productservice.dto.checkout;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutPreviewRequest {

    @NotEmpty(message = "item_ids không được rỗng")
    @Size(min = 1, max = 50, message = "item_ids phải từ 1 đến 50 phần tử")
    private List<String> itemIds;
}
