package com.flashsale.paymentservice.support;

import java.util.Map;

public class StripeMetadata {
    public static Long extractParentOrderId(Map<String, String> metadata) {
        if (metadata == null) return null;
        try {
            return Long.parseLong(metadata.get("parent_order_id"));
        } catch (Exception e) {
            return null;
        }
    }

    public static Long extractOrderId(Map<String, String> metadata) {
        if (metadata == null) return null;
        try {
            return Long.parseLong(metadata.get("order_id"));
        } catch (Exception e) {
            return null;
        }
    }


    public static Long extractUserId(Map<String, String> metadata) {
        if (metadata == null) return null;
        try {
            return Long.parseLong(metadata.get("user_id"));
        } catch (Exception e) {
            return null;
        }
    }
}

