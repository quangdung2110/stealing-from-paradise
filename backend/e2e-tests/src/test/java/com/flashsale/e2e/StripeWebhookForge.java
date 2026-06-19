package com.flashsale.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stripe.Stripe;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Builds Stripe webhook events with a valid HMAC signature so the E2E suite can
 * drive payment outcomes through payment-service's real verification and handler
 * code without depending on Stripe's webhook delivery (the only simulated part
 * is the HTTP delivery itself — signature checking, event parsing and all
 * downstream Kafka/saga processing run for real).
 *
 * The event's api_version is taken from the same stripe-java version that
 * payment-service pins (26.1.0), otherwise EventDataObjectDeserializer refuses
 * to deserialize the payload.
 */
final class StripeWebhookForge {

    private static final ObjectMapper JSON = new ObjectMapper();

    private StripeWebhookForge() {}

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static ObjectNode baseEvent(String eventId, String eventType, String apiVersion) {
        ObjectNode event = JSON.createObjectNode();
        event.put("id", eventId);
        event.put("object", "event");
        event.put("api_version", apiVersion);
        event.put("created", Instant.now().getEpochSecond());
        event.put("livemode", false);
        event.put("pending_webhooks", 1);
        event.put("type", eventType);
        return event;
    }

    // ─── PaymentIntent ──────────────────────────────────────────────────────

    /** A payment_intent.* event whose metadata routes to the given parent order. */
    static String paymentIntentEvent(String eventType, long parentOrderId) {
        String id = "pi_e2e_" + suffix();

        ObjectNode pi = JSON.createObjectNode();
        pi.put("id", id);
        pi.put("object", "payment_intent");
        pi.put("amount", 100000L);
        pi.put("currency", "vnd");
        pi.put("status", "payment_intent.succeeded".equals(eventType) ? "succeeded" : "requires_payment_method");
        pi.putObject("metadata").put("parent_order_id", String.valueOf(parentOrderId));
        // Charge id needed by charge.refunded routing
        pi.put("latest_charge", "ch_e2e_" + suffix());

        ObjectNode event = baseEvent("evt_e2e_" + suffix(), eventType, Stripe.API_VERSION);
        event.putObject("data").set("object", pi);
        return event.toString();
    }

    // ─── Charge ─────────────────────────────────────────────────────────────

    static String chargeRefundedEvent(long parentOrderId, long amountRefunded) {
        String chargeId = "ch_e2e_" + suffix();

        ObjectNode ch = JSON.createObjectNode();
        ch.put("id", chargeId);
        ch.put("object", "charge");
        ch.put("amount", 100000L);
        ch.put("amount_refunded", amountRefunded);
        ch.put("currency", "vnd");
        ch.put("status", "succeeded");
        ch.putObject("metadata").put("parent_order_id", String.valueOf(parentOrderId));

        ObjectNode event = baseEvent("evt_e2e_" + suffix(), "charge.refunded", Stripe.API_VERSION);
        event.putObject("data").set("object", ch);
        return event.toString();
    }

    // ─── Dispute ────────────────────────────────────────────────────────────

    static String disputeCreatedEvent(String chargeId, long amount, String reason) {
        String disputeId = "dp_e2e_" + suffix();

        ObjectNode dp = JSON.createObjectNode();
        dp.put("id", disputeId);
        dp.put("object", "dispute");
        dp.put("charge", chargeId);
        dp.put("amount", amount);
        dp.put("currency", "vnd");
        dp.put("reason", reason);
        dp.put("status", "needs_response");

        ObjectNode event = baseEvent("evt_e2e_" + suffix(), "charge.dispute.created", Stripe.API_VERSION);
        event.putObject("data").set("object", dp);
        return event.toString();
    }

    static String disputeClosedEvent(String disputeId, long amount) {
        ObjectNode dp = JSON.createObjectNode();
        dp.put("id", disputeId);
        dp.put("object", "dispute");
        dp.put("charge", "ch_e2e_" + suffix());
        dp.put("amount", amount);
        dp.put("currency", "vnd");
        dp.put("status", "won");

        ObjectNode event = baseEvent("evt_e2e_" + suffix(), "charge.dispute.closed", Stripe.API_VERSION);
        event.putObject("data").set("object", dp);
        return event.toString();
    }

    // ─── Transfer ───────────────────────────────────────────────────────────

    static String transferEvent(String eventType, long orderId, String transferId) {
        ObjectNode tr = JSON.createObjectNode();
        tr.put("id", transferId);
        tr.put("object", "transfer");
        tr.put("amount", 50000L);
        tr.put("currency", "vnd");
        tr.put("status", "paid");
        if ("transfer.reversed".equals(eventType)) {
            tr.put("amount_reversed", 50000L);
            tr.put("status", "reversed");
        }
        tr.putObject("metadata").put("order_id", String.valueOf(orderId));

        ObjectNode event = baseEvent("evt_e2e_" + suffix(), eventType, Stripe.API_VERSION);
        event.putObject("data").set("object", tr);
        return event.toString();
    }

    // ─── Account ────────────────────────────────────────────────────────────

    static String accountUpdatedEvent(String accountId, boolean detailsSubmitted,
                                       boolean chargesEnabled, boolean payoutsEnabled) {
        return accountUpdatedEvent(accountId, detailsSubmitted, chargesEnabled, payoutsEnabled,
                java.util.List.of(), null);
    }

    /** account.updated with optional requirements.currently_due and disabled_reason (KYC edge cases). */
    static String accountUpdatedEvent(String accountId, boolean detailsSubmitted,
                                       boolean chargesEnabled, boolean payoutsEnabled,
                                       java.util.List<String> currentlyDue, String disabledReason) {
        ObjectNode acct = JSON.createObjectNode();
        acct.put("id", accountId);
        acct.put("object", "account");
        acct.put("details_submitted", detailsSubmitted);
        acct.put("charges_enabled", chargesEnabled);
        acct.put("payouts_enabled", payoutsEnabled);
        ObjectNode req = acct.putObject("requirements");
        com.fasterxml.jackson.databind.node.ArrayNode due = req.putArray("currently_due");
        for (String item : currentlyDue) due.add(item);
        if (disabledReason != null) req.put("disabled_reason", disabledReason);

        ObjectNode event = baseEvent("evt_e2e_" + suffix(), "account.updated", Stripe.API_VERSION);
        event.putObject("data").set("object", acct);
        return event.toString();
    }

    // ─── Payout ─────────────────────────────────────────────────────────────

    static String payoutEvent(String eventType, String payoutId, long amount) {
        ObjectNode po = JSON.createObjectNode();
        po.put("id", payoutId);
        po.put("object", "payout");
        po.put("amount", amount);
        po.put("currency", "vnd");
        po.put("status", "payout.paid".equals(eventType) ? "paid" : "in_transit");

        ObjectNode event = baseEvent("evt_e2e_" + suffix(), eventType, Stripe.API_VERSION);
        event.putObject("data").set("object", po);
        return event.toString();
    }

    /** Stripe-Signature header: t=&lt;ts&gt;,v1=HMAC_SHA256(secret, "&lt;ts&gt;.&lt;payload&gt;"). */
    static String signatureHeader(String payload, String webhookSecret) {
        long timestamp = Instant.now().getEpochSecond();
        String signedPayload = timestamp + "." + payload;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return "t=" + timestamp + ",v1=" + hex;
        } catch (Exception e) {
            throw new AssertionError("Could not sign webhook payload", e);
        }
    }
}
