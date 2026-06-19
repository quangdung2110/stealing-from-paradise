package com.flashsale.paymentservice.stripe.webhook;

import com.stripe.model.Event;

public interface StripeEventHandler {
    void handle(Event event);
}
