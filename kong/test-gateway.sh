#!/usr/bin/env bash
# Smoke tests for the Kong gateway's asset-endpoint API key authentication.
# Requires the containerized stack to already be running:
#   docker compose -f compose.app.yaml up --build
# Usage: ./kong/test-gateway.sh [base-url]  (default: http://localhost:8080)

set -u

BASE_URL="${1:-http://localhost:8080}"
API_KEY="$(grep -m1 '^KONG_ASSET_API_KEY=' .env 2>/dev/null | cut -d= -f2-)"

# Configured rate limit for the assets route (kong/kong.yml: rate-limiting
# plugin, minute). The cache test below reuses this same request sequence
# (see the block comment further down), so this one constant drives both.
RATE_LIMIT=5

if [ -z "$API_KEY" ]; then
  echo "Could not read KONG_ASSET_API_KEY from .env -- is the stack configured?" >&2
  exit 1
fi

pass=0
fail=0

status() {
  curl -s -o /dev/null -w "%{http_code}" "$@"
}

check() {
  local desc="$1" expected="$2" actual="$3"
  if [ "$actual" = "$expected" ]; then
    echo "PASS: $desc (got $actual)"
    pass=$((pass + 1))
  else
    echo "FAIL: $desc (expected $expected, got $actual)" >&2
    fail=$((fail + 1))
  fi
}

# Performs a GET with the given curl args and sets RESP_CODE/RESP_HEADERS.
# Pure bash string splitting (no forked tail/sed) to keep this cheap to call
# repeatedly in a back-to-back sequence where elapsed time matters (see the
# rate-limit/cache block below).
fetch() {
  local raw
  raw="$(curl -s -D - -o /dev/null -w '\n%{http_code}' "$@")"
  RESP_CODE="${raw##*$'\n'}"
  RESP_HEADERS="${raw%$'\n'*}"
}

# Asserts a header pattern's presence ("present") or absence ("absent") in a
# headers blob already captured by the caller (e.g. via `fetch`).
check_header() {
  local desc="$1" headers="$2" pattern="$3" mode="$4"
  local matched=false
  if printf '%s\n' "$headers" | grep -qi "$pattern"; then
    matched=true
  fi
  if { [ "$mode" = "present" ] && [ "$matched" = "true" ]; } || { [ "$mode" = "absent" ] && [ "$matched" = "false" ]; }; then
    echo "PASS: $desc"
    pass=$((pass + 1))
  else
    echo "FAIL: $desc" >&2
    fail=$((fail + 1))
  fi
}

check "GET /health with no key returns 200" \
  "200" "$(status "$BASE_URL/health")"

check "GET /assets with no key returns 401" \
  "401" "$(status "$BASE_URL/assets")"

check "GET /assets with a wrong key returns 401" \
  "401" "$(status -H "apikey: wrong-key" "$BASE_URL/assets")"

check "GET /assets/{id} with no key returns 401" \
  "401" "$(status "$BASE_URL/assets/some-id")"

check "GET /assets with the correct key as a query param returns 401 (header-only)" \
  "401" "$(status "$BASE_URL/assets?apikey=$API_KEY")"

check "GET on an unmapped path returns Kong's own 404" \
  "404" "$(status "$BASE_URL/nope")"

fetch "$BASE_URL/health"
check_header "GET /health response carries a Request-Id header" \
  "$RESP_HEADERS" '^Request-Id:' "present"
check_header "GET /health response does not carry an X-Cache-Status header" \
  "$RESP_HEADERS" '^X-Cache-Status:' "absent"

# Rate limiting: RATE_LIMIT requests/minute per consumer on /assets, /health
# exempt. This block is the ONLY place in this script that sends an
# authenticated request to /assets, run back-to-back with nothing else in
# between, so its count maps directly onto the configured limit -- no other
# check's request needs to be accounted for, and no slow unrelated check is
# interleaved that could let elapsed time drift across Kong's per-minute
# window boundary mid-count. The first request here also covers "correct
# key returns 200" and "authenticated response carries a Request-Id header".
#
# The same sequence doubles as the response-caching test: all RATE_LIMIT + 1
# requests below are identical (same method, path, no query string), so they
# share one proxy-cache cache key. Request 1 is necessarily a cache miss;
# requests 2..RATE_LIMIT land within the 30s TTL and must be cache hits; the
# (RATE_LIMIT + 1)th never reaches proxy-cache at all (rate-limiting rejects
# it first in the plugin pipeline, confirmed live: a 429 response carries no
# X-Cache-Status header). The loop bounds and the final rejected request are
# both driven by RATE_LIMIT above, not restated as separate hardcoded numbers.
fetch -H "apikey: $API_KEY" "$BASE_URL/assets"
check "GET /assets with the correct key returns 200" "200" "$RESP_CODE"
check_header "GET /assets response (authenticated) carries a Request-Id header" \
  "$RESP_HEADERS" '^Request-Id:' "present"
check_header "First GET /assets request is a cache miss (X-Cache-Status: Miss)" \
  "$RESP_HEADERS" '^X-Cache-Status: Miss' "present"

# The cache is now warm for GET /assets. Prove key-auth still gates the route
# regardless -- a cached entry that could be served to an unauthenticated or
# wrongly-authenticated caller would be a security regression independent of
# whether the miss/hit behavior itself is otherwise correct. Neither of these
# is authenticated, so neither reaches rate-limiting or consumes its budget.
check "GET /assets with no key is still rejected once the cache is warm" \
  "401" "$(status "$BASE_URL/assets")"
check "GET /assets with a wrong key is still rejected once the cache is warm" \
  "401" "$(status -H "apikey: wrong-key" "$BASE_URL/assets")"

for i in $(seq 2 "$RATE_LIMIT"); do
  fetch -H "apikey: $API_KEY" "$BASE_URL/assets"
  check "Rate limit allows /assets request within the $RATE_LIMIT/minute budget ($i/$RATE_LIMIT)" \
    "200" "$RESP_CODE"
  check_header "/assets request $i/$RATE_LIMIT is served from cache (X-Cache-Status: Hit)" \
    "$RESP_HEADERS" '^X-Cache-Status: Hit' "present"
done

over_limit=$((RATE_LIMIT + 1))
fetch -H "apikey: $API_KEY" "$BASE_URL/assets"
check "${over_limit}th /assets request within the same minute is rate-limited" \
  "429" "$RESP_CODE"
check_header "Rate-limited 429 response carries no X-Cache-Status header (proxy-cache never runs after rejection)" \
  "$RESP_HEADERS" '^X-Cache-Status:' "absent"

health_rate_limited=false
for i in $(seq 1 8); do
  if [ "$(status "$BASE_URL/health")" = "429" ]; then
    health_rate_limited=true
  fi
done
check "GET /health is never rate-limited even under rapid repeated requests" \
  "false" "$health_rate_limited"

echo
echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
