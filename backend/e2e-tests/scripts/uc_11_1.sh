#!/bin/sh
# UC-11.1 happy path runbook — runs inside e2e-runner sidecar
set -eu
GATEWAY="${GATEWAY:-http://api-gateway:8080}"
VARIANT="${VARIANT:-c5803c7d-2d5c-4178-b579-7266a15ca9ff}"
CUST_ID="${CUST_ID:-6}"

j() { python3 -c "import sys,json;print($1)" ; }

login() {
  curl -s -X POST "$GATEWAY/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d "{\"credential\":\"$1\",\"password\":\"dev123\"}" \
  | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d['accessToken'])"
}

poll() { # url, token, py-expr, expected, label
  url="$1"; tok="$2"; expr="$3"; expected="$4"; label="$5"
  i=0
  while [ $i -lt 45 ]; do
    val=$(curl -s "$url" -H "Authorization: Bearer $tok" \
      | python3 -c "import sys,json;d=json.load(sys.stdin);$expr" 2>/dev/null || echo "")
    if [ "$val" = "$expected" ]; then
      echo "  [OK ${i}x2s] $label = $val"
      return 0
    fi
    i=$((i+1)); sleep 2
  done
  echo "  [TIMEOUT] $label expected='$expected' last='$val'"
  return 1
}

BUYER=$(login minhhoa)
echo "[+] buyer token acquired"

curl -s -X DELETE "$GATEWAY/api/v1/cart" -H "Authorization: Bearer $BUYER" >/dev/null
curl -s -X POST "$GATEWAY/api/v1/cart/items" -H "Authorization: Bearer $BUYER" \
  -H 'Content-Type: application/json' -d "{\"variantId\":\"$VARIANT\",\"quantity\":1}" >/dev/null

PREVIEW_TOKEN=$(curl -s -X POST "$GATEWAY/api/v1/cart/checkout/preview" -H "Authorization: Bearer $BUYER" \
  -H 'Content-Type: application/json' -d "{\"itemIds\":[\"$CUST_ID:$VARIANT\"]}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['previewToken'])")
echo "[+] previewToken=${PREVIEW_TOKEN:0:24}..."

ADDR_ID=$(curl -s "$GATEWAY/api/v1/users/me/addresses" -H "Authorization: Bearer $BUYER" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data'][0]['address_id'])")
echo "[+] addressId=$ADDR_ID"

PRE_MAX=$(curl -s "$GATEWAY/api/v1/orders?page=0&size=100" -H "Authorization: Bearer $BUYER" \
  | python3 -c "import sys,json;c=json.load(sys.stdin).get('data',{}).get('content',[]);print(max([o.get('parentOrderId') or 0 for o in c]+[0]))")
echo "[+] pre-max parentOrderId=$PRE_MAX"

curl -s -X POST "$GATEWAY/api/v1/cart/checkout/submit" -H "Authorization: Bearer $BUYER" \
  -H 'Content-Type: application/json' \
  -d "{\"previewToken\":\"$PREVIEW_TOKEN\",\"addressId\":$ADDR_ID}" >/dev/null

PARENT_ID=""
i=0
while [ $i -lt 45 ]; do
  PARENT_ID=$(curl -s "$GATEWAY/api/v1/orders?page=0&size=100" -H "Authorization: Bearer $BUYER" \
    | PRE_MAX=$PRE_MAX python3 -c "import sys,json,os;m=int(os.environ['PRE_MAX']);c=json.load(sys.stdin).get('data',{}).get('content',[]);n=[o['parentOrderId'] for o in c if (o.get('parentOrderId') or 0)>m];print(n[0] if n else '')")
  [ -n "$PARENT_ID" ] && break
  i=$((i+1)); sleep 2
done
[ -z "$PARENT_ID" ] && { echo "[FAIL] no new parent order"; exit 1; }
echo "[+] parentOrderId=$PARENT_ID (waited ${i}x2s)"

poll "$GATEWAY/api/v1/payments/parent-order/$PARENT_ID" "$BUYER" \
  "print(d['data']['status'])" "PENDING" "tx PENDING"

# UC-11.7 idempotency snapshot
TX1=$(curl -s "$GATEWAY/api/v1/payments/parent-order/$PARENT_ID" -H "Authorization: Bearer $BUYER" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['transactionId'])")
echo "[+] tx#1=$TX1"

# Forge payment_intent.succeeded
GATEWAY="$GATEWAY" python3 /forge.py pi payment_intent.succeeded "$PARENT_ID"

poll "$GATEWAY/api/v1/payments/parent-order/$PARENT_ID" "$BUYER" \
  "print(d['data']['status'])" "SUCCESS" "tx SUCCESS"

poll "$GATEWAY/api/v1/orders/parent/$PARENT_ID" "$BUYER" \
  "print('PASS' if all(s['status']=='PAID' for s in d['data']['orders']) else 'WAIT')" "PASS" "all sub-orders PAID"

# UC-11.7 idempotency check
TX2=$(curl -s "$GATEWAY/api/v1/payments/parent-order/$PARENT_ID" -H "Authorization: Bearer $BUYER" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['transactionId'])")
if [ "$TX1" = "$TX2" ]; then
  echo "[OK] idempotent transactionId=$TX1"
else
  echo "[FAIL] tx mutated $TX1 -> $TX2"; exit 1
fi

# Summary line for harness
echo "RESULT parent_order_id=$PARENT_ID tx=$TX1 status=SUCCESS"
