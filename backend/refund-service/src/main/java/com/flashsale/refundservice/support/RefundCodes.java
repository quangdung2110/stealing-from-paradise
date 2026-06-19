package com.flashsale.refundservice.support;

import com.flashsale.refundservice.domain.model.Refund;
import java.time.format.DateTimeFormatter;

public class RefundCodes {
    public static String buildRefundCode(Refund r) {
        return "RF-" + r.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + r.getId();
    }
}
