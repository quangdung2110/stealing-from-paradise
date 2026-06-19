package com.flashsale.chatservice.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OrderIdNormalizer {

    private static final Pattern TRAILING_DIGITS = Pattern.compile("(\\d+)$");

    private OrderIdNormalizer() {
    }

    static String normalize(String orderIdOrCode) {
        if (orderIdOrCode == null) {
            return null;
        }
        String trimmed = orderIdOrCode.trim();
        if (trimmed.matches("\\d+")) {
            return trimmed;
        }

        Matcher matcher = TRAILING_DIGITS.matcher(trimmed);
        return matcher.find() ? matcher.group(1) : trimmed;
    }
}
