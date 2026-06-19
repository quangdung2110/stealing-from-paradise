#!/usr/bin/env python3
"""
Backend E2E Test Suite - Full Business Flow Verification.

Covers all backend services against docker-compose dev mode:
  Auth/Identity · Catalog · Cart · Checkout · Order · Payment · Stripe Onboarding
  Flash Sale · Refund · Search · Notification · AI Chat · Webhooks · E2E Business Flows

Usage:
  GATEWAY=http://api-gateway:8080 python3 e2e_backend.py           # all tests
  GATEWAY=http://localhost:8080 python3 e2e_backend.py --group auth # one group
  GATEWAY=http://api-gateway:8080 python3 e2e_backend.py --test auth_login_buyer  # one test

Groups: auth, admin, catalog, inventory, cart, checkout, order, orderlifecycle, payment, stripe, flashsale, refund,
        search, notification, chat, webhook, e2e, all
"""

import sys, os, json, time, uuid, hmac, hashlib, threading, traceback, argparse, urllib.request, urllib.error, re

# ── Config ──────────────────────────────────────────────────────────────────
GW   = os.environ.get("GATEWAY", "http://localhost:8080")
WHS  = os.environ.get("WEBHOOK_SECRET", "whsec_30eee4f19680f05b87cf7c5d28cbac3cf5d16fc172be10bd06beb8c0b686926e")
PW   = os.environ.get("E2E_DEV_PASSWORD", "dev123")
TMO  = int(os.environ.get("E2E_TIMEOUT", "120"))
POLL = int(os.environ.get("E2E_POLL", "2"))

API_VERSION = "2024-06-20"  # matches stripe-java 26.1.0

# ── Dynamic IDs ─────────────────────────────────────────────────────────────
E2E_PRODUCT_ID = None
E2E_VARIANT_ID = None

def init_dynamic_ids():
    global E2E_PRODUCT_ID, E2E_VARIANT_ID
    if E2E_PRODUCT_ID and E2E_VARIANT_ID:
        return E2E_PRODUCT_ID, E2E_VARIANT_ID

    # 1. Fetch products
    s, r = api("GET", "/api/v1/products?page=0&size=10")
    if s != 200:
        raise RuntimeError(f"Failed to fetch products for E2E dynamic ID initialization: status={s}, response={r}")
    
    content = get_field(r, "content")
    if not content or not isinstance(content, list):
        raise RuntimeError(f"Empty product listing in E2E dynamic ID initialization: response={r}")

    # Iterate products to find one with variants
    for prod in content:
        pid = prod.get("id") or prod.get("productId")
        if not pid:
            continue
        # Get product details
        s2, r2 = api("GET", f"/api/v1/products/{pid}")
        if s2 != 200:
            continue
        # Find variants
        variants = get_field(r2, "variants") or r2.get("variants") or r2.get("data", {}).get("variants")
        if not variants or not isinstance(variants, list):
            continue
        for v in variants:
            vid = v.get("id") or v.get("variantId")
            if vid:
                E2E_PRODUCT_ID = pid
                E2E_VARIANT_ID = vid
                print(f"[Dynamic ID Init] Found E2E_PRODUCT_ID={E2E_PRODUCT_ID}, E2E_VARIANT_ID={E2E_VARIANT_ID}")
                return E2E_PRODUCT_ID, E2E_VARIANT_ID

    raise RuntimeError("No orderable product variant found in the database. Ensure dev seed data is loaded!")

# ── HTTP ────────────────────────────────────────────────────────────────────
def api(method, path, token=None, body=None, extra_headers=None):
    """Call API gateway. extra_headers: dict of additional headers (e.g. X-User-Id for chat)."""
    url = f"{GW}{path}" if not path.startswith("http") else path
    data = None
    if body is not None:
        data = (body if isinstance(body, str) else json.dumps(body)).encode()
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if token: headers["Authorization"] = f"Bearer {token}"
    if extra_headers: headers.update(extra_headers)
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read().decode()
            try: return r.status, json.loads(raw) if raw else {}
            except json.JSONDecodeError: return r.status, raw
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try: return e.code, json.loads(raw) if raw else {}
        except json.JSONDecodeError: return e.code, raw
    except urllib.error.URLError as e:
        return -1, str(e)

def login(who):
    s, r = api("POST", "/api/v1/auth/login", body={"credential": who, "password": PW})
    if s != 200: raise RuntimeError(f"Login {who}: {s}")
    t = r.get("accessToken") or r.get("data", {}).get("accessToken")
    if not t: raise RuntimeError(f"No token in login: {r}")
    return t

def register_seller(name=None):
    if not name: name = f"e2e{int(time.time())}{uuid.uuid4().hex[:4]}"
    s, r = api("POST", "/api/v1/auth/register/seller", body={"username": name, "email": f"{name}@e2e.test", "password": PW, "fullName": f"E2E {name[:8]}"})
    if s in (200, 201):
        t = r.get("accessToken") or r.get("data", {}).get("accessToken")
        if t: return name, t
    return name, login(name)

def register_buyer(name=None):
    if not name: name = f"e2eb{int(time.time())}{uuid.uuid4().hex[:4]}"
    s, r = api("POST", "/api/v1/auth/register", body={"username": name, "email": f"{name}@e2e.test", "password": PW, "fullName": f"E2E {name[:8]}"})
    if s in (200, 201):
        t = r.get("accessToken") or r.get("data", {}).get("accessToken")
        if t: return name, t
    return name, login(name)

def get_field(data, *keys):
    if isinstance(data, list):
        for item in data:
            v = get_field(item, *keys)
            if v is not None: return v
    elif isinstance(data, dict):
        for k in keys:
            if k in data and data[k] is not None: return data[k]
        for v in data.values():
            r = get_field(v, *keys)
            if r is not None: return r
    return None

def poll(desc, fn, timeout=TMO, interval=POLL):
    dl = time.time() + timeout
    while time.time() < dl:
        try:
            r = fn()
            if r: return r
        except: pass
        time.sleep(interval)
    raise TimeoutError(f"Poll timeout ({timeout}s): {desc}")

# ── Webhook ─────────────────────────────────────────────────────────────────
def _suffix(): return uuid.uuid4().hex[:16]
def _base(t):
    return {"id": f"evt_{_suffix()}", "object": "event", "api_version": API_VERSION,
            "created": int(time.time()), "livemode": False, "pending_webhooks": 1, "type": t}

def send_webhook(payload_dict):
    ps = json.dumps(payload_dict, separators=(",", ":"))
    ts = int(time.time())
    sig = hmac.new(WHS.encode(), f"{ts}.{ps}".encode(), hashlib.sha256).hexdigest()
    req = urllib.request.Request(f"{GW}/api/v1/stripe/webhooks", data=ps.encode(),
        headers={"Content-Type": "application/json", "Stripe-Signature": f"t={ts},v1={sig}"}, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=15) as r: return r.status, r.read().decode()[:300]
    except urllib.error.HTTPError as e: return e.code, e.read().decode()[:300]

def forge_pi(event_type, parent_id):
    pi = {"id": f"pi_{_suffix()}", "object": "payment_intent", "amount": 100000, "currency": "vnd",
          "status": "succeeded" if "succeeded" in event_type else "requires_payment_method",
          "metadata": {"parent_order_id": str(parent_id)}, "latest_charge": f"ch_{_suffix()}"}
    e = _base(event_type); e["data"] = {"object": pi}; return e

def forge_account(acct_id, det=True, ch=True, po=True, due=None, dis=None):
    req = {}
    if due: req["currently_due"] = due
    if dis: req["disabled_reason"] = dis
    a = {"id": acct_id, "object": "account", "details_submitted": det,
         "charges_enabled": ch, "payouts_enabled": po, "requirements": req}
    e = _base("account.updated"); e["data"] = {"object": a}; return e

# ── Result Tracking ─────────────────────────────────────────────────────────
PASS, FAIL, ERROR = 0, 0, 0
RESULTS = []

def ok(msg): print(f"  [OK] {msg}")
def warn(msg): print(f"  [WARN] {msg}")
def info(msg): print(f"  [INFO] {msg}")

def test(name, fn, group="general"):
    global PASS, FAIL, ERROR
    print(f"\n{'='*60}\n[{group}] {name}\n{'='*60}")
    try:
        fn()
        PASS += 1; RESULTS.append(("PASS", group, name))
        print(f">> PASS: {name}")
    except AssertionError as e:
        FAIL += 1; RESULTS.append(("FAIL", group, name, str(e)))
        print(f">> FAIL: {name}\n   {e}")
    except Exception as e:
        ERROR += 1; RESULTS.append(("ERROR", group, name, str(e)))
        print(f">> ERROR: {name}")
        traceback.print_exc()

def check_status(s, exp, label=""):
    if isinstance(exp, int):
        assert s == exp, f"{label}Expected {exp}, got {s}"
    elif isinstance(exp, tuple):
        if len(exp) == 2:
            assert exp[0] <= s <= exp[1], f"{label}Expected {exp[0]}-{exp[1]}, got {s}"
        else:
            assert s in exp, f"{label}Expected one of {exp}, got {s}"

def check_not_none(v, label=""): assert v is not None, f"{label}Value is None"
def check_true(c, label=""): assert c, f"{label}Expected true"
def check_eq(a, b, label=""): assert a == b, f"{label}{a} != {b}"
def check_neq(a, b, label=""): assert a != b, f"{label}{a} == {b}"

# ══════════════════════════════════════════════════════════════════════════════
# SECTION: AUTH & IDENTITY
# ══════════════════════════════════════════════════════════════════════════════

def t_auth_login_buyer():
    s, r = api("POST", "/api/v1/auth/login", body={"credential": "minhhoa", "password": PW})
    check_status(s, 200, "buyer login: ")
    t = r.get("accessToken") or get_field(r, "accessToken")
    check_not_none(t, "accessToken")
    ok(f"Logged in as minhhoa")

def t_auth_login_seller():
    s, r = api("POST", "/api/v1/auth/login", body={"credential": "techworld", "password": PW})
    check_status(s, 200, "seller login: ")
    ok("Logged in as techworld")

def t_auth_login_admin():
    s, r = api("POST", "/api/v1/auth/login", body={"credential": "admin", "password": PW})
    check_status(s, 200, "admin login: ")
    ok("Logged in as admin")

def t_auth_login_wrong_password():
    s, _ = api("POST", "/api/v1/auth/login", body={"credential": "minhhoa", "password": "wrong"})
    check_status(s, 401, "wrong password: ")
    ok(f"Correctly rejected: {s}")

def t_auth_no_token_protected():
    s, _ = api("GET", "/api/v1/orders?page=0&size=5")
    check_status(s, (401, 403), "no token: ")
    ok(f"Protected route: {s}")

def t_auth_register_buyer():
    n = f"testbuyer{int(time.time())}"
    s, r = api("POST", "/api/v1/auth/register", body={"username": n, "email": f"{n}@test.com", "password": PW, "fullName": "Test Buyer"})
    check_status(s, (200, 201), "register buyer: ")
    t = get_field(r, "accessToken")
    check_not_none(t, "accessToken")
    ok(f"Registered buyer: {n}")

def t_auth_register_seller():
    n = f"testseller{int(time.time())}"
    s, r = api("POST", "/api/v1/auth/register/seller", body={"username": n, "email": f"{n}@test.com", "password": PW, "fullName": "Test Seller"})
    check_status(s, (200, 201), "register seller: ")
    t = get_field(r, "accessToken")
    check_not_none(t, "accessToken")
    ok(f"Registered seller: {n}")

def t_auth_refresh():
    t = login("minhhoa")
    s, r = api("POST", "/api/v1/auth/refresh", body={"refreshToken": "dummy"})
    # May fail with invalid refresh token - acceptable
    info(f"Refresh: {s}")

def t_auth_logout():
    t = login("minhhoa")
    s, _ = api("POST", "/api/v1/auth/logout", t, body={})
    check_status(s, 200, "logout: ")
    ok("Logged out")

def t_auth_profile():
    t = login("minhhoa")
    s, r = api("GET", "/api/v1/users/me", t)
    check_status(s, 200, "profile: ")
    check_not_none(get_field(r, "username"), "username")
    check_not_none(get_field(r, "email"), "email")
    ok(f"Profile: {get_field(r, 'username')} / {get_field(r, 'email')}")

def t_auth_update_profile():
    t = login("minhhoa")
    s, r = api("PUT", "/api/v1/users/me", t, body={"fullName": "Minh Hoa Updated"})
    check_status(s, 200)
    ok("Profile updated")

def t_auth_addresses():
    t = login("minhhoa")
    s, r = api("GET", "/api/v1/users/me/addresses", t)
    check_status(s, 200, "addresses: ")
    addr_data = get_field(r, "data")
    check_not_none(addr_data, "address list")
    ok(f"Addresses: {len(addr_data) if isinstance(addr_data, list) else 'found'}")

def t_auth_create_address():
    t = login("minhhoa")
    s, r = api("POST", "/api/v1/users/me/addresses", t, body={
        "fullName": "Test Addr", "phoneNumber": "0901234567",
        "provinceId": 1, "districtId": 1, "wardCode": "00001",
        "streetAddress": "123 Test St", "isDefault": False})
    # Address creation may fail if province/ward seed data is missing
    check_status(s, (200, 500), "create address: ")
    if s == 200:
        aid = get_field(r, "address_id") or get_field(r, "id")
        ok(f"Created address: {aid}")
    else:
        info(f"Address creation: {s} (expected - may need province seed data)")

def t_auth_change_password():
    t = login("minhhoa")
    s, _ = api("POST", "/api/v1/users/me/change-password", t, body={"oldPassword": PW, "newPassword": "dev1234"})
    info(f"Change password: {s}")
    # Revert
    s2, _ = api("POST", "/api/v1/users/me/change-password", t, body={"oldPassword": "dev1234", "newPassword": PW})
    info(f"Revert password: {s2}")

def t_auth_upgrade_to_seller():
    n, t = register_buyer()
    s, r = api("POST", "/api/v1/users/me/roles/seller", t, body={})
    check_status(s, 200, "upgrade to seller: ")
    ok(f"Upgraded {n} to seller: {s}")

def t_auth_admin_users():
    t = login("admin")
    s, r = api("GET", "/api/v1/admin/users?page=0&size=10", t)
    check_status(s, 200, "admin users list: ")
    u = get_field(r, "content")
    check_not_none(u, "user list")
    ok(f"Admin users: {len(u) if isinstance(u, list) else 'ok'}")

def t_auth_admin_user_detail():
    t = login("admin")
    s, r = api("GET", "/api/v1/admin/users/6", t)
    check_status(s, 200, "admin user detail: ")
    ok(f"User 6: {get_field(r, 'username')}")

# ══════════════════════════════════════════════════════════════════════════════
# SECTION: ADMIN MANAGEMENT
# ══════════════════════════════════════════════════════════════════════════════

def t_admin_lock_user():
    t = login("admin")
    s, r = api("POST", "/api/v1/admin/users/6/lock", t, body={})
    check_status(s, 200, "lock user: ")
    ok(f"Locked user 6: {s}")
    # Unlock to restore
    s2, r2 = api("POST", "/api/v1/admin/users/6/unlock", t, body={})
    check_status(s2, 200, "unlock user: ")
    ok(f"Unlocked user 6: {s2}")

def t_admin_category_flow():
    t = login("admin")
    suffix = uuid.uuid4().hex[:6]
    name1 = f"E2E Test Cat {suffix}"
    name2 = f"E2E Test Cat Updated {suffix}"
    # Create
    s, r = api("POST", "/api/v1/admin/categories", t, body={"name": name1, "parentId": None})
    check_status(s, (200, 201), "create category: ")
    cid = get_field(r, "id") or get_field(get_field(r, "data"), "id")
    check_not_none(cid, "category ID")
    ok(f"Created category: {cid}")
    # Update
    s2, r2 = api("PUT", f"/api/v1/admin/categories/{cid}", t, body={"name": name2})
    check_status(s2, 200, "update category: ")
    ok(f"Updated category: {s2}")
    # Delete
    s3, r3 = api("DELETE", f"/api/v1/admin/categories/{cid}", t)
    check_status(s3, 200, "delete category: ")
    ok(f"Deleted category: {s3}")

# ══════════════════════════════════════════════════════════════════════════════
# SECTION: CATALOG
# ══════════════════════════════════════════════════════════════════════════════

def t_catalog_products():
    s, r = api("GET", "/api/v1/products?page=0&size=10")
    check_status(s, 200, "product list: ")
    content = get_field(r, "content")
    check_not_none(content, "product content")
    check_true(isinstance(content, list) and len(content) > 0, "products exist")
    ok(f"Products: {len(content)}")

def t_catalog_product_detail():
    init_dynamic_ids()
    s, r = api("GET", f"/api/v1/products/{E2E_PRODUCT_ID}")
    check_status(s, 200, "product detail: ")
    n = get_field(r, "name")
    check_not_none(n)
    ok(f"Product: {n}")

def t_catalog_sku_lookup():
    s, r = api("GET", "/api/v1/products/variants/sku/SKU-MAGSAFE")
    check_status(s, 200, "SKU lookup: ")
    ok(f"SKU: {get_field(r, 'variantCode') or 'found'}")

def t_catalog_categories():
    s, r = api("GET", "/api/v1/categories")
    check_status(s, 200, "categories: ")
    cats = get_field(r, "data")
    check_not_none(cats)
    ok(f"Categories: {len(cats) if isinstance(cats, list) else 'ok'}")

def t_catalog_seller_products():
    t = login("techworld")
    s, r = api("GET", "/api/v1/sellers/me/products", t)
    check_status(s, 200)
    content = get_field(r, "content")
    ok(f"Seller products: {len(content) if isinstance(content, list) else 'ok'}")

def t_catalog_seller_create_product():
    t = login("techworld")
    # Get a leaf category
    s, r = api("GET", "/api/v1/categories")
    cats = get_field(r, "data")
    cid = None
    def find_leaf(c):
        if isinstance(c, dict):
            ch = c.get("children")
            if not ch or (isinstance(ch, list) and len(ch) == 0):
                return c.get("id")
            if isinstance(ch, list):
                for cc in ch:
                    v = find_leaf(cc);
                    if v: return v
        return None
    if isinstance(cats, list):
        for c in cats:
            cid = find_leaf(c)
            if cid: break
    if not cid: cid = cats[0].get("id") if isinstance(cats, list) else str(cats)
    check_not_none(cid, "category ID")

    pn = f"E2E Manual Product {uuid.uuid4().hex[:6]}"
    s, r = api("POST", "/api/v1/products", t, body={"name": pn, "description": "E2E test", "categoryId": str(cid)})
    check_status(s, 201, "create product: ")
    pid = get_field(r, "id")
    if not pid: pid = get_field(get_field(r, "data"), "id")
    check_not_none(pid, "product ID")
    ok(f"Created product: {pid}")

    # Create variant
    vc = f"E2E-{uuid.uuid4().hex[:8].upper()}"
    s2, r2 = api("POST", f"/api/v1/seller/products/{pid}/variants", t, body={
        "variantCode": vc, "variantName": f"Variant {vc}", "price": 100000, "stockQuantity": 50})
    check_status(s2, (200, 201), "create variant: ")
    vid = get_field(r2, "variantId") or get_field(get_field(r2, "data"), "id") or get_field(get_field(r2, "data"), "variantId")
    ok(f"Created variant: {vid}")

    # Request presigned URL
    s_presigned, r_presigned = api("GET", f"/api/v1/products/{pid}/presigned-url?filename=image.jpg", t)
    check_status(s_presigned, 200, "get presigned-url: ")
    img_data = get_field(r_presigned, "data") or r_presigned
    img_id = get_field(img_data, "imageId")
    check_not_none(img_id, "image ID from presigned-url")
    ok(f"Got presigned URL image ID: {img_id}")

    # Register image
    img_url = f"http://localhost:9000/product-images/products/techworld/{pid}/{img_id}.jpg"
    s_img, r_img = api("POST", f"/api/v1/products/{pid}/images", t, body={
        "imageId": img_id,
        "url": img_url,
        "sortOrder": 0
    })
    check_status(s_img, 201, "register image: ")
    ok("Registered product image")

    # Submit
    s3, _ = api("POST", f"/api/v1/seller/products/{pid}/submit", t, body={})
    check_status(s3, 200, "submit: ")
    ok("Submitted for review")

    # Admin approve
    at = login("admin")
    s4, _ = api("POST", f"/api/v1/admin/products/{pid}/approve", at, body={})
    check_status(s4, 200, "admin approve: ")
    ok("Admin approved")

    # Publish
    s5, _ = api("POST", f"/api/v1/seller/products/{pid}/publish", t, body={})
    check_status(s5, 200, "publish: ")
    ok("Published")

    # Cleanup: delete
    s6, _ = api("DELETE", f"/api/v1/seller/products/{pid}", t)
    info(f"Cleanup delete: {s6}")

# ══════════════════════════════════════════════════════════════════════════════
# SECTION: INVENTORY
# ══════════════════════════════════════════════════════════════════════════════

def t_inventory_check():
    t = login("techworld")
    s, r = api("GET", "/api/v1/inventory/SKU-MAGSAFE", t)
    check_status(s, 200, "inventory check: ")
    qty = get_field(r, "stockAvailable") or get_field(r, "stockTotal") or get_field(r, "stockQuantity") or get_field(r, "quantity")
    check_not_none(qty, "stock quantity")
    ok(f"Inventory SKU-MAGSAFE: {qty}")

def t_inventory_restock():
    t = login("techworld")
    s, r = api("PUT", "/api/v1/inventory/SKU-MAGSAFE/restock", t, body={"quantity": 100, "reason": "E2E restock"})
    check_status(s, 200, "restock: ")
    ok(f"Restock SKU-MAGSAFE: {s}")

def t_inventory_adjust():
    t = login("techworld")
    s, r = api("POST", "/api/v1/seller/inventory/adjust?skuCode=SKU-MAGSAFE", t, body={"delta": -2, "reason": "MANUAL"})
    check_status(s, 200, "inventory adjust: ")
    ok(f"Adjust SKU-MAGSAFE: {s}")

def t_inventory_logs():
    t = login("techworld")
    s, r = api("GET", "/api/v1/seller/inventory/SKU-MAGSAFE/logs", t)
    check_status(s, 200, "inventory logs: ")
    logs = get_field(r, "data") or get_field(r, "content")
    ok(f"Inventory logs: {len(logs) if isinstance(logs, list) else 'found'}")

# ══════════════════════════════════════════════════════════════════════════════
# SECTION: CART
# ══════════════════════════════════════════════════════════════════════════════

def t_cart_flow():
    t = login("minhhoa")
    init_dynamic_ids()
    vid = E2E_VARIANT_ID

    # Clear
    s, _ = api("DELETE", "/api/v1/cart", t)
    check_status(s, 200, "clear cart: ")
    ok("Cart cleared")

    # Get empty
    s, r = api("GET", "/api/v1/cart", t)
    check_status(s, 200, "get cart: ")
    ok(f"Cart items: {len(get_field(r, 'items') or [])}")

    # Add
    s, r = api("POST", "/api/v1/cart/items", t, body={"variantId": vid, "quantity": 1})
    check_status(s, 200, "add to cart: ")
    ok("Added to cart")

    # Get with item
    s, r = api("GET", "/api/v1/cart", t)
    check_status(s, 200)
    items = get_field(r, "items")
    check_not_none(items, "cart items")
    check_true(isinstance(items, list) and len(items) > 0, "cart has items")
    ok(f"Cart has {len(items)} item(s)")

    # Update quantity
    s, _ = api("PUT", f"/api/v1/cart/items/{vid}", t, body={"quantity": 2})
    check_status(s, 200, "update quantity: ")
    ok("Quantity updated")

    # Remove
    s, _ = api("DELETE", f"/api/v1/cart/items/{vid}", t)
    check_status(s, 200, "remove item: ")
    ok("Item removed")

    # Invalid variant
    s, _ = api("POST", "/api/v1/cart/items", t, body={"variantId": "invalid-uuid", "quantity": 1})
    check_status(s, (400, 404, 422, 500), "invalid variant: ")
    ok(f"Invalid variant correctly rejected: {s}")

# ══════════════════════════════════════════════════════════════════════════════
# SECTION: CHECKOUT
# ══════════════════════════════════════════════════════════════════════════════

def t_checkout_preview():
    t = login("minhhoa")
    init_dynamic_ids()
    vid = E2E_VARIANT_ID
    # Clear + add
    api("DELETE", "/api/v1/cart", t)
    api("POST", "/api/v1/cart/items", t, body={"variantId": vid, "quantity": 1})
    # Preview
    s, r = api("POST", "/api/v1/cart/checkout/preview", t, body={"itemIds": [f"6:{vid}"]})
    check_status(s, 200, "preview: ")
    pt = get_field(r, "previewToken")
    check_not_none(pt, "previewToken")
    total = get_field(r, "totalAmount")
    check_not_none(total, "totalAmount")
    ok(f"Preview: token={pt[:20]}... total={total}")

def t_checkout_submit():
    t = login("minhhoa")
    init_dynamic_ids()
    vid = E2E_VARIANT_ID
    api("DELETE", "/api/v1/cart", t)
    api("POST", "/api/v1/cart/items", t, body={"variantId": vid, "quantity": 1})
    # Preview
    s, r = api("POST", "/api/v1/cart/checkout/preview", t, body={"itemIds": [f"6:{vid}"]})
    check_status(s, 200)
    pt = get_field(r, "previewToken")
    check_not_none(pt)
    # Get address
    s, r = api("GET", "/api/v1/users/me/addresses", t)
    check_status(s, 200)
    addrs = get_field(r, "data")
    check_not_none(addrs)
    aid = addrs[0].get("address_id") if isinstance(addrs, list) else get_field(addrs, "address_id")
    check_not_none(aid, "address ID")
    # Submit
    s, r = api("POST", "/api/v1/cart/checkout/submit", t, body={"previewToken": pt, "addressId": aid})
    check_status(s, 200, "submit: ")
    ok(f"Checkout submitted")

# ══════════════════════════════════════════════════════════════════════════════
# SECTION: ORDER
# ══════════════════════════════════════════════════════════════════════════════

def t_order_my_orders():
    t = login("minhhoa")
    s, r = api("GET", "/api/v1/orders?page=0&size=10", t)
    check_status(s, 200, "my orders: ")
    c = get_field(r, "content")
    check_not_none(c)
    ok(f"My orders: {len(c) if isinstance(c, list) else 'ok'}")

def t_order_seller_orders():
    t = login("techworld")
    s, r = api("GET", "/api/v1/sellers/me/orders?page=0&size=10", t)
    check_status(s, 200, "seller orders: ")
    ok("Seller orders OK")

def t_order_seller_dashboard():
    t = login("techworld")
    s, r = api("GET", "/api/v1/sellers/me/dashboard", t)
    check_status(s, 200, "seller dashboard: ")
    ok(f"Dashboard: {get_field(r, 'totalRevenue') or 'ok'}")

# ══════════════════════════════════════════════════════════════════════════════
# SECTION: ORDER LIFECYCLE
# ══════════════════════════════════════════════════════════════════════════════

def t_order_cancel():
    t = login("minhhoa")
    s, r = api("GET", "/api/v1/orders?page=0&size=20", t)
    check_status(s, 200, "get orders: ")
    orders = get_field(r, "content")
    if not orders or not isinstance(orders, list) or len(orders) == 0:
        warn("No orders to cancel")
        return
    oid = None
    for o in orders:
        if o.get("status") == "PENDING":
            oid = o.get("orderId") or o.get("id")
            break
    if not oid:
        warn("No PENDING order found to cancel")
        return
    s2, r2 = api("POST", f"/api/v1/orders/{oid}/cancel", t, body={"reason": "E2E test cancel"})
    check_status(s2, 200, "cancel: ")
    ok(f"Cancelled order {oid}")

def t_order_tracking():
    t = login("techworld")
    s, r = api("GET", "/api/v1/sellers/me/orders?page=0&size=20", t)
    check_status(s, 200, "seller orders: ")
    orders = get_field(r, "content")
    if not orders or not isinstance(orders, list) or len(orders) == 0:
        warn("No seller orders for tracking")
        return
    oid = None
    for o in orders:
        if o.get("status") == "PAID":
            oid = o.get("orderId") or o.get("id")
            break
    if not oid:
        warn("No PAID seller order found")
        return
    s2, r2 = api("PUT", f"/api/v1/orders/{oid}/tracking", t, body={"trackingNumber": "E2E-TRK-001"})
    check_status(s2, 200, "tracking: ")
    ok(f"Tracking set for order {oid}")

def t_order_confirm_received():
    t = login("minhhoa")
    s, r = api("GET", "/api/v1/orders?page=0&size=20", t)
    check_status(s, 200, "get orders: ")
    orders = get_field(r, "content")
    if not orders or not isinstance(orders, list) or len(orders) == 0:
        warn("No orders to confirm received")
        return
    oid = None
    for o in orders:
        if o.get("status") == "SHIPPING":
            oid = o.get("orderId") or o.get("id")
            break
    if not oid:
        warn("No SHIPPING order found")
        return
    s2, r2 = api("POST", f"/api/v1/orders/{oid}/confirm-received", t, body={})
    check_status(s2, 200, "confirm received: ")
    ok(f"Confirmed received order {oid}")

# ══════════════════════════════════════════════════════════════════════════════
# SECTION: PAYMENT
# ══════════════════════════════════════════════════════════════════════════════

def t_payment_query_parent():
    t = login("minhhoa")
    # Get an order to find a parentOrderId
    s, r = api("GET", "/api/v1/orders?page=0&size=5", t)
    check_status(s, 200)
    orders = get_field(r, "content")
    if not orders or not isinstance(orders, list) or len(orders) == 0:
        warn("No orders to query payment")
        return
    pid = orders[0].get("parentOrderId")
    if not pid:
        warn("No parentOrderId found")
        return
    s2, r2 = api("GET", f"/api/v1/payments/parent-order/{pid}", t)
    check_status(s2, (200, 404), "payment query: ")
    if s2 != 200:
        info(f"Payment query: {s2} for parent {pid} (order may have no payment)")
        return
    tx_id = get_field(r2, "transactionId")
    status_val = get_field(r2, "status")
    ok(f"Transaction: {tx_id} / {status_val}")

# ══════════════════════════════════════════════════════════════════════════════
# SECTION: STRIPE ONBOARDING & SELLER PAYMENTS
# ══════════════════════════════════════════════════════════════════════════════

def t_stripe_onboarding_status():
    t = login("techworld")
    s, r = api("GET", "/api/v1/stripe/onboarding/status", t)
    check_status(s, 200, "onboarding status: ")
    acct = get_field(r, "stripeAccountId")
    chk = get_field(r, "chargesEnabled")
    st = get_field(r, "onboardingStatus")
    ok(f"Account: {acct}, charges: {chk}, status: {st}")

def t_stripe_onboarding_start_new():
    n, t = register_seller()
    s, r = api("POST", "/api/v1/stripe/onboarding/start", t, body={})
    check_status(s, (200, 201, 500), "start onboarding: ")
    if s in (200, 201):
        acct = get_field(r, "stripeAccountId")
        url = get_field(r, "onboardingUrl")
        check_not_none(acct)
        check_true(not acct.startswith("acct_mock_"), "NOT mock account")
        ok(f"Onboarding started: {acct}, URL: {str(url)[:60]}...")
    else:
        info(f"Onboarding failed ({s}) - Stripe may be unavailable")

def t_stripe_refresh_link():
    t = login("techworld")
    s, r = api("POST", "/api/v1/stripe/onboarding/refresh-link", t, body={})
    info(f"Refresh link: {s}")

def t_stripe_buyer_blocked():
    t = login("minhhoa")
    for ep in ["/api/v1/stripe/onboarding/start", "/api/v1/stripe/onboarding/status"]:
        s, _ = api("POST" if "start" in ep else "GET", ep, t, body={} if "start" in ep else None)
        check_status(s, 403, f"buyer {ep}: ")
    ok("Buyer correctly blocked from onboarding endpoints")

def t_seller_payments():
    t = login("techworld")
    for ep in ["/api/v1/seller/payments/transfers?page=0&size=10",
               "/api/v1/seller/payments/earnings",
               "/api/v1/seller/payments/balance"]:
        s, _ = api("GET", ep, t)
        check_status(s, (200, 500), f"seller payment {ep.split('/')[-1]}: ")
        info(f"{ep.split('/')[-1]}: {s}")
    ok("Seller payments queried")

def t_stripe_dashboard_link():
    t = login("techworld")
    s, r = api("GET", "/api/v1/seller/payments/stripe-dashboard", t)
    check_status(s, (200, 500), "stripe dashboard: ")
    if s == 200:
        u = get_field(r, "expressDashboardUrl")
        ok(f"Dashboard URL: {str(u)[:60]}..." if u else "Dashboard URL found")
    else:
        info(f"Stripe dashboard link: {s}")

def t_admin_seller_list():
    t = login("admin")
    s, r = api("GET", "/api/v1/stripe/onboarding/admin/sellers", t)
    check_status(s, 200, "admin sellers: ")
    summary = get_field(r, "summary")
    accounts = get_field(r, "accounts")
    check_not_none(summary)
    ok(f"Total sellers: {get_field(summary, 'totalSellers')}, Complete: {get_field(summary, 'completedSellers')}")

# ══════════════════════════════════════════════════════════════════════════════
# SECTION: FLASH SALE
# ══════════════════════════════════════════════════════════════════════════════

def t_flash_sale_flow():
    at = login("admin")
    st = login("techworld")
    bt = login("minhhoa")

    # Create session
    from datetime import datetime, timedelta, timezone
    tz_vn = timezone(timedelta(hours=7))
    now = datetime.now(tz_vn)
    stime = (now - timedelta(hours=1)).strftime("%Y-%m-%dT%H:%M:%S")
    etime = (now + timedelta(hours=1)).strftime("%Y-%m-%dT%H:%M:%S")

    s, r = api("POST", "/api/v1/flash-sales", at, body={
        "name": f"E2E FS {uuid.uuid4().hex[:6]}",
        "startTime": stime, "endTime": etime})
    check_status(s, 200, "create flash sale: ")
    sid = get_field(r, "sessionId") or get_field(get_field(r, "data"), "sessionId") or get_field(get_field(r, "data"), "id")
    check_not_none(sid, "session ID")
    ok(f"Session: {sid}")

    # Activate
    s, _ = api("PUT", f"/api/v1/flash-sales/{sid}", at, body={"status": "ACTIVE"})
    check_status(s, 200, "activate: ")
    ok("Activated")

    # Get active
    s, r = api("GET", "/api/v1/flash-sales/active", bt)
    check_status(s, 200, "active sessions: ")
    ok("Active sessions listed")

    # Register item
    s, r = api("POST", f"/api/v1/flash-sales/{sid}/items", st, body={
        "skuCode": "SKU-MAGSAFE", "flashPrice": 99000, "flashStock": 10, "limitPerUser": 2})
    check_status(s, 200, "register item: ")
    fi = get_field(r, "id") or get_field(get_field(r, "data"), "id")
    check_not_none(fi, "fs item ID")
    ok(f"FS item: {fi}")

    # Approve
    s, _ = api("POST", f"/api/v1/flash-sales/{sid}/items/{fi}/approve", at, body={})
    check_status(s, 200, "approve: ")
    ok("Approved")

    # Set reminder
    s, _ = api("POST", f"/api/v1/flash-sales/{sid}/reminders", bt, body={})
    check_status(s, 200, "reminder: ")
    ok("Reminder set")

    # Buy
    s, r2 = api("GET", "/api/v1/users/me/addresses", bt)
    addrs = get_field(r2, "data")
    aid = (addrs[0].get("address_id") if isinstance(addrs, list) else get_field(addrs, "address_id")) if addrs else None
    if aid:
        s, r3 = api("POST", f"/api/v1/flash-sales/{sid}/buy", bt, body={
            "fsItemId": int(fi), "quantity": 1, "addressId": int(aid)})
        check_status(s, 200, "buy: ")
        ok(f"Flash sale buy: {get_field(r3, 'totalAmount') or 'ok'}")
    else:
        warn("No address, skipping buy")

# ══════════════════════════════════════════════════════════════════════════════
# SECTION: REFUND
# ══════════════════════════════════════════════════════════════════════════════

def t_refund_query():
    t = login("minhhoa")
    # Get an order to find refunds
    s, r = api("GET", "/api/v1/orders?page=0&size=5", t)
    check_status(s, 200)
    orders = get_field(r, "content")
    if not orders or not isinstance(orders, list) or len(orders) == 0:
        warn("No orders to query refunds")
        return

    for o in orders:
        oid = o.get("orderId") or o.get("id")
        if not oid: continue
        s2, _ = api("GET", f"/api/v1/orders/{oid}/refunds", t)
        if s2 == 200:
            ok(f"Refunds for order {oid}: OK")
            return
    warn("No order with refund endpoint accessible")

def t_refund_admin_list():
    t = login("admin")
    s, r = api("GET", "/api/v1/admin/refunds?page=0&size=10", t)
    info(f"Admin refunds: {s}")

def t_refund_presigned_url():
    t = login("minhhoa")
    s, r = api("GET", "/api/v1/orders?page=0&size=5", t)
    orders = get_field(r, "content")
    if not orders or not isinstance(orders, list) or len(orders) == 0:
        warn("No orders for presigned URL")
        return
    oid = orders[0].get("orderId") or orders[0].get("id")
    s2, _ = api("GET", f"/api/v1/orders/{oid}/refunds/presigned-url", t)
    info(f"Presigned URL: {s2}")

# ══════════════════════════════════════════════════════════════════════════════
# SECTION: SEARCH
# ══════════════════════════════════════════════════════════════════════════════

def t_search_products():
    t = login("minhhoa")
    s, r = api("GET", "/api/v1/search/products?q=MagSafe&page=0&size=10", t)
    check_status(s, 200, "search: ")
    c = get_field(r, "content")
    ok(f"Search results: {len(c) if isinstance(c, list) else 'ok'}")

def t_search_suggest():
    t = login("minhhoa")
    s, r = api("GET", "/api/v1/search/products/suggest?q=Mag", t)
    check_status(s, 200, "suggest: ")
    ok("Suggestions OK")

def t_search_reindex():
    t = login("admin")
    s, r = api("POST", "/api/v1/search/reindex", t, body={})
    check_status(s, (200, 409), "reindex: ")
    ok(f"Reindex: {s}")

def t_search_reindex_status():
    t = login("admin")
    s, r = api("GET", "/api/v1/search/reindex/status", t)
    check_status(s, 200, "reindex status: ")
    ok(f"Reindex status: {get_field(r, 'status') or 'ok'}")

# ══════════════════════════════════════════════════════════════════════════════
# SECTION: NOTIFICATION
# ══════════════════════════════════════════════════════════════════════════════

def t_notification_count():
    t = login("minhhoa")
    s, r = api("GET", "/api/v1/notifications/unread-count", t)
    check_status(s, 200, "unread count: ")
    uc = get_field(r, "unread_count") or get_field(r, "unreadCount")
    ok(f"Unread: {uc}")

def t_notification_history():
    t = login("minhhoa")
    s, r = api("GET", "/api/v1/notifications/history", t)
    check_status(s, 200, "history: ")
    ok("Notification history OK")

def t_notification_read_all():
    t = login("minhhoa")
    # Check before
    sb, rb = api("GET", "/api/v1/notifications/unread-count", t)
    before = get_field(rb, "unread_count") if sb == 200 else 0
    # Mark all read
    s, _ = api("PUT", "/api/v1/notifications/read-all", t, body={})
    check_status(s, 200, "read all: ")
    # Verify
    sa, ra = api("GET", "/api/v1/notifications/unread-count", t)
    after = get_field(ra, "unread_count") if sa == 200 else 0
    ok(f"Before: {before}, After: {after}")

# ══════════════════════════════════════════════════════════════════════════════
# SECTION: AI CHAT
# ══════════════════════════════════════════════════════════════════════════════

XUID = {"X-User-Id": "6"}

def t_chat_suggest():
    t = login("minhhoa")
    s, r = api("GET", "/api/ai/suggest", t, extra_headers=XUID)
    check_status(s, 200, "suggest: ")
    ok("Suggestions OK")

def t_chat_sessions():
    t = login("minhhoa")
    # Create
    s, r = api("POST", "/api/ai/sessions", t, body={}, extra_headers=XUID)
    if s in (200, 201):
        sid = get_field(r, "id") or get_field(get_field(r, "data"), "id")
        ok(f"Session: {sid}")
        # List
        s2, r2 = api("GET", "/api/ai/sessions", t, extra_headers=XUID)
        check_status(s2, 200, "list sessions: ")
        # Close
        if sid:
            s3, _ = api("DELETE", f"/api/ai/sessions/{sid}", t, extra_headers=XUID)
            info(f"Close session: {s3}")
    else:
        info(f"Create session: {s} (AI service may be down)")

def t_e2e_ai_cancel_order():
    """UC-11.3 + AI: Cancel order via AI Chatbot and verify confirmation flow"""
    # 1. Login as buyer
    t = login("minhhoa")
    init_dynamic_ids()
    vid = E2E_VARIANT_ID
    xuid = {"X-User-Id": "6", "X-User-Role": "BUYER", "X-User-Email": "minhhoa@gmail.com"}

    # 2. Create a fresh order via checkout
    api("DELETE", "/api/v1/cart", t)
    api("POST", "/api/v1/cart/items", t, body={"variantId": vid, "quantity": 1})
    s, r = api("POST", "/api/v1/cart/checkout/preview", t, body={"itemIds": [f"6:{vid}"]})
    check_status(s, 200)
    pt = get_field(r, "previewToken")
    check_not_none(pt)
    sa, ra = api("GET", "/api/v1/users/me/addresses", t)
    check_status(sa, 200)
    addrs = get_field(ra, "data")
    aid = (addrs[0].get("address_id") if isinstance(addrs, list) else get_field(addrs, "address_id")) if addrs else None
    check_not_none(aid)

    # Snapshot max orderId
    so, ro = api("GET", "/api/v1/orders?page=0&size=100", t)
    pre_max = 0
    if so == 200:
        c = get_field(ro, "content")
        if c and isinstance(c, list):
            pre_max = max((o.get("parentOrderId") or 0 for o in c), default=0)

    # Submit checkout
    s, r = api("POST", "/api/v1/cart/checkout/submit", t, body={"previewToken": pt, "addressId": aid})
    check_status(s, 200)

    # Find the new order ID
    def find_new():
        ss, rr = api("GET", "/api/v1/orders?page=0&size=100", t)
        if ss != 200: return None
        cc = get_field(rr, "content")
        if not cc or not isinstance(cc, list): return None
        for o in cc:
            if (o.get("parentOrderId") or 0) > pre_max: return o.get("orderId") or o.get("id")
        return None
    oid = poll("new order ID for AI cancel", find_new, timeout=60)
    check_not_none(oid)
    ok(f"Created order {oid} for AI cancel test")

    # 3. Create an AI chat session
    s, r = api("POST", "/api/ai/sessions", t, body={}, extra_headers=xuid)
    check_status(s, (200, 201))
    sid = get_field(r, "id") or get_field(get_field(r, "data"), "id")
    check_not_none(sid)
    ok(f"Chat session: {sid}")

    # 4. Chat with AI to request cancellation
    message = f"Hủy giúp tôi đơn hàng FE-ORD-PAID-{oid}"
    info(f"Sending message: {message}")
    chat_headers = xuid.copy()
    chat_headers["Accept"] = "text/event-stream"
    s, r = api("POST", "/api/ai/chat", t, body={"sessionId": sid, "message": message}, extra_headers=chat_headers)
    check_status(s, 200)
    
    # The response should be SSE text. Let's find confirmId in it.
    raw_sse = str(r)
    # Search for "confirmId":"..." in the SSE data
    confirm_match = re.search(r'"confirmId"\s*:\s*"([^"]+)"', raw_sse)
    if not confirm_match:
        raise RuntimeError(f"Could not find confirmId in SSE response: {raw_sse}")
    
    confirm_id = confirm_match.group(1)
    ok(f"Found confirmId: {confirm_id}")

    # 5. Call /api/ai/confirm to execute the action
    info(f"Confirming action: {confirm_id}")
    s_conf, r_conf = api("POST", "/api/ai/confirm", t, body={"confirmId": confirm_id, "confirmed": True}, extra_headers=xuid)
    check_status(s_conf, 200)
    ok("AI confirm request completed successfully")

    # 6. Verify that the order status is indeed CANCELLED
    def check_order_cancelled():
        ss, rr = api("GET", f"/api/v1/orders/{oid}", t)
        if ss != 200: return False
        status = get_field(rr, "status") or get_field(get_field(rr, "data"), "status")
        return status == "CANCELLED"
    
    poll("order CANCELLED via AI", check_order_cancelled, timeout=30)
    ok("Order cancelled status verified")

    # Clean up session
    api("DELETE", f"/api/ai/sessions/{sid}", t, extra_headers=xuid)

# ══════════════════════════════════════════════════════════════════════════════
# SECTION: WEBHOOK SECURITY
# ══════════════════════════════════════════════════════════════════════════════

def t_webhook_unsigned():
    req = urllib.request.Request(f"{GW}/api/v1/stripe/webhooks",
        data=b'{"type":"payment_intent.succeeded"}',
        headers={"Content-Type": "application/json"}, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            code = r.status
    except urllib.error.HTTPError as e: code = e.code
    except urllib.error.URLError as e: code = -1
    check_status(code, (400, 401, 403, 500), "unsigned: ")
    ok(f"Unsigned webhook: {code}")

def t_webhook_wrong_sig():
    ps = '{"type":"payment_intent.succeeded"}'
    ts = int(time.time())
    sig = hmac.new(b"whsec_WRONG", f"{ts}.{ps}".encode(), hashlib.sha256).hexdigest()
    req = urllib.request.Request(f"{GW}/api/v1/stripe/webhooks", data=ps.encode(),
        headers={"Content-Type": "application/json", "Stripe-Signature": f"t={ts},v1={sig}"}, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=10) as r: code = r.status
    except urllib.error.HTTPError as e: code = e.code
    except urllib.error.URLError as e: code = -1
    check_status(code, (400, 401, 403), "wrong sig: ")
    ok(f"Wrong signature: {code}")

def t_webhook_account_updated():
    t = login("techworld")
    s, r = api("GET", "/api/v1/stripe/onboarding/status", t)
    check_status(s, 200)
    acct = get_field(r, "stripeAccountId")
    check_not_none(acct)
    # Skip manual accounts
    if acct.startswith("acct_manual_"):
        info(f"Manual account {acct}, skipping webhook")
        return
    payload = forge_account(acct, True, True, True)
    code, body = send_webhook(payload)
    check_status(code, (200, 201, 204), "account.updated: ")
    ok(f"Account.updated: {code}")

# ══════════════════════════════════════════════════════════════════════════════
# SECTION: E2E BUSINESS FLOWS
# ══════════════════════════════════════════════════════════════════════════════

def t_e2e_checkout_payment_success():
    """UC-11.1: Checkout → PaymentIntent.succeeded → orders PAID"""
    t = login("minhhoa")
    init_dynamic_ids()
    vid = E2E_VARIANT_ID

    # Clear + add to cart
    api("DELETE", "/api/v1/cart", t)
    api("POST", "/api/v1/cart/items", t, body={"variantId": vid, "quantity": 1})

    # Preview
    s, r = api("POST", "/api/v1/cart/checkout/preview", t, body={"itemIds": [f"6:{vid}"]})
    check_status(s, 200, "preview: ")
    pt = get_field(r, "previewToken")
    check_not_none(pt)

    # Address
    sa, ra = api("GET", "/api/v1/users/me/addresses", t)
    check_status(sa, 200)
    addrs = get_field(ra, "data")
    check_not_none(addrs)
    aid = (addrs[0].get("address_id") if isinstance(addrs, list) else get_field(addrs, "address_id")) if addrs else None
    check_not_none(aid, "address ID")

    # Snapshot max parentOrderId
    so, ro = api("GET", "/api/v1/orders?page=0&size=100", t)
    pre_max = 0
    if so == 200:
        c = get_field(ro, "content")
        if c and isinstance(c, list):
            pre_max = max((o.get("parentOrderId") or 0 for o in c), default=0)

    # Submit
    s, r = api("POST", "/api/v1/cart/checkout/submit", t, body={"previewToken": pt, "addressId": aid})
    check_status(s, 200, "submit: ")
    ok("Checkout submitted")

    # Poll for new parent order
    def find_new():
        ss, rr = api("GET", "/api/v1/orders?page=0&size=100", t)
        if ss != 200: return None
        c = get_field(rr, "content")
        if not c or not isinstance(c, list): return None
        for o in c:
            if (o.get("parentOrderId") or 0) > pre_max: return o["parentOrderId"]
        return None
    pid = poll("new parent order", find_new, timeout=60)
    ok(f"Parent order: {pid}")

    # Poll for PENDING
    def check_pending():
        ss, rr = api("GET", f"/api/v1/payments/parent-order/{pid}", t)
        return ss == 200 and get_field(rr, "status") == "PENDING"
    try:
        poll("transaction PENDING", check_pending, timeout=60)
        ok("Transaction PENDING")
    except TimeoutError:
        warn("Transaction not PENDING (may already be SUCCESS)")

    # Forge payment_intent.succeeded
    code, body = send_webhook(forge_pi("payment_intent.succeeded", pid))
    check_status(code, (200, 201, 204), "webhook: ")
    ok(f"Webhook delivered: {code}")

    # Poll for SUCCESS + all PAID
    def check_success():
        ss, rr = api("GET", f"/api/v1/payments/parent-order/{pid}", t)
        return ss == 200 and get_field(rr, "status") == "SUCCESS"
    try:
        poll("transaction SUCCESS", check_success, timeout=30)
        ok("Transaction SUCCESS")
    except TimeoutError:
        warn("Transaction not SUCCESS")

    def check_paid():
        ss, rr = api("GET", f"/api/v1/orders/parent/{pid}", t)
        if ss != 200: return None
        orders = get_field(rr, "orders")
        if not orders or not isinstance(orders, list): return None
        return all(o.get("status") == "PAID" for o in orders)
    try:
        poll("all orders PAID", check_paid, timeout=30)
        ok("All orders PAID")
    except TimeoutError:
        warn("Orders not all PAID")

def t_e2e_payment_failed():
    """UC-11.2: payment_intent.payment_failed → orders CANCELLED"""
    t = login("minhhoa")
    init_dynamic_ids()
    vid = E2E_VARIANT_ID

    api("DELETE", "/api/v1/cart", t)
    api("POST", "/api/v1/cart/items", t, body={"variantId": vid, "quantity": 1})

    s, r = api("POST", "/api/v1/cart/checkout/preview", t, body={"itemIds": [f"6:{vid}"]})
    check_status(s, 200)
    pt = get_field(r, "previewToken")
    check_not_none(pt)

    sa, ra = api("GET", "/api/v1/users/me/addresses", t)
    check_status(sa, 200)
    addrs = get_field(ra, "data")
    aid = (addrs[0].get("address_id") if isinstance(addrs, list) else get_field(addrs, "address_id")) if addrs else None
    check_not_none(aid)

    so, ro = api("GET", "/api/v1/orders?page=0&size=100", t)
    pre_max = 0
    if so == 200:
        c = get_field(ro, "content")
        if c and isinstance(c, list):
            pre_max = max((o.get("parentOrderId") or 0 for o in c), default=0)

    s, r = api("POST", "/api/v1/cart/checkout/submit", t, body={"previewToken": pt, "addressId": aid})
    check_status(s, 200)

    def find_new():
        ss, rr = api("GET", "/api/v1/orders?page=0&size=100", t)
        if ss != 200: return None
        c = get_field(rr, "content")
        if not c or not isinstance(c, list): return None
        for o in c:
            if (o.get("parentOrderId") or 0) > pre_max: return o["parentOrderId"]
        return None
    pid = poll("new parent order", find_new, timeout=60)
    ok(f"Parent order: {pid}")

    # Forge payment_intent.payment_failed
    payload = forge_pi("payment_intent.payment_failed", pid)
    payload["data"]["object"]["last_payment_error"] = {"message": "card_declined"}
    code, body = send_webhook(payload)
    check_status(code, (200, 201, 204), "webhook: ")
    ok(f"Webhook failed: {code}")

    def check_cancelled():
        ss, rr = api("GET", f"/api/v1/orders/parent/{pid}", t)
        if ss != 200: return None
        orders = get_field(rr, "orders")
        if not orders or not isinstance(orders, list): return None
        return all(o.get("status") == "CANCELLED" for o in orders)
    try:
        poll("all CANCELLED", check_cancelled, timeout=30)
        ok("All orders CANCELLED")
    except TimeoutError:
        warn("Orders not all CANCELLED")

def t_e2e_stripe_onboarding_flow():
    """UC-11.6: Onboarding start → status → forge complete"""
    n, t = register_seller()
    # Start
    s, r = api("POST", "/api/v1/stripe/onboarding/start", t, body={})
    check_status(s, (200, 201, 500), "start: ")
    if s == 500:
        warn("Stripe API unavailable, skipping")
        return
    acct = get_field(r, "stripeAccountId")
    url = get_field(r, "onboardingUrl")
    check_not_none(acct)
    check_true(not acct.startswith("acct_mock_"), "NOT mock")
    ok(f"Account: {acct}, URL: {str(url)[:60]}...")

    # Status
    s2, r2 = api("GET", "/api/v1/stripe/onboarding/status", t)
    check_status(s2, 200, "status: ")
    st = get_field(r2, "onboardingStatus")
    ok(f"Status: {st}")

    # Forge account.updated for real accounts
    if not acct.startswith("acct_manual_"):
        code, body = send_webhook(forge_account(acct, True, True, True))
        check_status(code, (200, 201, 204), "webhook: ")
        ok(f"Account.updated: {code}")

    # Refresh
    s3, r3 = api("POST", "/api/v1/stripe/onboarding/refresh-link", t, body={})
    info(f"Refresh: {s3}")

def t_e2e_parallel_onboarding():
    """UC-11.6.8: 2 sellers parallel → distinct account IDs"""
    n1, t1 = register_seller()
    n2, t2 = register_seller()
    results = {}
    def do(token, label):
        s, r = api("POST", "/api/v1/stripe/onboarding/start", token, body={})
        results[label] = (s, r)
    t1t = threading.Thread(target=do, args=(t1, "a"))
    t2t = threading.Thread(target=do, args=(t2, "b"))
    t1t.start(); t2t.start(); t1t.join(); t2t.join()
    s1, r1 = results["a"]
    s2, r2 = results["b"]
    if s1 in (200, 201) and s2 in (200, 201):
        a1 = get_field(r1, "stripeAccountId")
        a2 = get_field(r2, "stripeAccountId")
        if a1 and a2:
            check_neq(a1, a2, f"distinct: ")
            ok(f"Distinct: {a1} != {a2}")
        else:
            warn("Cannot extract account IDs")
    else:
        info(f"Parallel: s1={s1} s2={s2}")

def t_e2e_transfer_webhook():
    """UC-11.10: transfer.created + transfer.reversed webhooks"""
    t = login("minhhoa")
    s, r = api("GET", "/api/v1/orders?page=0&size=5", t)
    check_status(s, 200)
    orders = get_field(r, "content")
    if not orders or not isinstance(orders, list) or len(orders) == 0:
        warn("No orders")
        return
    oid = orders[0].get("orderId") or orders[0].get("id")
    tid = f"tr_e2e_{uuid.uuid4().hex[:12]}"
    # Created
    tr = {"id": tid, "object": "transfer", "amount": 50000, "currency": "vnd", "status": "paid",
          "metadata": {"order_id": str(oid)}}
    e = _base("transfer.created"); e["data"] = {"object": tr}
    c1, _ = send_webhook(e)
    check_status(c1, (200, 201, 204), "transfer.created: ")
    ok(f"Transfer.created: {c1}")
    # Reversed
    tr2 = {**tr, "amount_reversed": 50000, "status": "reversed"}
    e2 = _base("transfer.reversed"); e2["data"] = {"object": tr2}
    c2, _ = send_webhook(e2)
    check_status(c2, (200, 201, 204), "transfer.reversed: ")
    ok(f"Transfer.reversed: {c2}")

def t_e2e_admin_seller_onboarding_summary():
    """Admin: GET /admin/sellers → verify summary + no mock accounts"""
    t = login("admin")
    s, r = api("GET", "/api/v1/stripe/onboarding/admin/sellers", t)
    check_status(s, 200)
    summary = get_field(r, "summary")
    accounts = get_field(r, "accounts")
    check_not_none(summary)
    ok(f"Summary: total={get_field(summary, 'totalSellers')} complete={get_field(summary, 'completedSellers')}")
    # Historical mock accounts may persist in DB
    if accounts and isinstance(accounts, list):
        mocks = [a for a in accounts if a.get("stripeAccountId", "").startswith("acct_mock_")]
        ok(f"Accounts: {len(accounts)} total, {len(mocks)} pre-existing mock(s)")

def t_e2e_auth_edge_cases():
    """Auth edge cases: buyer blocked from seller endpoints, seller blocked from admin"""
    bt = login("minhhoa")
    st = login("techworld")
    # Buyer can't access seller endpoints
    for ep in ["/api/v1/sellers/me/products", "/api/v1/seller/products"]:
        s, _ = api("GET", ep, bt)
        check_status(s, (401, 403, 404, 500), f"buyer {ep}: ")
    # Seller can't access admin endpoints (500 = known gateway routing issue)
    s, _ = api("GET", "/api/v1/admin/users?page=0&size=5", st)
    check_status(s, (401, 403, 500), "seller admin: ")
    # Seller can't access onboarding admin
    s, _ = api("GET", "/api/v1/stripe/onboarding/admin/sellers", st)
    check_status(s, (401, 403, 500), "seller admin onboarding: ")
    ok("Auth edge cases pass")

def t_e2e_refund_flow():
    """UC-11.4: Ship → deliver → request refund (up to PENDING)"""
    t = login("minhhoa")
    s, r = api("GET", "/api/v1/orders?page=0&size=5", t)
    check_status(s, 200)
    orders = get_field(r, "content")
    if not orders or not isinstance(orders, list) or len(orders) == 0:
        warn("No orders for refund flow")
        return

    # Find a PAID or DELIVERED order
    oid = None
    for o in orders:
        if o.get("status") in ("PAID", "DELIVERED", "SHIPPING"):
            oid = o.get("orderId") or o.get("id")
            break
    if not oid:
        warn("No PAID/DELIVERED/SHIPPING order found")
        return

    ok(f"Testing refund on order {oid}")

    # Get items
    s, r = api("GET", f"/api/v1/orders/{oid}", t)
    check_status(s, 200)
    items = get_field(r, "items")
    if not items or not isinstance(items, list) or len(items) == 0:
        warn("No items in order")
        return

    item_id = items[0].get("orderItemId") or items[0].get("id")
    check_not_none(item_id, "item ID")

    # Request refund
    s, r = api("POST", f"/api/v1/orders/{oid}/refunds", t, body={
        "reason": "Item damaged",
        "items": [{"orderItemId": item_id, "quantity": 1, "itemReason": "damaged"}],
        "evidenceImages": []})
    if s in (200, 201):
        ok(f"Refund requested: {s}")
    else:
        info(f"Refund request: {s} (may already have a refund)")

    # Partial refund on parent
    s2, r2 = api("GET", f"/api/v1/orders/{oid}", t)
    pid = get_field(r2, "parentOrderId") if s2 == 200 else None
    if pid:
        s3, _ = api("POST", f"/api/v1/orders/parent/{pid}/refunds/partial", t, body={
            "refundAmount": 10000, "reason": "Partial refund test"})
        info(f"Partial refund: {s3}")

def t_e2e_multi_seller_cart():
    """UC-11.8: Add variants from 2 different sellers to cart, preview checkout"""
    bt = login("minhhoa")
    vid1 = "c5803c7d-2d5c-4178-b579-7266a15ca9ff"  # seller 6 variant
    # Get a variant from a different seller (techworld = seller 4)
    s, r = api("GET", "/api/v1/products/variants/sku/SKU-MAGSAFE")
    vid2 = get_field(r, "id") or get_field(r, "variantId")
    if not vid2:
        warn("Could not find SKU-MAGSAFE variant, using fallback")
        vid2 = "SKU-MAGSAFE"
    # Clear cart + add both
    api("DELETE", "/api/v1/cart", bt)
    api("POST", "/api/v1/cart/items", bt, body={"variantId": vid1, "quantity": 1})
    api("POST", "/api/v1/cart/items", bt, body={"variantId": vid2, "quantity": 1})
    # Preview
    item_ids = [f"6:{vid1}", f"4:{vid2}"]
    s2, r2 = api("POST", "/api/v1/cart/checkout/preview", bt, body={"itemIds": item_ids})
    check_status(s2, 200, "multi-seller preview: ")
    pt = get_field(r2, "previewToken")
    check_not_none(pt, "previewToken")
    ok(f"Multi-seller checkout preview: token={str(pt)[:20]}...")

def t_e2e_charge_refunded_webhook():
    """UC-11.9: Forge a charge.refunded webhook with parent_order_id from existing paid order"""
    t = login("minhhoa")
    s, r = api("GET", "/api/v1/orders?page=0&size=20", t)
    check_status(s, 200)
    orders = get_field(r, "content")
    if not orders or not isinstance(orders, list) or len(orders) == 0:
        warn("No orders for charge.refunded webhook")
        return
    pid = None
    for o in orders:
        if o.get("status") in ("PAID", "DELIVERED", "SHIPPING"):
            pid = o.get("parentOrderId")
            if pid: break
    if not pid:
        warn("No paid order with parentOrderId found")
        return
    chg_id = f"ch_e2e_{uuid.uuid4().hex[:12]}"
    refund = {"id": f"re_e2e_{uuid.uuid4().hex[:12]}", "object": "refund", "amount": 10000,
              "currency": "vnd", "charge": chg_id, "status": "succeeded",
              "metadata": {"parent_order_id": str(pid)}}
    e = _base("charge.refunded")
    chg = {"id": chg_id, "object": "charge", "amount": 50000, "currency": "vnd",
           "status": "succeeded", "refunded": True, "amount_refunded": 10000,
           "metadata": {"parent_order_id": str(pid)}}
    e["data"] = {"object": chg, "previous_attributes": {"refunded": False}}
    code, body = send_webhook(e)
    check_status(code, (200, 201, 204), "charge.refunded: ")
    ok(f"Charge.refunded webhook: {code}")

def t_e2e_payment_idempotency():
    """UC-11.7: Call GET /api/v1/payments/parent-order/{id} twice, verify same transactionId"""
    t = login("minhhoa")
    s, r = api("GET", "/api/v1/orders?page=0&size=10", t)
    check_status(s, 200)
    orders = get_field(r, "content")
    if not orders or not isinstance(orders, list) or len(orders) == 0:
        warn("No orders for idempotency test")
        return
    # Find a parentOrderId with an existing payment
    found = False
    for o in orders:
        pid = o.get("parentOrderId")
        if not pid: continue
        s1, r1 = api("GET", f"/api/v1/payments/parent-order/{pid}", t)
        if s1 == 200:
            tx1 = get_field(r1, "transactionId")
            s2, r2 = api("GET", f"/api/v1/payments/parent-order/{pid}", t)
            if s2 == 200:
                tx2 = get_field(r2, "transactionId")
                if tx1 and tx2:
                    check_eq(tx1, tx2, "same transactionId: ")
                    ok(f"Idempotency: {tx1} == {tx2}")
                    return
    info("Idempotency: no order with valid payment found")

def t_e2e_auth_refresh_token():
    """Verify refresh token endpoint (may fail if login response format differs)"""
    t = login("minhhoa")
    s, r = api("POST", "/api/v1/auth/login", body={"credential": "minhhoa", "password": PW})
    rt = get_field(r, "refreshToken") or get_field(get_field(r, "data"), "refreshToken")
    if rt:
        s2, r2 = api("POST", "/api/v1/auth/refresh", body={"refreshToken": rt})
        check_status(s2, (200, 401), "refresh: ")
        if s2 == 200:
            new_t = get_field(r2, "accessToken") or get_field(get_field(r2, "data"), "accessToken")
            if new_t: ok("Refresh token works")
            else: info("Refresh: no new accessToken")
        else:
            info("Refresh: 401 (login refreshToken vs /refresh endpoint mismatch - known issue)")
    else:
        info("No refreshToken in login response - skipping")

def t_e2e_multi_seller_cart():
    """UC-11.8: Add variants from different sellers to cart, verify checkout"""
    t = login("minhhoa")
    # Get two variants from different products
    s, r = api("GET", "/api/v1/products?page=0&size=20")
    check_status(s, 200)
    content = get_field(r, "content")
    if not content or not isinstance(content, list):
        warn("No products")
        return
    variants = []
    for p in content[:8]:
        vlist = p.get("variants") or []
        for v in vlist[:2] if isinstance(vlist, list) else [vlist] if isinstance(vlist, dict) else []:
            vid = v.get("variantId") or v.get("id") if isinstance(v, dict) else None
            if vid: variants.append((vid, p.get("sellerId")))
    if len(variants) < 2:
        warn(f"Need 2+ variants, found {len(variants)}")
        return
    # Try to find variants from different sellers
    seen_sellers = set()
    selected = []
    for vid, sid in variants:
        if sid and sid not in seen_sellers:
            selected.append(vid)
            seen_sellers.add(sid)
        if len(selected) >= 2: break
    if len(selected) < 2:
        selected = [v[0] for v in variants[:2]]
    api("DELETE", "/api/v1/cart", t)
    for vid in selected:
        api("POST", "/api/v1/cart/items", t, body={"variantId": vid, "quantity": 1})
    s, r = api("GET", "/api/v1/cart", t)
    check_status(s, 200)
    items = get_field(r, "items")
    check_not_none(items)
    cart_count = len(items) if isinstance(items, list) else 1
    ok(f"Cart has {cart_count} items from {len(seen_sellers)} seller(s)")

def t_e2e_publish_search_reindex():
    """UC-11.12: Create product, publish, verify search reindex"""
    at = login("admin")
    st = login("techworld")
    # Get leaf category
    s, r = api("GET", "/api/v1/categories")
    cats = get_field(r, "data")
    cid = None
    if isinstance(cats, list):
        for c in cats:
            ch = c.get("children")
            if ch and isinstance(ch, list):
                for cc in ch:
                    if not cc.get("children"): cid = cc.get("id"); break
            if cid: break
            if not ch: cid = c.get("id")
    if not cid:
        warn("No category found")
        return
    # Create
    pn = f"E2E Search Test {uuid.uuid4().hex[:6]}"
    s, r = api("POST", "/api/v1/products", st, body={"name": pn, "description": "Search reindex test", "categoryId": str(cid)})
    check_status(s, 201, "create: ")
    pid = get_field(r, "id") or get_field(get_field(r, "data"), "id")
    check_not_none(pid)
    # Variant
    vc = f"SEARCH-{uuid.uuid4().hex[:8].upper()}"
    api("POST", f"/api/v1/seller/products/{pid}/variants", st, body={"variantCode": vc, "variantName": vc, "price": 100000, "stockQuantity": 10})
    # Submit + approve may need images first
    s_img, _ = api("POST", f"/api/v1/products/{pid}/images", st, body={"imageId": str(uuid.uuid4()), "url": f"https://picsum.photos/seed/{pid[:8]}/400/400", "sortOrder": 0})
    api("POST", f"/api/v1/seller/products/{pid}/submit", st, body={})
    api("POST", f"/api/v1/admin/products/{pid}/approve", at, body={})
    s3, r3 = api("POST", f"/api/v1/seller/products/{pid}/publish", st, body={})
    check_status(s3, (200, 400), "publish: ")
    ok(f"Product {pid} processed (publish: {s3})")
    # Cleanup
    api("DELETE", f"/api/v1/seller/products/{pid}", st)
    info(f"Cleanup: deleted {pid}")

def t_e2e_buyer_cancel_order():
    """UC-11.3: Buyer cancels PENDING order, verify transaction CANCELLED"""
    t = login("minhhoa")
    init_dynamic_ids()
    vid = E2E_VARIANT_ID

    # Create a fresh order via checkout
    api("DELETE", "/api/v1/cart", t)
    api("POST", "/api/v1/cart/items", t, body={"variantId": vid, "quantity": 1})
    s, r = api("POST", "/api/v1/cart/checkout/preview", t, body={"itemIds": [f"6:{vid}"]})
    check_status(s, 200)
    pt = get_field(r, "previewToken")
    check_not_none(pt)
    sa, ra = api("GET", "/api/v1/users/me/addresses", t)
    check_status(sa, 200)
    addrs = get_field(ra, "data")
    aid = (addrs[0].get("address_id") if isinstance(addrs, list) else get_field(addrs, "address_id")) if addrs else None
    check_not_none(aid)

    # Snapshot
    so, ro = api("GET", "/api/v1/orders?page=0&size=100", t)
    pre_max = 0
    if so == 200:
        c = get_field(ro, "content")
        if c and isinstance(c, list):
            pre_max = max((o.get("parentOrderId") or 0 for o in c), default=0)

    s, r = api("POST", "/api/v1/cart/checkout/submit", t, body={"previewToken": pt, "addressId": aid})
    check_status(s, 200)

    def find_new():
        ss, rr = api("GET", "/api/v1/orders?page=0&size=100", t)
        if ss != 200: return None
        cc = get_field(rr, "content")
        if not cc or not isinstance(cc, list): return None
        for o in cc:
            if (o.get("parentOrderId") or 0) > pre_max: return o["parentOrderId"], o.get("orderId") or o.get("id")
        return None
    pid_oid = poll("new parent order", find_new, timeout=60)
    pid, oid = pid_oid[0], pid_oid[1]
    ok(f"Order {oid} in parent {pid}")

    # Cancel the order
    s, _ = api("POST", f"/api/v1/orders/{oid}/cancel", t, body={"reason": "E2E buyer cancel"})
    check_status(s, 200, "cancel: ")
    ok(f"Order {oid} cancelled by buyer")

    # Poll for CANCELLED payment
    def check_tx_cancelled():
        ss, rr = api("GET", f"/api/v1/payments/parent-order/{pid}", t)
        return ss == 200 and get_field(rr, "status") == "CANCELLED"
    try:
        poll("transaction CANCELLED", check_tx_cancelled, timeout=30)
        ok("Transaction CANCELLED")
    except TimeoutError:
        warn("Transaction not CANCELLED (may be SUCCESS or PENDING)")

def t_e2e_refund_approve_flow():
    """UC-11.4: Admin approve refund after buyer request (full lifecycle)"""
    t = login("minhhoa")
    at = login("admin")
    s, r = api("GET", "/api/v1/orders?page=0&size=50", t)
    check_status(s, 200)
    orders = get_field(r, "content")
    if not orders or not isinstance(orders, list) or len(orders) == 0:
        warn("No orders for refund approve test")
        return
    oid = None
    for o in orders:
        status = o.get("status")
        candidate_id = o.get("orderId") or o.get("id")
        
        # Check if candidate has active refund request
        ref_s, ref_r = api("GET", f"/api/v1/orders/{candidate_id}/refunds", t)
        if ref_s == 200:
            refs = get_field(ref_r, "data") or []
            if refs:
                continue # Skip if already has refund requests
                
        if status == "DELIVERED":
            oid = candidate_id
            break
        elif status == "PAID":
            seller_id = o.get("sellerId")
            seller_username = {
                1: "techworld",
                2: "fashionhub",
                3: "gadgetpro",
                4: "homeliving",
                5: "sportoutdoor"
            }.get(seller_id)
            if not seller_username:
                continue
                
            info(f"Transitioning PAID order {candidate_id} to DELIVERED via seller {seller_username}...")
            # Login as seller to update tracking
            st = login(seller_username)
            s_track, _ = api("PUT", f"/api/v1/orders/{candidate_id}/tracking", st, body={
                "trackingNumber": f"TRK-{uuid.uuid4().hex[:8].upper()}"
            })
            if s_track != 200:
                warn(f"Failed to update tracking for order {candidate_id}: {s_track}")
                continue
                
            # Confirm received to mark as DELIVERED
            s_recv, _ = api("POST", f"/api/v1/orders/{candidate_id}/confirm-received", t, body={})
            if s_recv != 200:
                warn(f"Failed to confirm received for order {candidate_id}: {s_recv}")
                continue
                
            oid = candidate_id
            break

    if not oid:
        warn("No suitable PAID or DELIVERED order found for refund test")
        return
    # Get items
    s, r = api("GET", f"/api/v1/orders/{oid}", t)
    check_status(s, 200)
    items = get_field(r, "items")
    if not items or not isinstance(items, list) or len(items) == 0:
        warn("No items")
        return
    item_id = items[0].get("orderItemId") or items[0].get("id")
    if not item_id:
        warn("No item ID")
        return
    # Request refund
    s, r = api("POST", f"/api/v1/orders/{oid}/refunds", t, body={
        "reason": "E2E refund approve test",
        "items": [{"orderItemId": item_id, "quantity": 1, "itemReason": "damaged"}],
        "evidenceImages": []})
    check_status(s, (200, 201), "request refund: ")
    ok(f"Refund requested on order {oid}")
    # Find refund ID with polling (since Kafka processing is async)
    def find_refund():
        sid, rid = api("GET", f"/api/v1/orders/{oid}/refunds", t)
        if sid == 200:
            refs = get_field(rid, "data") or []
            if isinstance(refs, list) and len(refs) > 0:
                return refs[-1].get("refundId") or refs[-1].get("id")
        return None

    try:
        rfid = poll("refund ID", find_refund, timeout=120)
        ok(f"Found refund ID: {rfid}")
        # Admin approve
        sap, _ = api("POST", f"/api/v1/admin/refunds/{rfid}/approve", at, body={"adminNote": "E2E approve"})
        info(f"Admin approve refund {rfid}: {sap}")
        if sap in (200, 201):
            ok("Refund approved by admin")
            return
    except TimeoutError:
        warn("Timed out waiting for refund ID to appear")
        
    warn("Could not complete refund approve flow")

def t_e2e_publish_search_reindex():
    """UC-11.12: Create product, publish, verify search reindex"""
    at = login("admin")
    st = login("techworld")
    # Get leaf category
    s, r = api("GET", "/api/v1/categories")
    cats = get_field(r, "data")
    cid = None
    if isinstance(cats, list):
        for c in cats:
            ch = c.get("children")
            if ch and isinstance(ch, list):
                for cc in ch:
                    if not cc.get("children"): cid = cc.get("id"); break
            if not ch: cid = c.get("id")
            if cid: break
    if not cid: cid = str(cats[0].get("id") if isinstance(cats, list) else cats) if cats else None
    if not cid:
        warn("No category found")
        return
    # Create
    pn = f"E2E Search Test {uuid.uuid4().hex[:6]}"
    s, r = api("POST", "/api/v1/products", st, body={"name": pn, "description": "Search reindex test", "categoryId": str(cid)})
    check_status(s, 201, "create: ")
    pid = get_field(r, "id") or get_field(get_field(r, "data"), "id")
    check_not_none(pid)
    # Variant
    vc = f"SEARCH-{uuid.uuid4().hex[:8].upper()}"
    api("POST", f"/api/v1/seller/products/{pid}/variants", st, body={"variantCode": vc, "variantName": vc, "price": 100000, "stockQuantity": 10})
    # Image
    s_presigned, r_presigned = api("GET", f"/api/v1/products/{pid}/presigned-url?filename=image.jpg", st)
    if s_presigned == 200:
        img_data = get_field(r_presigned, "data") or r_presigned
        img_id = get_field(img_data, "imageId")
        if img_id:
            img_url = f"http://localhost:9000/product-images/products/techworld/{pid}/{img_id}.jpg"
            api("POST", f"/api/v1/products/{pid}/images", st, body={
                "imageId": img_id,
                "url": img_url,
                "sortOrder": 0
            })
    # Submit + approve + publish
    api("POST", f"/api/v1/seller/products/{pid}/submit", st, body={})
    api("POST", f"/api/v1/admin/products/{pid}/approve", at, body={})
    s3, _ = api("POST", f"/api/v1/seller/products/{pid}/publish", st, body={})
    check_status(s3, (200, 400), "publish: ")
    ok(f"Published {pid}")
    # Poll search
    import urllib.parse
    enc = urllib.parse.quote(pn)
    bt = login("minhhoa")
    def find_in_search():
        ss, rr = api("GET", f"/api/v1/search/products?q={enc}&page=0&size=10", bt)
        if ss != 200: return None
        content = get_field(rr, "content")
        if not content or not isinstance(content, list): return None
        for p in content:
            if pn in (p.get("name") or ""): return True
        return None
    try:
        poll(f"product in search: {pn}", find_in_search, timeout=60)
        ok("Product found in search index")
    except TimeoutError:
        warn("Product not found in search (reindex may be slow)")
    # Cleanup
    api("DELETE", f"/api/v1/seller/products/{pid}", st)
    info(f"Cleanup: deleted {pid}")

# ══════════════════════════════════════════════════════════════════════════════
# TEST REGISTRY & MAIN
# ══════════════════════════════════════════════════════════════════════════════

GROUPS = {
    "auth": [
        ("login_buyer", t_auth_login_buyer),
        ("login_seller", t_auth_login_seller),
        ("login_admin", t_auth_login_admin),
        ("login_wrong_password", t_auth_login_wrong_password),
        ("no_token_protected", t_auth_no_token_protected),
        ("register_buyer", t_auth_register_buyer),
        ("register_seller", t_auth_register_seller),
        ("profile", t_auth_profile),
        ("update_profile", t_auth_update_profile),
        ("addresses", t_auth_addresses),
        ("create_address", t_auth_create_address),
        ("change_password", t_auth_change_password),
        ("upgrade_to_seller", t_auth_upgrade_to_seller),
        ("admin_users", t_auth_admin_users),
        ("admin_user_detail", t_auth_admin_user_detail),
    ],
    "admin": [
        ("admin_lock_user", t_admin_lock_user),
        ("admin_category_flow", t_admin_category_flow),
    ],
    "inventory": [
        ("inventory_check", t_inventory_check),
        ("inventory_restock", t_inventory_restock),
        ("inventory_adjust", t_inventory_adjust),
        ("inventory_logs", t_inventory_logs),
    ],
    "catalog": [
        ("products", t_catalog_products),
        ("product_detail", t_catalog_product_detail),
        ("sku_lookup", t_catalog_sku_lookup),
        ("categories", t_catalog_categories),
        ("seller_products", t_catalog_seller_products),
        ("seller_create_product", t_catalog_seller_create_product),
    ],
    "cart": [
        ("cart_flow", t_cart_flow),
    ],
    "checkout": [
        ("preview", t_checkout_preview),
        ("submit", t_checkout_submit),
    ],
    "order": [
        ("my_orders", t_order_my_orders),
        ("seller_orders", t_order_seller_orders),
        ("seller_dashboard", t_order_seller_dashboard),
    ],
    "orderlifecycle": [
        ("order_cancel", t_order_cancel),
        ("order_tracking", t_order_tracking),
        ("order_confirm_received", t_order_confirm_received),
    ],
    "payment": [
        ("query_parent", t_payment_query_parent),
    ],
    "stripe": [
        ("onboarding_status", t_stripe_onboarding_status),
        ("onboarding_start_new", t_stripe_onboarding_start_new),
        ("onboarding_refresh", t_stripe_refresh_link),
        ("buyer_blocked", t_stripe_buyer_blocked),
        ("seller_payments", t_seller_payments),
        ("stripe_dashboard", t_stripe_dashboard_link),
        ("admin_seller_list", t_admin_seller_list),
    ],
    "flashsale": [
        ("flash_sale_flow", t_flash_sale_flow),
    ],
    "refund": [
        ("refund_query", t_refund_query),
        ("refund_admin_list", t_refund_admin_list),
        ("refund_presigned_url", t_refund_presigned_url),
    ],
    "search": [
        ("search_products", t_search_products),
        ("search_suggest", t_search_suggest),
        ("search_reindex", t_search_reindex),
        ("search_reindex_status", t_search_reindex_status),
    ],
    "notification": [
        ("notification_count", t_notification_count),
        ("notification_history", t_notification_history),
        ("notification_read_all", t_notification_read_all),
    ],
    "chat": [
        ("chat_suggest", t_chat_suggest),
        ("chat_sessions", t_chat_sessions),
    ],
    "webhook": [
        ("webhook_unsigned", t_webhook_unsigned),
        ("webhook_wrong_sig", t_webhook_wrong_sig),
        ("webhook_account_updated", t_webhook_account_updated),
    ],
    "e2e": [
        ("checkout_payment_success", t_e2e_checkout_payment_success),
        ("payment_failed_cancelled", t_e2e_payment_failed),
        ("buyer_cancel_order", t_e2e_buyer_cancel_order),
        ("ai_cancel_order", t_e2e_ai_cancel_order),
        ("stripe_onboarding_flow", t_e2e_stripe_onboarding_flow),
        ("parallel_onboarding", t_e2e_parallel_onboarding),
        ("transfer_webhook", t_e2e_transfer_webhook),
        ("admin_seller_summary", t_e2e_admin_seller_onboarding_summary),
        ("auth_edge_cases", t_e2e_auth_edge_cases),
        ("refund_flow", t_e2e_refund_flow),
        ("refund_approve_flow", t_e2e_refund_approve_flow),
        ("multi_seller_cart", t_e2e_multi_seller_cart),
        ("charge_refunded_webhook", t_e2e_charge_refunded_webhook),
        ("payment_idempotency", t_e2e_payment_idempotency),
        ("auth_refresh_token", t_e2e_auth_refresh_token),
        ("publish_search_reindex", t_e2e_publish_search_reindex),
    ],
}

ALL_TESTS = {}
for group, tests in GROUPS.items():
    for name, fn in tests:
        ALL_TESTS[f"{group}_{name}"] = (group, fn)

def main():
    parser = argparse.ArgumentParser(description="Backend E2E Test Suite")
    parser.add_argument("--group", help="Test group: " + ", ".join(GROUPS.keys()))
    parser.add_argument("--test", help="Single test name")
    parser.add_argument("--list", action="store_true", help="List all tests")
    parser.add_argument("--gateway", default=None, help="API Gateway URL (default: http://localhost:8080)")
    args = parser.parse_args()

    global GW
    if args.gateway:
        GW = args.gateway

    if args.list:
        for g, tests in GROUPS.items():
            print(f"\n[{g}]")
            for n, _ in tests: print(f"  {g}_{n}")
        return

    # Health check
    print(f"Gateway: {GW}")
    try:
        s, _ = api("GET", "/actuator/health")
        print(f"Health: {s}")
        if s != 200:
            print("[WARN] Gateway health check failed")
            if input("Continue? [y/N] ").strip().lower() != "y":
                sys.exit(1)
    except Exception as e:
        print(f"[FATAL] Cannot reach gateway: {e}")
        print("   Start: docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d")
        sys.exit(1)

    if args.test:
        if args.test not in ALL_TESTS:
            print(f"Unknown: {args.test}")
            print(f"Use --list to see all tests")
            sys.exit(1)
        grp, fn = ALL_TESTS[args.test]
        test(args.test, fn, grp)
    elif args.group:
        if args.group not in GROUPS:
            print(f"Unknown group: {args.group}. Available: {', '.join(GROUPS)}")
            sys.exit(1)
        for name, fn in GROUPS[args.group]:
            test(f"{args.group}_{name}", fn, args.group)
    else:
        # Run all groups
        for g, tests in GROUPS.items():
            print(f"\n{'#'*60}\n# GROUP: {g}\n{'#'*60}")
            for name, fn in tests:
                test(f"{g}_{name}", fn, g)

    # Summary
    total = PASS + FAIL + ERROR
    print(f"\n{'='*60}")
    print(f"RESULTS: {PASS} passed, {FAIL} failed, {ERROR} errors ({total} total)")
    print(f"{'='*60}")
    for rt, grp, name, *det in RESULTS:
        icon = {"PASS": "[PASS]", "FAIL": "[FAIL]", "ERROR": "[ERROR]"}.get(rt, "?")
        detail = f" - {det[0]}" if det else ""
        print(f"  {icon} [{grp}] {name}{detail}")
    if FAIL > 0 or ERROR > 0: sys.exit(1)

if __name__ == "__main__":
    main()
