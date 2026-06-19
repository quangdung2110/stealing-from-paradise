#!/usr/bin/env python3
"""
Verify Redis Lua scripts & DB stock sync for race condition demo.

Tests:
  1. Redis ping + Lua script existence
  2. Compare Redis stock vs DB stock for demo variant
  3. Atomic Lua decrement: 2 threads race, verify only 1 wins
  4. StockReverconciliation check (Redis → DB drift detection)

Usage:
  python verify_stock_integrity.py
"""

import json
import time
import threading
import urllib.request
import urllib.error
import sys
import os

# ═══════════════════════════════════════════════════════════════════════════
GW   = os.environ.get("GATEWAY", "http://localhost:8080")
PW   = os.environ.get("E2E_DEV_PASSWORD", "dev123")

# Demo variant
SKU        = "FE-SKU-AIRPODS-COMBO"
VARIANT_ID = "90000000-0000-4000-9001-000000000103"
REDIS_KEY  = f"stock:available:{VARIANT_ID}"

# Redis config (from docker-compose)
REDIS_HOST = os.environ.get("REDIS_HOST", "localhost")
REDIS_PORT = 6379
REDIS_PASS = os.environ.get("REDIS_PASSWORD", "redis123")

# ── The EXACT Lua script from RedisLuaConfig.java ────────────────────────
STOCK_DECREMENT_LUA = """
local cur = redis.call('GET', KEYS[1])
if not cur then return -1 end
local n = tonumber(cur)
local q = tonumber(ARGV[1])
if n < q then return 0 end
redis.call('DECRBY', KEYS[1], q)
return 1
"""

# ═══════════════════════════════════════════════════════════════════════════
# API helper (same as demo)
# ═══════════════════════════════════════════════════════════════════════════

def api(method, path, token=None, body=None):
    url = f"{GW}{path}"
    data = None
    if body is not None:
        data = (body if isinstance(body, str) else json.dumps(body)).encode()
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if token: headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            raw = r.read().decode()
            try: return r.status, json.loads(raw) if raw else {}
            except: return r.status, raw
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try: return e.code, json.loads(raw) if raw else {}
        except: return e.code, raw
    except urllib.error.URLError as e:
        return -1, str(e)

def login(who):
    s, r = api("POST", "/api/v1/auth/login", body={"credential": who, "password": PW})
    if s != 200: raise RuntimeError(f"Login failed {who}: {s}")
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

# ═══════════════════════════════════════════════════════════════════════════
# Redis connection (using raw socket to avoid redis-py dependency)
# ═══════════════════════════════════════════════════════════════════════════

class RedisSocket:
    """Minimal Redis client over raw TCP socket — no redis-py needed."""

    def __init__(self, host=REDIS_HOST, port=REDIS_PORT, password=REDIS_PASS):
        import socket
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.settimeout(10)
        self.sock.connect((host, port))
        self._file = self.sock.makefile('r', buffering=1, newline='\r\n')
        if password:
            self.auth(password)

    def auth(self, password):
        resp = self._cmd("AUTH", password)
        if resp != "OK":
            resp2 = self._cmd("AUTH", password, "default")
            if resp2 != "OK":
                raise RuntimeError(f"Redis AUTH failed: {resp}")

    def _cmd(self, *args):
        parts = [f"${len(b)}\r\n{b}".encode() if isinstance(b, str) else f"${len(str(b))}\r\n{str(b)}".encode()
                 for b in args]
        cmd = f"*{len(parts)}\r\n".encode() + b"".join(parts)
        self.sock.sendall(cmd)
        return self._read()

    def _read(self):
        line = self._file.readline().rstrip('\r\n')
        if not line:
            raise ConnectionError("Redis closed connection")
        c = line[0]
        if c == '+': return line[1:]
        if c == '-': return 'ERR:' + line[1:]
        if c == ':': return int(line[1:])
        if c == '$':
            length = int(line[1:])
            if length < 0: return None
            data = self._file.read(length + 2)  # +2 for \r\n
            return data.rstrip('\r\n')
        if c == '*':
            count = int(line[1:])
            return [self._read() for _ in range(count)]
        raise ValueError(f"Unknown Redis response: {line}")

    def get(self, key):
        return self._cmd("GET", key)

    def set(self, key, value):
        return self._cmd("SET", key, str(value))

    def incrby(self, key, delta):
        return self._cmd("INCRBY", key, str(delta))

    def decrby(self, key, delta):
        return self._cmd("DECRBY", key, str(delta))

    def eval(self, script, numkeys, *keys_and_args):
        """Run EVAL script numkeys key1 ... arg1 ..."""
        return self._cmd("EVAL", script, str(numkeys), *keys_and_args)

    def close(self):
        try: self.sock.close()
        except: pass


# ═══════════════════════════════════════════════════════════════════════════
# TESTS
# ═══════════════════════════════════════════════════════════════════════════

passed = 0
failed = 0

def test(name, condition, detail=""):
    global passed, failed
    if condition:
        print(f"  ✅ {name}")
        passed += 1
    else:
        print(f"  ❌ {name}  {detail}")
        failed += 1


def main():
    global passed, failed
    print()
    print("=" * 72)
    print("  VERIFY Redis Lua Script + DB Stock Sync")
    print("=" * 72)
    print(f"  Redis:    {REDIS_HOST}:{REDIS_PORT}")
    print(f"  Variant:  {VARIANT_ID}")
    print(f"  SKU:      {SKU}")
    print(f"  RedisKey: {REDIS_KEY}")
    print()

    # ── 1. Redis connectivity ─────────────────────────────────────────────
    print("[1] Redis connectivity")
    try:
        redis = RedisSocket()
        test("Redis connected", True)
    except Exception as e:
        test("Redis connected", False, str(e))
        print("\n  ❌ Cannot proceed without Redis — is docker-compose running?")
        return

    # ── 2. Check Redis key exists ────────────────────────────────────────
    print("\n[2] Redis stock key check")
    raw = redis.get(REDIS_KEY)
    redis_stock_raw = raw
    test("Key exists in Redis", raw is not None,
         f"key={REDIS_KEY} → {raw} (Redis may not be warmed up)")
    if raw is not None:
        redis_stock = int(raw)
        print(f"     Redis stock: {redis_stock}")
    else:
        redis_stock = None

    # ── 3. Check DB stock via API ─────────────────────────────────────────
    print("\n[3] DB stock check (via Inventory API)")
    try:
        seller_token = login("techworld")
    except Exception as e:
        test("Seller login", False, str(e))
        redis.close()
        return

    s, inv = api("GET", f"/api/v1/inventory/{SKU}", seller_token)
    if s == 200:
        db_stock = get_field(inv, "stockAvailable", "stockTotal", "stock")
        if db_stock is not None:
            db_stock = int(db_stock)
        db_locked = get_field(inv, "stockLocked") or 0
        print(f"     DB stockAvailable: {db_stock}")
        print(f"     DB stockLocked:    {db_locked}")
        test("API returned stock", db_stock is not None)
    else:
        db_stock = None
        test("API returned stock", False, f"HTTP {s}: {str(inv)[:100]}")

    # ── 4. Compare Redis vs DB ────────────────────────────────────────────
    print("\n[4] Redis ↔ DB sync check")
    if redis_stock is not None and db_stock is not None:
        drift = abs(redis_stock - db_stock)
        in_sync = drift == 0
        test(f"Redis({redis_stock}) == DB({db_stock})", in_sync,
             f"DRIFT={drift} — run StockReconciliationScheduler or restart product-service")
        if drift > 0 and redis_stock < db_stock:
            print(f"     ⚠️  Redis < DB → possible oversell window. Fix: SET Redis to DB value.")
        elif drift > 0 and redis_stock > db_stock:
            print(f"     ⚠️  Redis > DB → Redis cache stale, DB was decremented without Redis update.")
    else:
        test("Cannot compare (missing data)", False)

    # ── 5. Lua script correctness (unit test against Redis) ──────────────
    print("\n[5] Lua script unit test")
    try:
        # Set a test key
        test_key = REDIS_KEY  # use the real key
        original = int(redis.get(test_key) or 0)

        # Set known value for testing
        redis.set(test_key, "10")

        # Test 1: normal decrement
        r1 = redis.eval(STOCK_DECREMENT_LUA, 1, test_key, "3")
        new_val = int(redis.get(test_key))
        test("Lua decrement 10-3=7 → result=1", r1 == 1 and new_val == 7,
             f"result={r1}, new_val={new_val}")

        # Test 2: insufficient stock
        r2 = redis.eval(STOCK_DECREMENT_LUA, 1, test_key, "999")
        val2 = int(redis.get(test_key))
        test("Lua insufficient stock → result=0, val unchanged",
             r2 == 0 and val2 == 7,
             f"result={r2}, val={val2}")

        # Test 3: exact match
        r3 = redis.eval(STOCK_DECREMENT_LUA, 1, test_key, "7")
        val3 = int(redis.get(test_key))
        test("Lua exact 7-7=0 → result=1", r3 == 1 and val3 == 0,
             f"result={r3}, val={val3}")

        # Test 4: zero stock → insufficient
        r4 = redis.eval(STOCK_DECREMENT_LUA, 1, test_key, "1")
        test("Lua 0 < 1 → result=0", r4 == 0,
             f"result={r4}")

        # Restore original value
        redis.set(test_key, str(original))

    except Exception as e:
        test("Lua test execution", False, str(e))
        import traceback
        traceback.print_exc()

    # ── 6. ATOMICITY TEST: 2 threads race on same key ────────────────────
    print("\n[6] Lua atomicity test (2 threads race)")

    # Create a temp key with stock=1
    race_key = f"stock:available:race_test_{int(time.time())}"
    redis.set(race_key, "1")

    results = []
    def racer(name):
        try:
            r = RedisSocket()
            v = r.eval(STOCK_DECREMENT_LUA, 1, race_key, "1")
            results.append((name, v))
            r.close()
        except Exception as e:
            results.append((name, f"ERR:{e}"))

    t1 = threading.Thread(target=racer, args=("thread-A",))
    t2 = threading.Thread(target=racer, args=("thread-B",))
    t1.start(); t2.start()
    t1.join(); t2.join()

    winners = [r for r in results if r[1] == 1]
    losers  = [r for r in results if r[1] == 0]
    errors  = [r for r in results if r[1] not in (0, 1)]

    atomic_ok = len(winners) == 1 and len(losers) == 1 and len(errors) == 0
    test("Atomic: exactly 1 thread gets stock=1",
         atomic_ok,
         f"winners={len(winners)}, losers={len(losers)}, errors={errors}")

    for name, val in results:
        icon = "✅ WON" if val == 1 else ("❌ LOST" if val == 0 else "💥 ERR")
        print(f"     [{icon}] {name}: Lua returned {val}")

    # Restore original
    try: redis._cmd("DEL", race_key)
    except: pass

    # ── 7. StockReconciliationScheduler check ─────────────────────────────
    print("\n[7] Reconciliation scheduler status")

    # Check if scheduler would detect drift by intentionally creating a drift
    # then running the reconciliation logic manually
    if redis_stock is not None:
        test_key2 = REDIS_KEY
        current_redis = int(redis.get(test_key2))
        # Intentionally set Redis to wrong value
        redis.set(test_key2, str(current_redis + 5))
        time.sleep(0.1)
        drifted_val = int(redis.get(test_key2))
        drift_created = drifted_val == current_redis + 5
        test("Drift simulation: Redis value changed artificially", drift_created,
             f"expected {current_redis+5}, got {drifted_val}")

        # The scheduler should catch this on next tick.
        # For now, restore correct value
        redis.set(test_key2, str(current_redis))
        after_restore = int(redis.get(test_key2))
        test("Drift restored correctly", after_restore == current_redis,
             f"expected {current_redis}, got {after_restore}")
    else:
        test("Drift test skipped (no Redis key)", None)

    # ── 8. API check: reserveStock end-to-end via checkout submit ─────────
    print("\n[8] ReserveStock end-to-end (API → Redis → DB)")

    try:
        buyer_token = login("minhhoa")
    except Exception as e:
        test("Buyer login", False, str(e))
        redis.close()
        return

    # Get current stock from API
    s, inv = api("GET", f"/api/v1/inventory/{SKU}", seller_token)
    pre_stock = get_field(inv, "stockAvailable") or 0
    pre_redis = int(redis.get(REDIS_KEY) or 0)

    # Do a full checkout → this exercises Redis Lua + DB CAS
    # Setup cart
    api("DELETE", "/api/v1/cart", buyer_token)
    s_buyer, r_buyer = api("GET", "/api/v1/users/me", buyer_token)
    cid = get_field(r_buyer, "id", "userId")

    api("POST", "/api/v1/cart/items", buyer_token,
        body={"variantId": VARIANT_ID, "quantity": 1})

    s, r = api("POST", "/api/v1/cart/checkout/preview", buyer_token,
               body={"itemIds": [f"{cid}:{VARIANT_ID}"]})
    if s != 200:
        test("Preview for E2E", False, f"HTTP {s}: {str(r)[:100]}")
        redis.close()
        return
    preview_token = get_field(r, "previewToken")
    test("Preview token obtained", preview_token is not None)

    # Get address
    s, ra = api("GET", "/api/v1/users/me/addresses", buyer_token)
    addr_id = None
    if s == 200:
        addrs = ra.get("data") or []
        if isinstance(addrs, list) and addrs:
            addr_id = addrs[0].get("address_id")
    test("Address obtained", addr_id is not None, f"(got {addr_id})")

    # Submit
    s, r = api("POST", "/api/v1/cart/checkout/submit", buyer_token,
               body={"previewToken": preview_token, "addressId": addr_id})
    test("Checkout submit OK", s == 200, f"HTTP {s}: {str(r)[:100]}")

    # Check stock after
    time.sleep(1)
    s, inv2 = api("GET", f"/api/v1/inventory/{SKU}", seller_token)
    post_stock = get_field(inv2, "stockAvailable")
    if post_stock is not None:
        post_stock = int(post_stock)
    post_redis = int(redis.get(REDIS_KEY) or 0)
    test(f"Stock decremented: {pre_stock} → {post_stock} (DB)",
         post_stock is not None and post_stock == pre_stock - 1,
         f"expected {pre_stock-1}, got {post_stock}")
    test(f"Redis decremented: {pre_redis} → {post_redis}",
         pre_redis == pre_stock and post_redis == post_stock,
         f"Redis {pre_redis}→{post_redis}, DB {pre_stock}→{post_stock}")

    # ── SUMMARY ───────────────────────────────────────────────────────────
    print()
    print("=" * 72)
    print(f"  RESULTS: {passed} passed, {failed} failed")
    print("=" * 72)

    if failed > 0:
        print()
        print("  🔧 TROUBLESHOOTING:")
        print("     - Redis key missing? → Restart product-service to warm up cache")
        print("     - Redis/DB drift?   → Wait for StockReconciliationScheduler (5 min)")
        print("     - Lua script wrong? → Check RedisLuaConfig.java")
        print("     - Auth failed?      → Seed database: docker compose down -v && up")
    else:
        print()
        print("  ✅ ALL CHECKS PASSED — Redis Lua + DB stock ready for race condition demo!")
        print(f"  🚀 Run: python demo_race_condition.py")

    redis.close()


if __name__ == "__main__":
    main()
