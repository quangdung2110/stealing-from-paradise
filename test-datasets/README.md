# Test datasets

Thu muc nay co 3 nhom du lieu:

- `database/`: dataset seed truc tiep vao Postgres/Mongo/Elasticsearch de test frontend voi backend real mode.

- `users.json`, `products.json`, `orders.json`, `payments.json`, `flashsales.json`: seed/catalog cu, dang duoc tai lieu `TESTING.md` tham chieu. Khong sua cac file nay khi chay manual test.
- `full-coverage/full-coverage-dataset.json`: dataset moi de kiem thu truc tiep tat ca use case va business flow trong `documents/`.

Neu muc tieu la chay frontend ngay, dung:

```powershell
.\test-datasets\database\seed-frontend-dataset.ps1
```

Huong dan chi tiet: `test-datasets/database/README.md`.

## Coverage

Dataset full coverage bam theo:

- `documents/use-cases/**`: 48 use case
- `documents/flows/**`: 12 business flow
- `documents/api-contracts/**`: method/path/body shape
- `documents/traceability/**`: trace UC/flow/service

Kiem tra coverage truoc khi chay:

```powershell
node .\test-datasets\full-coverage\check-coverage.mjs
```

Ket qua mong doi:

```text
docs use cases      : 48
covered use cases   : 48
docs business flows : 12
covered flows       : 12
RESULT: PASS
```

## Chuan bi moi truong

Chay stack dev:

```powershell
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d
```

Gateway mac dinh:

```powershell
$base = "http://localhost:8080"
$api = "$base/api/v1"
$ai = "$base/api/ai"
$ds = Get-Content .\test-datasets\full-coverage\full-coverage-dataset.json -Raw | ConvertFrom-Json
$runId = Get-Date -Format "yyyyMMddHHmmss"
```

Health check:

```powershell
curl.exe -s "$base/actuator/health"
curl.exe -s "http://localhost:8761/eureka/apps"
```

## Lay token

```powershell
$buyerBody = $ds.requestBodies.identity.loginBuyer | ConvertTo-Json -Depth 20
$sellerBody = $ds.requestBodies.identity.loginSeller | ConvertTo-Json -Depth 20
$adminBody = $ds.requestBodies.identity.loginAdmin | ConvertTo-Json -Depth 20

$buyerLogin = curl.exe -s -X POST "$api/auth/login" -H "Content-Type: application/json" -d $buyerBody | ConvertFrom-Json
$sellerLogin = curl.exe -s -X POST "$api/auth/login" -H "Content-Type: application/json" -d $sellerBody | ConvertFrom-Json
$adminLogin = curl.exe -s -X POST "$api/auth/login" -H "Content-Type: application/json" -d $adminBody | ConvertFrom-Json

$buyerToken = if ($buyerLogin.data.accessToken) { $buyerLogin.data.accessToken } else { $buyerLogin.accessToken }
$sellerToken = if ($sellerLogin.data.accessToken) { $sellerLogin.data.accessToken } else { $sellerLogin.accessToken }
$adminToken = if ($adminLogin.data.accessToken) { $adminLogin.data.accessToken } else { $adminLogin.accessToken }
```

## Resolve placeholder can thiet

Dataset dung placeholder `{{...}}` de tranh hard-code id sinh dong. Chay lookup truoc cac flow order/payment:

```powershell
$sku = curl.exe -s "$api/products/variants/sku/SKU-MAGSAFE" | ConvertFrom-Json
$variantActiveTech = if ($sku.data.id) { $sku.data.id } else { $sku.id }

$addr = curl.exe -s "$api/users/me/addresses" -H "Authorization: Bearer $buyerToken" | ConvertFrom-Json
$buyerDefaultAddress = if ($addr.data[0].address_id) { $addr.data[0].address_id } else { $addr.data[0].id }
```

Cac placeholder ngay gio:

```powershell
$nowPlus30MinutesIso = (Get-Date).ToUniversalTime().AddMinutes(30).ToString("yyyy-MM-ddTHH:mm:ssZ")
$nowPlus90MinutesIso = (Get-Date).ToUniversalTime().AddMinutes(90).ToString("yyyy-MM-ddTHH:mm:ssZ")
$nowMinus1MinuteIso = (Get-Date).ToUniversalTime().AddMinutes(-1).ToString("yyyy-MM-ddTHH:mm:ssZ")
$nowPlus5MinutesIso = (Get-Date).ToUniversalTime().AddMinutes(5).ToString("yyyy-MM-ddTHH:mm:ssZ")
```

## Chay truc tiep theo case

Mo `full-coverage/full-coverage-dataset.json`, tim `cases[].id`, doc `steps` hoac `method/path/bodyRef`, thay placeholder bang gia tri da resolve, roi gui request.

Vi du: UC-PRODUCT-009 add to cart.

```powershell
$body = $ds.requestBodies.product.cartAdd
$body.variantId = $variantActiveTech
$json = $body | ConvertTo-Json -Depth 20

curl.exe -s -X POST "$api/cart/items" `
  -H "Authorization: Bearer $buyerToken" `
  -H "Content-Type: application/json" `
  -d $json
```

Vi du: UC-ORDER-001 checkout.

```powershell
$previewBody = $ds.requestBodies.product.checkoutPreview
$previewBody.itemIds = @("6:$variantActiveTech")
$previewJson = $previewBody | ConvertTo-Json -Depth 20

$preview = curl.exe -s -X POST "$api/cart/checkout/preview" `
  -H "Authorization: Bearer $buyerToken" `
  -H "Content-Type: application/json" `
  -d $previewJson | ConvertFrom-Json

$previewToken = if ($preview.data.previewToken) { $preview.data.previewToken } else { $preview.previewToken }

$submitBody = $ds.requestBodies.product.checkoutSubmit
$submitBody.previewToken = $previewToken
$submitBody.addressId = $buyerDefaultAddress
$submitJson = $submitBody | ConvertTo-Json -Depth 20

curl.exe -s -X POST "$api/cart/checkout/submit" `
  -H "Authorization: Bearer $buyerToken" `
  -H "Content-Type: application/json" `
  -d $submitJson
```

Vi du: UC-AICHAT-001 start chat.

```powershell
$chatBody = $ds.requestBodies.aiChat.createSession | ConvertTo-Json -Depth 20
curl.exe -s -X POST "$ai/sessions" `
  -H "Authorization: Bearer $buyerToken" `
  -H "Content-Type: application/json" `
  -d $chatBody
```

## Thu tu full regression de it bi ket du lieu

1. `TC-IDENTITY-*`: register/login/profile/address.
2. `TC-PRODUCT-001` den `TC-PRODUCT-015`: catalog, category, seller product, variant, image, inventory, cart, admin review.
3. `TC-ORDER-001` den `TC-ORDER-008`: checkout, view, cancel, ship, delivered, return/refund, seller views.
4. `TC-PAYMENT-*`: payment query, Stripe webhook, onboarding, seller transfer views.
5. `TC-REFUND-*`: admin review approve/reject. Real Stripe refund can require a real charged test PaymentIntent.
6. `TC-FLASHSALE-*`: session, item registration, reminder/buy, worker end-session observation.
7. `TC-SEARCH-*`: search/suggest/reindex and publish-to-index observation.
8. `TC-NOTIF-*`: open SSE first, then trigger order/refund/flash-sale event in another terminal.
9. `TC-AICHAT-*`: start session, stream chat, confirm action. Requires valid LLM config for happy path; documented 503 is acceptable when provider is unavailable.

## Stripe va async notes

- `/api/v1/stripe/webhooks` phai co `Stripe-Signature` hop le. Unsigned payload la negative test va nen bi reject.
- Neu dung forged `payment_intent.succeeded`, Stripe refund that co the fail khi admin approve refund vi PaymentIntent khong duoc charge that tren Stripe. Day la expected caveat da ghi trong `documents/MANUAL_API_TEST_PLAN.md`.
- Kafka/Redis/Axon flows can poll 5-90 giay tuy flow. Nen query lai order/payment/refund/notification sau khi gui event.
- Flash sale API trong dataset dung `/api/v1/flash-sales`, khop controller va OpenAPI hien tai. `documents/operations/API_URLS.md` con mot so path legacy `/flash-sale/sessions`.

## Bao cao ket qua

Khi ghi ket qua manual test, dung `cases[].id` lam test id va map nguoc qua:

- `coversUseCases`
- `coversBusinessFlows`
- `expect.assert`

Neu them use case hoac flow moi trong `documents/`, cap nhat `full-coverage-dataset.json` roi chay lai:

```powershell
node .\test-datasets\full-coverage\check-coverage.mjs
```
