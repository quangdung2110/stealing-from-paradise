#!/usr/bin/env python3
"""
Race Condition Demo — Full Checkout → Payment Flow

 🔗  http://localhost:3000/products/90000000-0000-4000-8001-000000000103
     AirPods Pro 2 (USB-C) Combo — 4.990.000 ₫

 FLOW USER THẬT:
   Cart → Preview (thấy stock) → Submit (reserve stock) → Form thẻ VISA → Thanh toán

 RACE POINT: Submit đồng loạt.
   - Winners: submit 200 → poll parent order → forge payment webhook → PAID ✅
   - Losers:  submit 409 → INSUFFICIENT_STOCK ❌ (không bao giờ thấy form thẻ)

 MÔ PHỎNG FORM THẺ:
   Bước "form thẻ" là Stripe PaymentElement (frontend). Trong demo này,
   sau khi find được parent_order_id, ta forge webhook payment_intent.succeeded
   để mô phỏng user nhập thẻ test 4242... → thanh toán thành công.

USAGE:
   GATEWAY=http://localhost:8080 python demo_race_condition.py
"""

import sys, os, json, time, uuid, hmac, hashlib, threading, urllib.request, urllib.error
from concurrent.futures import ThreadPoolExecutor, as_completed

GW  = os.environ.get("GATEWAY", "http://localhost:8080")
PW  = os.environ.get("E2E_DEV_PASSWORD", "dev123")
WHS = os.environ.get("STRIPE_WEBHOOK_SECRET", os.environ.get("WEBHOOK_SECRET", ""))
# Try to read from project root .env if not set in environment
if not WHS:
    try:
        script_dir = os.path.dirname(os.path.abspath(__file__))
        # Walk up from scripts/ → e2e-tests/ → backend/ → project root
        for levels in range(1, 5):
            env_path = os.path.normpath(os.path.join(script_dir, *([".."] * levels), ".env"))
            if os.path.isfile(env_path):
                with open(env_path) as f:
                    for line in f:
                        line = line.strip()
                        if line.startswith("STRIPE_WEBHOOK_SECRET=") or line.startswith("WEBHOOK_SECRET="):
                            WHS = line.split("=", 1)[1].strip().strip('"').strip("'")
                            break
                if WHS:
                    break
    except Exception:
        pass

if not WHS:
    print("⚠️  STRIPE_WEBHOOK_SECRET not found in env or .env file", file=sys.stderr)

PRODUCT_NAME = "AirPods Pro 2 (USB-C) Combo"
PRODUCT_ID   = "90000000-0000-4000-8001-000000000103"
VARIANT_ID   = "90000000-0000-4000-9001-000000000103"
SKU          = "FE-SKU-AIRPODS-COMBO"
PRICE        = "4.990.000 ₫"
FRONTEND_URL = f"http://localhost:3000/products/{PRODUCT_ID}"

# ═══ HTTP ═══════════════════════════════════════════════════════════════════

def api(method, path, token=None, body=None):
    url = f"{GW}{path}"
    data = None
    if body is not None:
        data = (body if isinstance(body, str) else json.dumps(body)).encode()
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if token: headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read().decode()
            try: return r.status, json.loads(raw) if raw else {}
            except: return r.status, raw
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try: return e.code, json.loads(raw) if raw else {}
        except: return e.code, raw
    except Exception as e: return -1, str(e)

def login(who):
    s, r = api("POST", "/api/v1/auth/login", body={"credential": who, "password": PW})
    if s != 200: raise RuntimeError(f"Login {who}: {s}")
    return r.get("accessToken") or r.get("data", {}).get("accessToken")

def get_field(data, *keys):
    if isinstance(data, list):
        for it in data:
            v = get_field(it, *keys)
            if v is not None: return v
        return None
    if isinstance(data, dict):
        for k in keys:
            if k in data and data[k] is not None: return data[k]
        for v in data.values():
            r = get_field(v, *keys)
            if r is not None: return r
    return None

# ═══ WEBHOOK ════════════════════════════════════════════════════════════════

def send_webhook(payload_dict):
    ps = json.dumps(payload_dict, separators=(",", ":"))
    ts = int(time.time())
    sig = hmac.new(WHS.encode(), f"{ts}.{ps}".encode(), hashlib.sha256).hexdigest()
    req = urllib.request.Request(f"{GW}/api/v1/stripe/webhooks", data=ps.encode(),
        headers={"Content-Type": "application/json",
                 "Stripe-Signature": f"t={ts},v1={sig}"}, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=15) as r: return r.status, r.read().decode()[:300]
    except urllib.error.HTTPError as e: return e.code, e.read().decode()[:300]

def forge_pi(parent_id):
    return {
        "id": f"evt_{uuid.uuid4().hex[:16]}",
        "object": "event",
        "api_version": "2024-06-20",
        "created": int(time.time()),
        "livemode": False,
        "pending_webhooks": 1,
        "type": "payment_intent.succeeded",
        "data": {"object": {
            "id": f"pi_{uuid.uuid4().hex[:12]}",
            "object": "payment_intent",
            "amount": 4990000,
            "currency": "vnd",
            "status": "succeeded",
            "metadata": {"parent_order_id": str(parent_id)},
            "latest_charge": f"ch_{uuid.uuid4().hex[:12]}"
        }}
    }

# ═══ POLL ══════════════════════════════════════════════════════════════════

def poll(desc, fn, timeout=120, interval=2):
    dl = time.time() + timeout
    while time.time() < dl:
        try:
            r = fn()
            if r: return r
        except: pass
        time.sleep(interval)
    raise TimeoutError(desc)

# ═══ USER / ADDRESS ════════════════════════════════════════════════════════

def ensure_address(token):
    s, r = api("GET", "/api/v1/users/me/addresses", token)
    if s == 200:
        addrs = r.get("data") or []
        if (isinstance(addrs, list) and addrs) or (isinstance(addrs, dict) and addrs): return
    api("POST", "/api/v1/users/me/addresses", token, body={
        "full_address": "123 Lê Lợi, Quận 1, TP. Hồ Chí Minh",
        "province_id": 79, "district_id": 1, "is_default": True
    })

def register_buyer(name, email):
    s, r = api("POST", "/api/v1/auth/register", body={
        "username": name, "email": email, "password": PW,
        "fullName": f"Race {name}", "role": "BUYER"
    })
    if s in (200, 201):
        t = r.get("accessToken") or r.get("data", {}).get("accessToken")
        if t: ensure_address(t); return t
    if s == 409 or (isinstance(r, dict) and "already" in str(r).lower()):
        return login(name)
    raise RuntimeError(f"Register {name}: {s}")

def get_customer_id(tok):
    s, r = api("GET", "/api/v1/users/me", tok)
    if s == 200:
        c = get_field(r, "id", "userId", "customerId")
        if c is not None: return int(c)
    raise RuntimeError(f"customerId: {s}")

def get_address_id(tok):
    s, r = api("GET", "/api/v1/users/me/addresses", tok)
    if s != 200: return None
    addrs = r.get("data") or []
    if isinstance(addrs, list) and addrs:
        a = addrs[0]; return int(a.get("address_id") or a.get("addressId") or 0)
    return None

def snapshot_max_parent(tok):
    s, r = api("GET", "/api/v1/orders?page=0&size=200", tok)
    if s != 200: return 0
    c = r.get("content") or r.get("data", {}).get("content") or []
    return max((o.get("parentOrderId") or 0 for o in c if isinstance(o, dict)), default=0)

# ═══ STOCK ═════════════════════════════════════════════════════════════════

def get_stock(seller_tok):
    s, inv = api("GET", f"/api/v1/inventory/{SKU}", seller_tok)
    if s == 200:
        v = get_field(inv, "stockAvailable", "stockTotal", "stock")
        return int(v) if v is not None else None
    return None

def set_stock(seller_tok, target):
    cur = get_stock(seller_tok)
    if cur is None: return False
    if cur == target: return True
    if cur > 0:
        api("POST", f"/api/v1/seller/inventory/adjust?skuCode={SKU}",
            seller_tok, body={"delta": -cur})
        time.sleep(0.3)
    s, _ = api("PUT", f"/api/v1/inventory/{SKU}/restock",
               seller_tok, body={"quantity": target})
    return s == 200

# ═══ INPUT ═════════════════════════════════════════════════════════════════

def ask_int(prompt, default):
    try:
        v = input(f"  {prompt} [{default}]: ").strip()
        if not v: return default
        return int(v)
    except (EOFError, KeyboardInterrupt): print("\n  Huỷ."); sys.exit(0)
    except ValueError: return default

# ═══ RACE ══════════════════════════════════════════════════════════════════

lock = threading.Lock()
race_results = []

def run_race(n_users, stock):
    global race_results
    race_results = []

    print()
    print("─" * 70)
    print(f"  🏁  RACE: {n_users} users, stock={stock}")
    print("─" * 70)

    # 1. Register
    print("\n  ⏳ Register users...", end=" ", flush=True)
    users = []
    for i in range(n_users):
        name = f"racer{i}"
        try: register_buyer(name, f"{name}@e2e.test")
        except: pass
        users.append(name)
    print(f"{len(users)} OK")

    # 2. Preview + snapshot pre_max
    print(f"  ⏳ Preview ({n_users} users)...")
    entries = []
    for i, u in enumerate(users):
        try:
            t = login(u)
            cid = get_customer_id(t)
            addr = get_address_id(t)
            api("DELETE", "/api/v1/cart", t)
            s, _ = api("POST", "/api/v1/cart/items", t,
                       body={"variantId": VARIANT_ID, "quantity": 1})
            if s not in (200, 201): continue
            s, r = api("POST", "/api/v1/cart/checkout/preview", t,
                       body={"itemIds": [f"{cid}:{VARIANT_ID}"]})
            if s != 200: continue
            pt = get_field(r, "previewToken")
            if not pt: continue
            pre_max = snapshot_max_parent(t)
            entries.append({"tid": i, "user": u, "token": t, "pt": pt,
                           "addr": addr, "cid": cid, "pre_max": pre_max})
        except Exception as e:
            print(f"    ⚠️  racer{i}: {e}")
    print(f"      {len(entries)}/{n_users} preview OK → vào race")

    if len(entries) <= stock:
        print(f"\n  ⚠️  Cần stock < preview_pass. Hiện có {len(entries)} preview_pass.")
        return

    # 3. SUBMIT đồng loạt
    print(f"  🚀  SUBMIT đồng loạt (Barrier={len(entries)})...")
    barrier = threading.Barrier(len(entries), timeout=30)

    def racer(e):
        try: barrier.wait(timeout=25)
        except: pass
        t0 = time.time()
        s, r = api("POST", "/api/v1/cart/checkout/submit", e["token"],
                   body={"previewToken": e["pt"], "addressId": e["addr"]})
        e["ms"] = (time.time() - t0) * 1000
        e["submit_status"] = s
        e["submit_body"] = json.dumps(r) if isinstance(r, dict) else str(r)
        # save for later
        e["session_id"] = get_field(r, "sessionId", "data", "sessionId") if s == 200 else None

    with ThreadPoolExecutor(max_workers=len(entries)) as ex:
        fs = [ex.submit(racer, e) for e in entries]
        for f in as_completed(fs): f.result()

    winners = [e for e in entries if e["submit_status"] == 200]
    losers  = [e for e in entries if e["submit_status"] != 200]

    print(f"\n  ⚡  Sau submit:")
    print(f"      Winners: {len(winners)} (HTTP 200)")
    print(f"      Losers:  {len(losers)}")
    for e in losers:
        body = e.get("submit_body", "")
        reason = "INSUFFICIENT" if "INSUFFICIENT" in body.upper() or "không đủ" in body.lower() else str(e["submit_status"])
        print(f"        racer{e['tid']}: {reason}")

    # 4. Payment simulation cho từng winner
    if winners:
        print(f"\n  💳  PAYMENT SIMULATION ({len(winners)} winners)...")
        print(f"      Mỗi winner: poll parent_order → forge webhook → verify PAID")
        print()

        for w in winners:
            tid = w["tid"]
            print(f"  ── racer{tid} ──")

            # Bước này mô phỏng "user nhập thẻ VISA 4242..."
            print(f"     1️⃣  Đang tìm parent_order (Kafka async)...")
            try:
                pid = poll(f"parent_order racer{tid}", lambda: _find_new_parent(w["token"], w["pre_max"]), timeout=60)
                w["parent_order_id"] = pid
                print(f"     2️⃣  Parent Order: {pid}")
            except TimeoutError:
                print(f"     2️⃣  ❌ Không tìm thấy parent order sau 60s")
                continue

            # Poll payment PENDING
            try:
                poll("payment PENDING", lambda: _check_payment_status(w["token"], pid, "PENDING"), timeout=30)
                print(f"     3️⃣  Payment: PENDING")
            except TimeoutError:
                print(f"     3️⃣  ⚠️  Payment chưa PENDING (có thể đã SUCCESS)")

            # Forge webhook (mô phỏng Stripe confirm payment thành công)
            code, _ = send_webhook(forge_pi(pid))
            print(f"     4️⃣  Webhook payment_intent.succeeded → {code}")

            # Verify payment SUCCESS
            try:
                poll("payment SUCCESS", lambda: _check_payment_status(w["token"], pid, "SUCCESS"), timeout=30)
                print(f"     5️⃣  Payment: SUCCESS ✅")
            except TimeoutError:
                print(f"     5️⃣  ⚠️  Payment chưa SUCCESS")

            # Verify orders PAID
            try:
                poll("orders PAID", lambda: _check_orders_paid(w["token"], pid), timeout=30)
                print(f"     6️⃣  Orders: PAID ✅")
            except TimeoutError:
                print(f"     6️⃣  ⚠️  Orders chưa PAID")

            print()

    # 5. Final results
    print("═" * 70)
    print("  📊  FINAL RESULTS")
    print("═" * 70)
    print(f"     Stock ban đầu:      {stock}")
    print(f"     Preview OK:         {len(entries)}")
    print(f"     SUBMIT OK (winners):{len(winners)}")
    print(f"        └─ Đã payment:   {sum(1 for w in winners if w.get('parent_order_id'))}")
    print(f"     SUBMIT FAIL (losers):{len(losers)}")
    print(f"        └─ Hết hàng:     {sum(1 for e in losers if 'INSUFFICIENT' in e.get('submit_body','').upper() or 'không đủ' in e.get('submit_body','').lower())}")

    # Stock after
    seller = login("techworld")
    final = get_stock(seller)
    winners_paid = [w for w in winners if w.get("parent_order_id")]
    print(f"\n     📦 Stock cuối:        {final}")
    if final == 0 and len(winners_paid) == stock:
        print(f"        → Stock đã về 0 = {stock} orders PAID — khớp!")
    elif final == 0:
        print(f"        → Stock 0, nhưng chỉ {len(winners_paid)} paid — còn lại là PENDING reservation (TTL 15 phút)")
    else:
        print(f"        → Còn stock — có thể có order bị cancelled hoặc stock chưa confirm hết")

    print()

    # Timeline
    for e in sorted(entries, key=lambda x: x["tid"]):
        icon = "💳" if e.get("parent_order_id") else ("✅" if e["submit_status"] == 200 else "❌")
        pid_str = f" pid={e.get('parent_order_id','')}" if e.get("parent_order_id") else ""
        print(f"  [{icon}] racer{e['tid']:3d}  submit={e['submit_status']}  {e['ms']:.0f}ms{pid_str}")

    # Race point explanation
    print()
    print("  📐  RACE POINT:")
    print("      CheckoutSubmitService.java:50  revalidateStock() — DB read")
    print("      CheckoutSubmitService.java:66  reserveStock()   — Redis LUA CAS + DB CAS")
    print("      → TOCTOU giữa 2 lần check stock.")
    print()
    print("  💳  PAYMENT FLOW (winners only):")
    print("      Cart → Preview → SUBMIT (race) → Kafka → OrderCreated →")
    print("      PaymentIntent (Stripe) → Form thẻ VISA →")
    print("      confirmPayment → webhook → PAID → CONFIRMED reservation")


def _find_new_parent(tok, pre_max):
    s, r = api("GET", "/api/v1/orders?page=0&size=200", tok)
    if s != 200: return None
    c = r.get("content") or r.get("data", {}).get("content") or []
    for o in c:
        if not isinstance(o, dict): continue
        pid = o.get("parentOrderId") or 0
        if pid > pre_max: return pid
    return None

def _check_payment_status(tok, pid, expect):
    s, r = api("GET", f"/api/v1/payments/parent-order/{pid}", tok)
    return s == 200 and get_field(r, "status") == expect

def _check_orders_paid(tok, pid):
    s, r = api("GET", f"/api/v1/orders/parent/{pid}", tok)
    if s != 200: return None
    orders = get_field(r, "orders") or []
    if not isinstance(orders, list) or not orders: return None
    return all(o.get("status") == "PAID" for o in orders)


# ═══ MAIN ══════════════════════════════════════════════════════════════════

def main():
    print()
    print("╔" + "═" * 68 + "╗")
    print("║" + "  RACE CONDITION DEMO — Full Checkout → Payment".center(68) + "║")
    print("╠" + "═" * 68 + "╣")
    print(f"║  Sản phẩm:  {PRODUCT_NAME:<52s}║")
    print(f"║  SKU:       {SKU:<52s}║")
    print(f"║  Giá:       {PRICE:<52s}║")
    print(f"║  Gateway:   {GW:<52s}║")
    print("╚" + "═" * 68 + "╝")
    print()

    print("⏳ Check connection...")
    try: seller_tok = login("techworld")
    except Exception as e:
        print(f"  ❌ {e}"); sys.exit(1)
    cur = get_stock(seller_tok)
    if cur is None:
        print(f"  ❌ Cannot read stock for {SKU}")
        sys.exit(1)
    print(f"  ✅ OK. Current stock: {cur}")
    print(f"\n  🔗 {FRONTEND_URL}")
    print()

    # Input
    print("─" * 70)
    print("  ⚙️   CONFIG")
    print("─" * 70)
    stock = ask_int("Set stock to", min(cur, 3))
    n = ask_int("Number of racers", stock + 7)
    if n <= stock:
        n = stock + 7; print(f"      → Auto-corrected to {n}")
    print()

    # Set stock
    print("─" * 70)
    print("  📦  SET STOCK")
    print("─" * 70)
    if not set_stock(seller_tok, stock):
        print("  ❌ Failed"); sys.exit(1)
    actual = get_stock(seller_tok)
    print(f"  ✅ Stock = {actual}")
    print()

    # Confirm
    print("─" * 70)
    print(f"  stock={stock}, racers={n}")
    print(f"  Expect: only {stock}/{n} succeed")
    print("─" * 70)
    if input("  Run? [y/N]: ").strip().lower() not in ("y", "yes"):
        print("  Cancelled."); sys.exit(0)

    run_race(n, stock)


if __name__ == "__main__":
    main()
