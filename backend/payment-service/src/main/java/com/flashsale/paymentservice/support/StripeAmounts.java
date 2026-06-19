package com.flashsale.paymentservice.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class StripeAmounts {
    public static long toStripeAmount(BigDecimal amount) {
        // VND is a zero-decimal currency in Stripe — amount is already in the smallest unit
        return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public static String buildTransRef(Long parentOrderId) {
        return "TXN-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-" + parentOrderId;
    }

    public static Long toLong(Object value) {
        if (value == null) return null;
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    public static BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
