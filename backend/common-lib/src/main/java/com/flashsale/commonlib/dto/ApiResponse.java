package com.flashsale.commonlib.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private String  message;
    private T       data;
    private String  errorCode;
    private long    timestamp;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .success(true).data(data).timestamp(System.currentTimeMillis()).build();
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
            .success(true).message(message).data(data).timestamp(System.currentTimeMillis()).build();
    }

    public static <T> ApiResponse<T> error(String errorCode, String message) {
        return ApiResponse.<T>builder()
            .success(false).errorCode(errorCode).message(message).timestamp(System.currentTimeMillis()).build();
    }
}

