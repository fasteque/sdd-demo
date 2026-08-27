#!/usr/bin/env bash
# Smoke tests for the Kong gateway's asset-endpoint API key authentication.
# Requires the containerized stack to already be running:
#   docker compose -f compose.app.yaml up --build
# Usage: ./kong/test-gateway.sh [base-url]  (default: http://localhost:8080)

set -u

BASE_URL="${1:-http://localhost:8080}"
API_KEY="$(grep -m1 '^KONG_ASSET_API_KEY=' .env 2>/dev/null | cut -d= -f2-)"

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

check_request_id() {
  local desc="$1"; shift
  local header
  header="$(curl -s -D - -o /dev/null "$@" | grep -i '^Request-Id:' || true)"
  if [ -n "$header" ]; then
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

check_request_id "GET /health response carries a Request-Id header" \
  "$BASE_URL/health"

# Rate limiting: 5 requests/minute per consumer on /assets, /health exempt.
# This block is the ONLY place in this script that sends an authenticated
# request to /assets, run back-to-back with nothing else in between, so its
# count maps directly onto the configured limit (minute: 5) -- no other
# check's request needs to be accounted for, and no slow unrelated check is
# interleaved that could let elapsed time drift across Kong's per-minute
# window boundary mid-count. The first request here also covers "correct
# key returns 200" and "authenticated response carries a Request-Id header".
response="$(curl -s -D - -o /dev/null -w '\n%{http_code}' -H "apikey: $API_KEY" "$BASE_URL/assets")"
code="$(printf '%s\n' "$response" | tail -n1)"
headers="$(printf '%s\n' "$response" | sed '$d')"

check "GET /assets with the correct key returns 200" "200" "$code"

if printf '%s\n' "$headers" | grep -qi '^Request-Id:'; then
  echo "PASS: GET /assets response (authenticated) carries a Request-Id header"
  pass=$((pass + 1))
else
  echo "FAIL: GET /assets response (authenticated) carries a Request-Id header" >&2
  fail=$((fail + 1))
fi

for i in 2 3 4 5; do
  check "Rate limit allows /assets request within the 5/minute budget ($i/5)" \
    "200" "$(status -H "apikey: $API_KEY" "$BASE_URL/assets")"
done

check "6th /assets request within the same minute is rate-limited" \
  "429" "$(status -H "apikey: $API_KEY" "$BASE_URL/assets")"

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
