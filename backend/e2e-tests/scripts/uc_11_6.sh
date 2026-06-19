#!/bin/sh
# UC-11.6 Stripe Connect Onboarding happy path
set -u
GATEWAY="${GATEWAY:-http://api-gateway:8080}"

RAND=$(date +%s)
USER="ucseller$RAND"
NS=$(curl -s -X POST "$GATEWAY/api/v1/auth/register/seller" -H 'Content-Type: application/json' \
     -d "{\"username\":\"$USER\",\"email\":\"$USER@test.com\",\"password\":\"dev123\",\"fullName\":\"UC11_6 Seller\"}" \
     | python3 -c "import sys,json;d=json.load(sys.stdin);print(d.get('accessToken') or d.get('data',{}).get('accessToken') or '')")
[ -z "$NS" ] && { echo "[FAIL] register/seller no token"; exit 1; }
echo "[+] new seller=$USER token acquired"

START=$(curl -s -X POST "$GATEWAY/api/v1/stripe/onboarding/start" -H "Authorization: Bearer $NS" \
     -H 'Content-Type: application/json' -d '{}')
echo "[+] /start: $START" | head -c 300
echo

STATUS=$(curl -s "$GATEWAY/api/v1/stripe/onboarding/status" -H "Authorization: Bearer $NS")
echo "[+] /status: $STATUS"
ACCT=$(echo "$STATUS" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['stripeAccountId'])")
echo "[+] stripeAccountId=$ACCT"

# Refresh link (mock account auto-complete may reject)
echo "[+] /refresh-link:"
curl -s -X POST "$GATEWAY/api/v1/stripe/onboarding/refresh-link" -H "Authorization: Bearer $NS" \
     -H 'Content-Type: application/json' -d '{}' | head -c 300
echo

# Forge account.updated → chargesEnabled=true
GATEWAY="$GATEWAY" python3 /forge.py account "$ACCT"

i=0
while [ $i -lt 30 ]; do
  CE=$(curl -s "$GATEWAY/api/v1/stripe/onboarding/status" -H "Authorization: Bearer $NS" \
    | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(d.get('chargesEnabled'))")
  [ "$CE" = "True" ] && { echo "  [OK ${i}x2s] chargesEnabled=True"; break; }
  i=$((i+1)); sleep 2
done

FINAL=$(curl -s "$GATEWAY/api/v1/stripe/onboarding/status" -H "Authorization: Bearer $NS" \
  | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print('charges=',d.get('chargesEnabled'),'payouts=',d.get('payoutsEnabled'),'status=',d.get('onboardingStatus'))")
echo "RESULT_UC116 seller=$USER acct=$ACCT $FINAL"
