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

check "GET /assets with the correct key returns 200" \
  "200" "$(status -H "apikey: $API_KEY" "$BASE_URL/assets")"

check "GET /assets/{id} with no key returns 401" \
  "401" "$(status "$BASE_URL/assets/some-id")"

check "GET /assets with the correct key as a query param returns 401 (header-only)" \
  "401" "$(status "$BASE_URL/assets?apikey=$API_KEY")"

check "GET on an unmapped path returns Kong's own 404" \
  "404" "$(status "$BASE_URL/nope")"

check_request_id "GET /health response carries a Request-Id header" \
  "$BASE_URL/health"

check_request_id "GET /assets response (authenticated) carries a Request-Id header" \
  -H "apikey: $API_KEY" "$BASE_URL/assets"

echo
echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
