#!/usr/bin/env python3
"""
Stripe Webhook Event Forge — for manual E2E testing inside Docker sidecar.

Generates properly-structured, HMAC-signed Stripe webhook events and POSTs
them to the API Gateway. Mirrors the Java StripeWebhookForge in the E2E test
suite, but usable from a plain `python3` shell (no JVM needed).

Requirements:
  - Python 3.8+  (stdlib only — no pip packages)
  - Run from inside the Docker network (flashsale-net) so api-gateway:8080
    is reachable, OR set GATEWAY env var to a reachable gateway URL.

Usage examples:
  # payment_intent.succeeded for parent order 170
  python3 forge.py pi payment_intent.succeeded 170

  # payment_intent.payment_failed for parent order 171
  python3 forge.py pi payment_intent.payment_failed 171

  # charge.refunded for parent order 170, amount 50000
  python3 forge.py charge_refund 170 50000

  # transfer.created for order 186, transfer id tr_xxx
  python3 forge.py transfer transfer.created 186 tr_manual_001

  # transfer.reversed
  python3 forge.py transfer transfer.reversed 186 tr_manual_001

  # account.updated (seller completes onboarding)
  python3 forge.py account acct_xxx

  # charge.dispute.created
  python3 forge.py dispute charge.dispute.created ch_xxx 50000

  # Unsigned request (negative test — expect 400)
  python3 forge.py raw_unsigned

  # Bad signature (negative test — expect 400)
  python3 forge.py raw_badsig

Environment variables:
  GATEWAY          — gateway base URL (default: http://api-gateway:8080)
  WEBHOOK_SECRET   — Stripe webhook signing secret (default: dev whsec_…)
"""

import sys
import json
import time
import uuid
import hmac
import hashlib
import urllib.request
import urllib.error
import os

# ── Configuration ────────────────────────────────────────────────────────────

GW = os.environ.get("GATEWAY", "http://api-gateway:8080")
SECRET = os.environ.get(
    "WEBHOOK_SECRET",
    "whsec_9036236865171c8dd43b2c376f96d9847980b59fc9eef44c16ccb2ca0feb7268",
)

# Must match the Stripe API version pinned by payment-service's stripe-java
# dependency (26.1.0 → 2024-06-20). EventDataObjectDeserializer rejects
# events with a mismatched api_version.
API_VERSION = "2024-06-20"


# ── Helpers ──────────────────────────────────────────────────────────────────

def _suffix():
    return uuid.uuid4().hex[:16]


def _base(event_type):
    """Full Stripe event envelope with all required fields."""
    return {
        "id": f"evt_e2e_{_suffix()}",
        "object": "event",
        "api_version": API_VERSION,
        "created": int(time.time()),
        "livemode": False,
        "pending_webhooks": 1,
        "type": event_type,
    }


# ── Event Builders ───────────────────────────────────────────────────────────

def payment_intent_event(event_type, parent_order_id):
    pi = {
        "id": f"pi_e2e_{_suffix()}",
        "object": "payment_intent",
        "amount": 100000,
        "currency": "vnd",
        "status": "succeeded" if event_type == "payment_intent.succeeded" else "requires_payment_method",
        "metadata": {"parent_order_id": str(parent_order_id)},  # snake_case!
        "latest_charge": f"ch_e2e_{_suffix()}",
    }
    e = _base(event_type)
    e["data"] = {"object": pi}
    return json.dumps(e, separators=(",", ":"))


def charge_refunded_event(parent_order_id, amount_refunded):
    ch = {
        "id": f"ch_e2e_{_suffix()}",
        "object": "charge",
        "amount": 100000,
        "amount_refunded": amount_refunded,
        "currency": "vnd",
        "status": "succeeded",
        "metadata": {"parent_order_id": str(parent_order_id)},
    }
    e = _base("charge.refunded")
    e["data"] = {"object": ch}
    return json.dumps(e, separators=(",", ":"))


def transfer_event(event_type, order_id, transfer_id):
    tr = {
        "id": transfer_id,
        "object": "transfer",
        "amount": 50000,
        "currency": "vnd",
        "status": "reversed" if event_type == "transfer.reversed" else "paid",
        "metadata": {"order_id": str(order_id)},
    }
    if event_type == "transfer.reversed":
        tr["amount_reversed"] = 50000
    e = _base(event_type)
    e["data"] = {"object": tr}
    return json.dumps(e, separators=(",", ":"))


def account_updated_event(account_id, details_submitted=True,
                          charges_enabled=True, payouts_enabled=True,
                          currently_due=None, disabled_reason=None):
    """Build account.updated event.

    currently_due: list of requirement strings (e.g. ["business_url", "external_account"])
    disabled_reason: e.g. "requirements.past_due" or "rejected.fraud"
    """
    req = {}
    if currently_due:
        req["currently_due"] = currently_due
    if disabled_reason:
        req["disabled_reason"] = disabled_reason
    a = {
        "id": account_id,
        "object": "account",
        "details_submitted": details_submitted,
        "charges_enabled": charges_enabled,
        "payouts_enabled": payouts_enabled,
        "requirements": req,
    }
    e = _base("account.updated")
    e["data"] = {"object": a}
    return json.dumps(e, separators=(",", ":"))


def dispute_event(event_type, charge_id, amount, reason="fraudulent"):
    dp = {
        "id": f"dp_e2e_{_suffix()}",
        "object": "dispute",
        "charge": charge_id,
        "amount": amount,
        "currency": "vnd",
        "status": "needs_response" if event_type == "charge.dispute.created" else "won",
    }
    if event_type == "charge.dispute.created":
        dp["reason"] = reason
    e = _base(event_type)
    e["data"] = {"object": dp}
    return json.dumps(e, separators=(",", ":"))


# ── Signing & Sending ────────────────────────────────────────────────────────

def sign_and_send(payload):
    """Sign payload with HMAC-SHA256 and POST to gateway webhook endpoint."""
    ts = int(time.time())
    sig = hmac.new(
        SECRET.encode(), f"{ts}.{payload}".encode(), hashlib.sha256
    ).hexdigest()
    req = urllib.request.Request(
        f"{GW}/api/v1/stripe/webhooks",
        data=payload.encode(),
        headers={
            "Content-Type": "application/json",
            "Stripe-Signature": f"t={ts},v1={sig}",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return r.status, r.read().decode()[:200]
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()[:200]


# ── CLI ──────────────────────────────────────────────────────────────────────

def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    cmd = sys.argv[1]

    if cmd == "pi":
        code, body = sign_and_send(
            payment_intent_event(sys.argv[2], int(sys.argv[3]))
        )
    elif cmd == "charge_refund":
        code, body = sign_and_send(
            charge_refunded_event(int(sys.argv[2]), int(sys.argv[3]))
        )
    elif cmd == "transfer":
        code, body = sign_and_send(
            transfer_event(sys.argv[2], int(sys.argv[3]), sys.argv[4])
        )
    elif cmd == "account":
        # python3 forge.py account acct_xxx [details_submitted] [charges_enabled] [payouts_enabled] [currently_due_csv] [disabled_reason]
        aid = sys.argv[2]
        det = sys.argv[3].lower() not in ("false", "0", "no", "f") if len(sys.argv) > 3 else True
        ch  = sys.argv[4].lower() not in ("false", "0", "no", "f") if len(sys.argv) > 4 else True
        po  = sys.argv[5].lower() not in ("false", "0", "no", "f") if len(sys.argv) > 5 else True
        due = sys.argv[6].split(",") if len(sys.argv) > 6 and sys.argv[6] else None
        dis = sys.argv[7] if len(sys.argv) > 7 and sys.argv[7] else None
        code, body = sign_and_send(
            account_updated_event(aid, det, ch, po, due, dis)
        )
    elif cmd == "dispute":
        code, body = sign_and_send(
            dispute_event(sys.argv[2], sys.argv[3], int(sys.argv[4]))
        )
    elif cmd == "raw_unsigned":
        req = urllib.request.Request(
            f"{GW}/api/v1/stripe/webhooks",
            data=b'{"type":"payment_intent.succeeded"}',
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=10) as r:
                code, body = r.status, r.read().decode()[:100]
        except urllib.error.HTTPError as e:
            code, body = e.code, e.read().decode()[:100]
        except urllib.error.URLError as e:
            code, body = -1, str(e)
    elif cmd == "raw_badsig":
        p = '{"type":"payment_intent.succeeded"}'
        ts = int(time.time())
        bad = hmac.new(
            b"whsec_WRONG", f"{ts}.{p}".encode(), hashlib.sha256
        ).hexdigest()
        req = urllib.request.Request(
            f"{GW}/api/v1/stripe/webhooks",
            data=p.encode(),
            headers={
                "Content-Type": "application/json",
                "Stripe-Signature": f"t={ts},v1={bad}",
            },
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=10) as r:
                code, body = r.status, r.read().decode()[:100]
        except urllib.error.HTTPError as e:
            code, body = e.code, e.read().decode()[:100]
    else:
        print(f"Unknown command: {cmd}")
        print(__doc__)
        sys.exit(2)

    print(f"http={code} body={body}")


if __name__ == "__main__":
    main()
