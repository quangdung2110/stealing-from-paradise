package com.flashsale.flashsaleservice.service;

import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class FlashSaleTimeValidator {

    private static final Duration MIN_LEAD_TIME = Duration.ofHours(1);
    private static final Duration MAX_DURATION  = Duration.ofHours(24);

    public void validate(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime now = LocalDateTime.now();
        if (startTime == null || endTime == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "startTime và endTime là bắt buộc");
        }
        if (startTime.isBefore(now.plus(MIN_LEAD_TIME))) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "startTime phải cách hiện tại ít nhất " + MIN_LEAD_TIME.toHours() + " giờ");
        }
        if (!endTime.isAfter(startTime)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "endTime phải sau startTime");
        }
        if (Duration.between(startTime, endTime).compareTo(MAX_DURATION) > 0) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Phiên không được dài quá " + MAX_DURATION.toHours() + " giờ");
        }
    }
}
