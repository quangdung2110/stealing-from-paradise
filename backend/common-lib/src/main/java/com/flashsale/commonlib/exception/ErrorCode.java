package com.flashsale.commonlib.exception;

public enum ErrorCode {
    // Auth
    UNAUTHORIZED("AUTH_001", "Chưa xác thực",                       401),
    FORBIDDEN("AUTH_002",    "Không có quyền truy cập",              403),
    TOKEN_EXPIRED("AUTH_003", "Token đã hết hạn",                    401),
    TOKEN_INVALID("AUTH_004", "Token không hợp lệ",                  401),
    TOKEN_REVOKED("AUTH_005", "Token đã bị thu hồi",                 401),
    // Resource
    NOT_FOUND("RES_001",       "Không tìm thấy tài nguyên",          404),
    ALREADY_EXISTS("RES_002",  "Tài nguyên đã tồn tại",              409),
    OPTIMISTIC_LOCK("RES_003", "Xung đột dữ liệu, thử lại",         409),
    CONFLICT("RES_004",         "Xung đột dữ liệu",                   409),
    // Business
    INSUFFICIENT_STOCK("BIZ_001",     "Không đủ hàng",                 400),
    ORDER_NOT_CANCELLABLE("BIZ_002",  "Đơn hàng không thể hủy",        400),
    PAYMENT_FAILED("BIZ_003",        "Thanh toán thất bại",            402),
    FLASH_SALE_ENDED("BIZ_004",      "Flash Sale đã kết thúc",         410),
    LIMIT_PER_USER_EXCEEDED("BIZ_005","Vượt giới hạn mua mỗi người",   400),
    // Validation / System
    BAD_REQUEST("VAL_000", "Yêu cầu không hợp lệ",                       400),
    VALIDATION_FAILED("VAL_001",   "Dữ liệu không hợp lệ",           400),
    RATE_LIMIT_EXCEEDED("VAL_002", "Quá nhiều yêu cầu",               429),
    INTERNAL_ERROR("SYS_001",     "Lỗi hệ thống",                    500);

    private final String code;
    private final String defaultMessage;
    private final int    httpStatus;

    ErrorCode(String code, String defaultMessage, int httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    public String getCode() { return code; }
    public String getDefaultMessage() { return defaultMessage; }
    public int getHttpStatus() { return httpStatus; }
}

