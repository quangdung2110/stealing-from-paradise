package com.flashsale.commonlib.stripe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class StripeClientSecretExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private StripeClientSecretExtractor() {}

    /**
     * Extracts the client_secret from the raw Stripe PaymentIntent JSON
     * stored in the Transaction.raw_response column.
     *
     * The raw JSON looks like:
     * {
     *   "id": "pi_xxx",
     *   "object": "payment_intent",
     *   "client_secret": "pi_xxx_secret_yyy",
     *   ...
     * }
     *
     * @param rawResponse the raw JSON string from Stripe
     * @return the client_secret string, or null if not found
     */
    public static String extract(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(rawResponse);
            JsonNode secretNode = node.get("client_secret");
            return (secretNode != null && !secretNode.isNull())
                    ? secretNode.asText()
                    : null;
        } catch (Exception e) {
            return null;
        }
    }
}
