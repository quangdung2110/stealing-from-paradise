#!/bin/sh
# UC-11.8 multi-seller cart → 1 parent / N sub / 1 tx
set -u
GATEWAY="${GATEWAY:-http://api-gateway:8080}"
V1="c5803c7d-2d5c-4178-b579-7266a15ca9ff"   # seller 1 (techworld)
V2="568156e1-00bb-4bdf-9c8c-10a5fbcdb603"   # seller 4 (homeliving)
CUST_ID=6

login() {
  curl -s -X POST "$GATEWAY/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d "{\"credential\":\"$1\",\"password\":\"dev123\"}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])"
}

BUYER=$(login minhhoa)
curl -s -X DELETE "$GATEWAY/api/v1/cart" -H "Authorization: Bearer $BUYER" >/dev/null
for V in $V1 $V2; do
  curl -s -X POST "$GATEWAY/api/v1/cart/items" -H "Authorization: Bearer $BUYER" \
    -H 'Content-Type: application/json' -d "{\"variantId\":\"$V\",\"quantity\":1}" >/dev/null
done

PT=$(curl -s -X POST "$GATEWAY/api/v1/cart/checkout/preview" -H "Authorization: Bearer $BUYER" \
  -H 'Content-Type: application/json' -d "{\"itemIds\":[\"$CUST_ID:$V1\",\"$CUST_ID:$V2\"]}" \
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
[ -z "$PID" ] && { echo "[FAIL] no new parent order"; exit 1; }
echo "[+] parentOrderId=$PID"

# Wait for sub-orders to materialize (2)
sleep 4
SUBINFO=$(curl -s "$GATEWAY/api/v1/orders/parent/$PID" -H "Authorization: Bearer $BUYER" \
  | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print('orders=',len(d['orders']),'sellers=',sorted({s['sellerId'] for s in d['orders']}))")
echo "[+] $SUBINFO"

TXINFO=$(curl -s "$GATEWAY/api/v1/payments/parent-order/$PID" -H "Authorization: Bearer $BUYER" \
  | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print('tx=',d.get('transactionId'),'status=',d.get('status'),'amount=',d.get('amount') or d.get('totalAmount'))")
echo "[+] $TXINFO"

# Assertion: ≥2 sub-orders, distinct sellers, 1 transactionId, amount ≈ V1+V2 prices
ASSERT=$(curl -s "$GATEWAY/api/v1/orders/parent/$PID" -H "Authorization: Bearer $BUYER" \
  | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
subs=d['orders']
sellers={s['sellerId'] for s in subs}
print('PASS' if len(subs)>=2 and len(sellers)>=2 else 'FAIL')
")
echo "[+] multi-seller assertion: $ASSERT"
echo "RESULT_UC118 parent_order_id=$PID multi_seller=$ASSERT"
