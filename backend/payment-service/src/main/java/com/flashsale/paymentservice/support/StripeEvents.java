package com.flashsale.paymentservice.support;

import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class StripeEvents {

    private StripeEvents() {}

    /**
     * Safely deserialize an Event's data object. Falls back to deserializeUnsafe()
     * when the SDK's pinned API version differs from the webhook event's API version
     * (in which case getObject() returns Optional.empty()).
     */
    public static StripeObject deserialize(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        StripeObject obj = deserializer.getObject().orElse(null);
        if (obj != null) return obj;
        try {
            return deserializer.deserializeUnsafe();
        } catch (Exception e) {
            log.error("Failed to deserialize Stripe event {} (type={}): {}",
                    event.getId(), event.getType(), e.getMessage());
            return null;
        }
    }
}
