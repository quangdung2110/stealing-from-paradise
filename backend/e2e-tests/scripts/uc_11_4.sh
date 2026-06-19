#!/bin/sh
# UC-11.4 Fulfillment + Partial Refund (full lifecycle: PAID → SHIPPING → DELIVERED → refund)
set -u
GATEWAY="${GATEWAY:-http://api-gateway:8080}"
VARIANT="${VARIANT:-c5803c7d-2d5c-4178-b579-7266a15ca9ff}"
CUST_ID=6

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

BUYER=$(login minhhoa)
ADMIN=$(login admin)

# Step 1: fresh checkout
curl -s -X DELETE "$GATEWAY/api/v1/cart" -H "Authorization: Bearer $BUYER" >/dev/null
curl -s -X POST "$GATEWAY/api/v1/cart/items" -H "Authorization: Bearer $BUYER" \
  -H 'Content-Type: application/json' -d "{\"variantId\":\"$VARIANT\",\"quantity\":1}" >/dev/null
PT=$(curl -s -X POST "$GATEWAY/api/v1/cart/checkout/preview" -H "Authorization: Bearer $BUYER" \
  -H 'Content-Type: application/json' -d "{\"itemIds\":[\"$CUST_ID:$VARIANT\"]}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['previewToken'])")
AID=$(curl -s "$GATEWAY/api/v1/users/me/addresses" -H "Authorization: Bearer $BUYER" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data'][0]['address_id'])")
PRE=$(curl -s "$GATEWAY/api/v1/orders?page=0&size=100" -H "Authorization: Bearer $BUYER" \
  | python3 -c "import sys,json;c=json.load(sys.stdin).get('data',{}).get('content',[]);print(max([o.get('parentOrderId') or 0 for o in c]+[0]))")
curl -s -X POST "$GATEWAY/api/v1/cart/checkout/submit" -H "Authorization: Bearer $BUYER" \
  -H 'Content-Type: application/json' -d "{\"previewToken\":\"$PT\",\"addressId\":$AID}" >/dev/null

PID=""; i=0
while [ $i -lt 45 ]; do
  PID=$(curl -s "$GATEWAY/api/v1/orders?page=0&size=100" -H "Authorization: Bearer $BUYER" \
    | PRE=$PRE python3 -c "import sys,json,os;m=int(os.environ['PRE']);c=json.load(sys.stdin).get('data',{}).get('content',[]);n=[o['parentOrderId'] for o in c if (o.get('parentOrderId') or 0)>m];print(n[0] if n else '')")
  [ -n "$PID" ] && break
  i=$((i+1)); sleep 2
done
echo "[+] parentOrderId=$PID"
poll "$GATEWAY/api/v1/payments/parent-order/$PID" "$BUYER" "print(d['data']['status'])" "PENDING" "tx PENDING"

# Step 2: forge payment_intent.succeeded
GATEWAY="$GATEWAY" python3 /forge.py pi payment_intent.succeeded "$PID"
poll "$GATEWAY/api/v1/payments/parent-order/$PID" "$BUYER" "print(d['data']['status'])" "SUCCESS" "tx SUCCESS"
poll "$GATEWAY/api/v1/orders/parent/$PID" "$BUYER" \
  "print('PASS' if all(s['status']=='PAID' for s in d['data']['orders']) else 'WAIT')" "PASS" "sub PAID"

# Step 3: seller ships
SUB=$(curl -s "$GATEWAY/api/v1/orders/parent/$PID" -H "Authorization: Bearer $BUYER" \
  | python3 -c "import sys,json;o=json.load(sys.stdin)['data']['orders'][0];print(o['orderId'],o['sellerId'])")
ORDER_ID=$(echo $SUB | awk '{print $1}')
SID=$(echo $SUB | awk '{print $2}')
echo "[+] orderId=$ORDER_ID sellerId=$SID"
case $SID in
  1) SELLER=$(login techworld);;
  2) SELLER=$(login fashionhub);;
  3) SELLER=$(login gadgetpro);;
  4) SELLER=$(login homeliving);;
  5) SELLER=$(login sportoutdoor);;
  *) echo "[FAIL] unknown sellerId=$SID"; exit 1;;
esac

curl -s -X PUT "$GATEWAY/api/v1/orders/$ORDER_ID/tracking" -H "Authorization: Bearer $SELLER" \
  -H 'Content-Type: application/json' -d '{"trackingNumber":"MANUAL-UC114-001"}' >/dev/null
poll "$GATEWAY/api/v1/orders/$ORDER_ID" "$BUYER" "print(d['data']['status'])" "SHIPPING" "order SHIPPING"

# Step 4: buyer confirms received
curl -s -X POST "$GATEWAY/api/v1/orders/$ORDER_ID/confirm-received" -H "Authorization: Bearer $BUYER" \
  -H 'Content-Type: application/json' -d '{}' >/dev/null
poll "$GATEWAY/api/v1/orders/$ORDER_ID" "$BUYER" "print(d['data']['status'])" "DELIVERED" "order DELIVERED"

# Step 5: buyer requests refund
ITEM_ID=$(curl -s "$GATEWAY/api/v1/orders/$ORDER_ID" -H "Authorization: Bearer $BUYER" \
  | python3 -c "import sys,json;i=json.load(sys.stdin)['data']['items'][0];print(i.get('orderItemId') or i.get('id'))")
echo "[+] orderItemId=$ITEM_ID — requesting refund"
curl -s -X POST "$GATEWAY/api/v1/orders/$ORDER_ID/refunds" -H "Authorization: Bearer $BUYER" \
  -H 'Content-Type: application/json' \
  -d "{\"reason\":\"E2E refund test\",\"items\":[{\"orderItemId\":$ITEM_ID,\"quantity\":1,\"itemReason\":\"damaged\"}],\"evidenceImages\":[]}" >/dev/null
sleep 3

REFUND_ID=$(curl -s "$GATEWAY/api/v1/orders/$ORDER_ID/refunds" -H "Authorization: Bearer $BUYER" \
  | python3 -c "import sys,json;rs=json.load(sys.stdin)['data'];print(rs[-1].get('refundId') or rs[-1].get('id'))")
echo "[+] refundId=$REFUND_ID"
[ -z "$REFUND_ID" ] || [ "$REFUND_ID" = "None" ] && echo "[WARN] no refundId yet — refund flow may use async"

# Step 6: admin approves
if [ -n "$REFUND_ID" ] && [ "$REFUND_ID" != "None" ]; then
  curl -s -X POST "$GATEWAY/api/v1/admin/refunds/$REFUND_ID/approve" -H "Authorization: Bearer $ADMIN" \
    -H 'Content-Type: application/json' -d '{"adminNote":"E2E manual approve"}' >/dev/null
  poll "$GATEWAY/api/v1/orders/$ORDER_ID/refunds/$REFUND_ID" "$BUYER" \
    "print(d['data']['status'])" "APPROVED" "refund APPROVED"
fi

echo "RESULT_UC114 parent_order_id=$PID order_id=$ORDER_ID refund_id=$REFUND_ID"
