package com.flashsale.orderservice.client.dto;

import lombok.Data;

/**
 * Thông tin địa chỉ nhận từ Identity Service.
 */
@Data
public class AddressInfo {

    private Long addressId;
    private Long userId;
    private String fullAddress;
    private Integer provinceId;
    private Integer districtId;
}
