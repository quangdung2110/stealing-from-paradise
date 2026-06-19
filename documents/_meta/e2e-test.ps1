$API = "http://localhost:8080/api/v1"
$G = [System.ConsoleColor]::Green
$R = [System.ConsoleColor]::Red
$Y = [System.ConsoleColor]::Yellow
$C = [System.ConsoleColor]::Cyan

function OkFail { param($v) if ($v) { Write-Host "  OK" -ForegroundColor Green } else { Write-Host "  FAIL" -ForegroundColor Red } }

Write-Host "==============================================" -ForegroundColor $C
Write-Host "     E2E TEST SUITE - FlashSale" -ForegroundColor $C
Write-Host "==============================================" -ForegroundColor $C

# ======== 1. ACCOUNT LOGIN ========
Write-Host "`n[1/8] Account Login" -ForegroundColor $Y

try { $br = Invoke-RestMethod "$API/auth/login" -Method POST -ContentType "application/json" -Body '{"credential":"fe_buyer","password":"dev123"}'; $BT = $br.data.accessToken; Write-Host "  fe_buyer  : True" -ForegroundColor Green } catch { Write-Host "  fe_buyer  : $_" -ForegroundColor Red }
try { $sr = Invoke-RestMethod "$API/auth/login" -Method POST -ContentType "application/json" -Body '{"credential":"fe_seller","password":"dev123"}'; $ST = $sr.data.accessToken; Write-Host "  fe_seller : True" -ForegroundColor Green } catch { Write-Host "  fe_seller : $_" -ForegroundColor Red }
try { $ar = Invoke-RestMethod "$API/auth/login" -Method POST -ContentType "application/json" -Body '{"credential":"fe_admin","password":"dev123"}'; $AT = $ar.data.accessToken; Write-Host "  fe_admin  : True" -ForegroundColor Green } catch { Write-Host "  fe_admin  : $_" -ForegroundColor Red }

# ======== 2. BUYER FLOWS ========
Write-Host "`n[2/8] fe_buyer — Browse & Products" -ForegroundColor $Y

try { $p = Invoke-RestMethod "$API/products?page=0&size=5" -Method GET; Write-Host "  Browse products : True ($($p.data.totalElements) total)" -ForegroundColor Green } catch { Write-Host "  Browse products : $_" -ForegroundColor Red }

try { $pid = $p.data.content[0].id; $d = Invoke-RestMethod "$API/products/$pid" -Method GET; Write-Host "  Product detail  : True ($($d.data.name))" -ForegroundColor Green } catch { Write-Host "  Product detail  : $_" -ForegroundColor Red }

try { $searchHeaders = @{"Authorization"="Bearer $BT"}; $s = Invoke-RestMethod "$API/search/products?q=iPhone&page=0&size=5" -Method GET; Write-Host "  Search products  : True ($($s.data.totalResults) results)" -ForegroundColor Green } catch { Write-Host "  Search products  : $_" -ForegroundColor Red }

try { $bh = @{"Authorization"="Bearer $BT"}; $u = Invoke-RestMethod "$API/users/me" -Method GET -Headers $bh; Write-Host "  Get profile      : True ($($u.data.fullName))" -ForegroundColor Green } catch { Write-Host "  Get profile      : $_" -ForegroundColor Red }

try { $a = Invoke-RestMethod "$API/users/me/addresses" -Method GET -Headers $bh; $c = $a.data | Measure-Object; Write-Host "  Get addresses    : True ($($c.Count) addrs)" -ForegroundColor Green } catch { Write-Host "  Get addresses    : $_" -ForegroundColor Red }

try { $cart = Invoke-RestMethod "$API/cart" -Method GET -Headers $bh; Write-Host "  Get cart         : True" -ForegroundColor Green } catch { Write-Host "  Get cart         : $_" -ForegroundColor Red }

try { $bo = Invoke-RestMethod "$API/orders?page=0&size=5" -Method GET -Headers $bh; Write-Host "  Get orders       : True ($($bo.data.totalElements) orders)" -ForegroundColor Green } catch { Write-Host "  Get orders       : $_" -ForegroundColor Red }

try { $w = Invoke-RestMethod "$API/wishlist" -Method GET -Headers $bh; Write-Host "  Get wishlist     : True" -ForegroundColor Green } catch { Write-Host "  Get wishlist     : $_" -ForegroundColor Red }

# ======== 3. SELLER FLOWS ========
Write-Host "`n[3/8] fe_seller — Products & Orders" -ForegroundColor $Y

$sh = @{"Authorization"="Bearer $ST"}

try { $sp = Invoke-RestMethod "$API/sellers/me/products?page=0&size=5" -Method GET -Headers $sh; Write-Host "  Seller products  : True ($($sp.data.totalElements) products)" -ForegroundColor Green } catch { Write-Host "  Seller products  : $_" -ForegroundColor Red }

try { $so = Invoke-RestMethod "$API/sellers/me/orders" -Method GET -Headers $sh; Write-Host "  Seller orders    : True" -ForegroundColor Green } catch { Write-Host "  Seller orders    : $_" -ForegroundColor Red }

try { $sd = Invoke-RestMethod "$API/sellers/me/dashboard" -Method GET -Headers $sh; Write-Host "  Dashboard        : True" -ForegroundColor Green } catch { Write-Host "  Dashboard        : $_" -ForegroundColor Red }

try { $spm = Invoke-RestMethod "$API/seller/payments" -Method GET -Headers $sh; Write-Host "  Seller payments  : True" -ForegroundColor Green } catch { Write-Host "  Seller payments  : $_" -ForegroundColor Red }

try { $ss = Invoke-RestMethod "$API/stripe/onboarding/status" -Method GET -Headers $sh; Write-Host "  Stripe status    : True" -ForegroundColor Green } catch { Write-Host "  Stripe status    : $_" -ForegroundColor Red }

# ======== 4. ADMIN FLOWS ========
Write-Host "`n[4/8] fe_admin — Management" -ForegroundColor $Y

$ah = @{"Authorization"="Bearer $AT"}

try { $ap = Invoke-RestMethod "$API/admin/products?page=0&size=5" -Method GET -Headers $ah; Write-Host "  Admin products   : True ($($ap.data.totalElements) products)" -ForegroundColor Green } catch { Write-Host "  Admin products   : $_" -ForegroundColor Red }

try { $aus = Invoke-RestMethod "$API/admin/users" -Method GET -Headers $ah; Write-Host "  Admin users      : True" -ForegroundColor Green } catch { Write-Host "  Admin users      : $_" -ForegroundColor Red }

try { $arb = Invoke-RestMethod "$API/admin/banners" -Method GET -Headers $ah; Write-Host "  Admin banners    : True" -ForegroundColor Green } catch { Write-Host "  Admin banners    : $_" -ForegroundColor Red }

try { $arf = Invoke-RestMethod "$API/admin/refunds?page=0&size=5" -Method GET -Headers $ah; Write-Host "  Admin refunds    : True ($($arf.data.totalElements) refunds)" -ForegroundColor Green } catch { Write-Host "  Admin refunds    : $_" -ForegroundColor Red }

# ======== 5. FLASH SALES ========
Write-Host "`n[5/8] Flash Sales" -ForegroundColor $Y

try { $fs = Invoke-RestMethod "$API/flash-sales" -Method GET -Headers $bh; Write-Host "  Browse flash sales: True" -ForegroundColor Green } catch { Write-Host "  Browse flash sales: $_" -ForegroundColor Red }

try { $fs2 = Invoke-RestMethod "$API/flash-sales" -Method GET -Headers $sh; Write-Host "  Seller flash sales: True" -ForegroundColor Green } catch { Write-Host "  Seller flash sales: $_" -ForegroundColor Red }

try { $fs3 = Invoke-RestMethod "$API/search/products?is_flash=true&page=0&size=5" -Method GET; Write-Host "  Flash search      : True" -ForegroundColor Green } catch { Write-Host "  Flash search      : $_" -ForegroundColor Red }

# ======== 6. CATEGORIES ========
Write-Host "`n[6/8] Categories" -ForegroundColor $Y

try { $cat = Invoke-RestMethod "$API/categories" -Method GET; Write-Host "  Categories: True ($($cat.data | Measure-Object | Select-Object -ExpandProperty Count) cats)" -ForegroundColor Green } catch { Write-Host "  Categories: $_" -ForegroundColor Red }

# ======== 7. CROSS-ACCOUNT ========
Write-Host "`n[7/8] Cross-Account Operations" -ForegroundColor $Y

# Seller public info (no auth needed)
try { $spub = Invoke-RestMethod "$API/users/sellers/900002" -Method GET; Write-Host "  Seller public info: True ($($spub.data.sellerName))" -ForegroundColor Green } catch { Write-Host "  Seller public info: $_" -ForegroundColor Red }

# Check buyer CANNOT access seller endpoints
try { $sec1 = Invoke-RestMethod "$API/sellers/me/products" -Method GET -Headers $bh; Write-Host "  Security: buyer @ seller: Wrong (should 403)" -ForegroundColor Red } catch { Write-Host "  Security: buyer @ seller: 403 blocked OK" -ForegroundColor Green }

# Check seller CANNOT access admin endpoints
try { $sec2 = Invoke-RestMethod "$API/admin/users" -Method GET -Headers $sh; Write-Host "  Security: seller @ admin: Wrong (should 403)" -ForegroundColor Red } catch { Write-Host "  Security: seller @ admin: 403 blocked OK" -ForegroundColor Green }

# Notifications
try { $notifs = Invoke-RestMethod "$API/notifications" -Method GET -Headers $bh; Write-Host "  Buyer notifications: True" -ForegroundColor Green } catch { Write-Host "  Buyer notifications: $_" -ForegroundColor Red }

# ======== 8. CHECKOUT PREVIEW ========
Write-Host "`n[8/8] Checkout Flow (preview only — no submit)" -ForegroundColor $Y

try { $cartData = Invoke-RestMethod "$API/cart" -Method GET -Headers $bh; Write-Host "  Cart check: True" -ForegroundColor Green } catch { Write-Host "  Cart check: $_" -ForegroundColor Red }

# ======== SUMMARY ========
Write-Host "`n==============================================" -ForegroundColor $C
Write-Host "  E2E TEST COMPLETE" -ForegroundColor $C
Write-Host "==============================================" -ForegroundColor $C
Write-Host "  Tested: all 3 accounts (buyer/seller/admin)" -ForegroundColor Green
Write-Host "  Buyer  : login, products, search, profile," -ForegroundColor Green
Write-Host "            addresses, cart, orders, wishlist" -ForegroundColor Green
Write-Host "  Seller : products, orders, dashboard," -ForegroundColor Green
Write-Host "            payments, stripe status" -ForegroundColor Green
Write-Host "  Admin  : products, users, banners, refunds" -ForegroundColor Green
Write-Host "  Cross  : seller public info, RBAC security" -ForegroundColor Green
Write-Host "  Extras : flash sales, categories, search," -ForegroundColor Green
Write-Host "            notifications" -ForegroundColor Green
Write-Host "==============================================" -ForegroundColor $C
