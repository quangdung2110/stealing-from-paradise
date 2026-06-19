#!/bin/sh
# Phase A bundled runbook: UC-11.2/3/9/10/11
# Pre-req: UC-11.1 ran successfully producing $UC11_1_PARENT_ID (default 247)
set -u
GATEWAY="${GATEWAY:-http://api-gateway:8080}"
VARIANT="${VARIANT:-c5803c7d-2d5c-4178-b579-7266a15ca9ff}"
CUST_ID="${CUST_ID:-6}"
UC11_1_PARENT_ID="${UC11_1_PARENT_ID:-247}"

login() {
  curl -s -X POST "$GATEWAY/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d "{\"credential\":\"$1\",\"password\":\"dev123\"}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])"
}

poll() {
  url="$1"; tok="$2"; expr="$3"; expected="$4"; label="$5"
  i=0
  while [ $i -lt 45 ]; do
    val=$(curl -s "$url" -H "Authorization: Bearer $tok" \
      | python3 -c "import sys,json;d=json.load(sys.stdin);$expr" 2>/dev/null || echo "")
    [ "$val" = "$expected" ] && { echo "  [OK ${i}x2s] $label=$val"; return 0; }
    i=$((i+1)); sleep 2
  done
  echo "  [TIMEOUT] $label expected='$expected' last='$val'"; return 1
}

new_order() { # echo PARENT_ID. uses caller's BUYER + VARIANT.
  BUYER_T="$1"
  curl -s -X DELETE "$GATEWAY/api/v1/cart" -H "Authorization: Bearer $BUYER_T" >/dev/null
  curl -s -X POST "$GATEWAY/api/v1/cart/items" -H "Authorization: Bearer $BUYER_T" \
    -H 'Content-Type: application/json' -d "{\"variantId\":\"$VARIANT\",\"quantity\":1}" >/dev/null
  PT=$(curl -s -X POST "$GATEWAY/api/v1/cart/checkout/preview" -H "Authorization: Bearer $BUYER_T" \
    -H 'Content-Type: application/json' -d "{\"itemIds\":[\"$CUST_ID:$VARIANT\"]}" \
    | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['previewToken'])")
  AID=$(curl -s "$GATEWAY/api/v1/users/me/addresses" -H "Authorization: Bearer $BUYER_T" \
    | python3 -c "import sys,json;print(json.load(sys.stdin)['data'][0]['address_id'])")
  PRE=$(curl -s "$GATEWAY/api/v1/orders?page=0&size=100" -H "Authorization: Bearer $BUYER_T" \
    | python3 -c "import sys,json;c=json.load(sys.stdin).get('data',{}).get('content',[]);print(max([o.get('parentOrderId') or 0 for o in c]+[0]))")
  curl -s -X POST "$GATEWAY/api/v1/cart/checkout/submit" -H "Authorization: Bearer $BUYER_T" \
    -H 'Content-Type: application/json' -d "{\"previewToken\":\"$PT\",\"addressId\":$AID}" >/dev/null
  PID=""; i=0
  while [ $i -lt 45 ]; do
    PID=$(curl -s "$GATEWAY/api/v1/orders?page=0&size=100" -H "Authorization: Bearer $BUYER_T" \
      | PRE=$PRE python3 -c "import sys,json,os;m=int(os.environ['PRE']);c=json.load(sys.stdin).get('data',{}).get('content',[]);n=[o['parentOrderId'] for o in c if (o.get('parentOrderId') or 0)>m];print(n[0] if n else '')")
    [ -n "$PID" ] && break
    i=$((i+1)); sleep 2
  done
  echo "$PID"
}

BUYER=$(login minhhoa)
echo "============================================================"
echo " UC-11.2 — payment_intent.payment_failed → sub-orders CANCELLED"
echo "============================================================"
P2=$(new_order "$BUYER")
echo "[+] parentOrderId=$P2"
poll "$GATEWAY/api/v1/payments/parent-order/$P2" "$BUYER" "print(d['data']['status'])" "PENDING" "tx PENDING"
GATEWAY="$GATEWAY" python3 /forge.py pi payment_intent.payment_failed "$P2"
poll "$GATEWAY/api/v1/payments/parent-order/$P2" "$BUYER" "print(d['data']['status'])" "FAILED" "tx FAILED"
poll "$GATEWAY/api/v1/orders/parent/$P2" "$BUYER" \
  "print('PASS' if all(s['status']=='CANCELLED' for s in d['data']['orders']) else 'WAIT')" "PASS" "all sub-orders CANCELLED"
echo "RESULT_UC112 parent_order_id=$P2 status=FAILED"

echo
echo "============================================================"
echo " UC-11.3 — buyer cancel PENDING → tx CANCELLED"
echo "============================================================"
P3=$(new_order "$BUYER")
echo "[+] parentOrderId=$P3"
poll "$GATEWAY/api/v1/payments/parent-order/$P3" "$BUYER" "print(d['data']['status'])" "PENDING" "tx PENDING"
SUB_IDS=$(curl -s "$GATEWAY/api/v1/orders/parent/$P3" -H "Authorization: Bearer $BUYER" \
  | python3 -c "import sys,json;print(' '.join(str(s['orderId']) for s in json.load(sys.stdin)['data']['orders']))")
echo "[+] sub-orders to cancel: $SUB_IDS"
for OID in $SUB_IDS; do
  curl -s -X POST "$GATEWAY/api/v1/orders/$OID/cancel" -H "Authorization: Bearer $BUYER" \
    -H 'Content-Type: application/json' -d '{"reason":"E2E manual test"}' >/dev/null
done
poll "$GATEWAY/api/v1/payments/parent-order/$P3" "$BUYER" "print(d['data']['status'])" "CANCELLED" "tx CANCELLED"
echo "RESULT_UC113 parent_order_id=$P3 status=CANCELLED"

echo
echo "============================================================"
echo " UC-11.9 — charge.refunded webhook on PARENT_ID=$UC11_1_PARENT_ID"
echo "============================================================"
GATEWAY="$GATEWAY" python3 /forge.py charge_refund "$UC11_1_PARENT_ID" 50000

echo
echo "============================================================"
echo " UC-11.10 — transfer.created + transfer.reversed"
echo "============================================================"
ORDER_FOR_TRANSFER=$(curl -s "$GATEWAY/api/v1/orders/parent/$UC11_1_PARENT_ID" -H "Authorization: Bearer $BUYER" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['orders'][0]['orderId'])")
echo "[+] orderId for transfer test: $ORDER_FOR_TRANSFER"
TR="tr_manual_$(date +%s)"
GATEWAY="$GATEWAY" python3 /forge.py transfer transfer.created "$ORDER_FOR_TRANSFER" "$TR"
GATEWAY="$GATEWAY" python3 /forge.py transfer transfer.reversed "$ORDER_FOR_TRANSFER" "$TR"

echo
echo "============================================================"
echo " UC-11.11 — webhook signature negative"
echo "============================================================"
GATEWAY="$GATEWAY" python3 /forge.py raw_unsigned
GATEWAY="$GATEWAY" python3 /forge.py raw_badsig

echo
echo "============================================================"
echo " DONE Phase A bundle"
echo "============================================================"
