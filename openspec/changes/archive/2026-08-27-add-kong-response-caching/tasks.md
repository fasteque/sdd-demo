## 1. Gateway Configuration

- [x] 1.1 Add the `proxy-cache` plugin to the `assets` route in `kong/kong.yml`, configured with `strategy: memory`, `cache_ttl: 30`, `request_method: [GET, HEAD]`, `response_code: [200]`, and verify the file still passes Kong's declarative-config validation (`kong config parse kong/kong.yml`, e.g. via `docker run --rm -e KONG_DATABASE=off -v <path>:/tmp/kong.yml:ro kong:3.9.3 kong config parse /tmp/kong.yml`)
- [x] 1.2 Confirm the `health` route's plugin list is unchanged (no proxy-cache attached) by inspecting the route block in `kong/kong.yml`

## 2. Manual Verification

- [x] 2.1 Start the containerized stack (`compose.app.yaml`) and send a `GET /assets` request with the `asset-client` API key; verify the response carries `X-Cache-Status: Miss`
- [x] 2.2 Immediately repeat the same `GET /assets` request; verify the response carries `X-Cache-Status: Hit` and returns the same body
- [x] 2.3 Wait for the 30-second TTL to elapse and repeat the request; verify the response carries `X-Cache-Status: Miss` again (cache expired)
- [x] 2.4 Send `POST /assets` (or `DELETE /assets/{id}`); verify the response is not cached (no `X-Cache-Status` semantics implying a stored write) and does not itself get served from cache on a repeat
- [x] 2.5 Send requests to `/health` in a loop; verify none carry an `X-Cache-Status` header

## 3. Spec Sync

- [x] 3.1 Confirm `openspec/changes/add-kong-response-caching/specs/api-gateway/spec.md` accurately reflects the final `kong/kong.yml` plugin config (TTL, cacheable methods/status codes, strategy) before running `/opsx:sync`

## 4. Automated Coverage

- [x] 4.1 Extend `kong/test-gateway.sh` with cases for: first `GET /assets` is a cache miss, an immediate repeat is a cache hit, and `/health` never carries `X-Cache-Status` -- run the extended script against a freshly restarted stack and confirm all checks pass. Be mindful of the rate-limiting section already in the script: reuse or account for its request budget rather than assuming a clean 5-request window is still available when the cache checks run (see the existing rate-limit block's own comment for why request ordering/count matters against a shared per-minute budget).

  Implementation note: reused the existing rate-limit block's request sequence instead of adding new requests -- all 6 requests in that block hit the same cache key, so request 1 is naturally a cache miss, requests 2-5 (already asserted `200` for the rate-limit budget) are also asserted `X-Cache-Status: Hit`, and the 6th (429) is asserted to carry no `X-Cache-Status` header at all (verified live: proxy-cache never runs once rate-limiting has already rejected the request). No new request-budget accounting was needed. Verified with 3 consecutive clean runs (22/22 checks passing) against a freshly restarted stack.

## 5. Code Review Fixes

- [x] 5.1 Pin `content_type` explicitly on the `proxy-cache` plugin in `kong/kong.yml` (`application/json` and `application/json; charset=utf-8`) instead of relying on Kong's implicit default match, which would silently stop caching if the app's emitted `Content-Type` ever gains a charset suffix
- [x] 5.2 Add a test case proving key-auth still rejects unauthenticated/wrong-key requests to `/assets` once a cache entry is already warm, so a future plugin-priority regression that let `proxy-cache` bypass auth would be caught
- [x] 5.3 Refactor `kong/test-gateway.sh`: add a `fetch` helper (bash-native response/header splitting, no forked `tail`/`sed`) to cut per-request overhead in the back-to-back rate-limit/cache sequence and eliminate the triplicated curl-split pattern; fold `check_request_id`'s bookkeeping into `check_header` (removing the duplicate implementation); reuse one `/health` fetch for both its checks instead of issuing it twice; introduce a `RATE_LIMIT` constant driving the loop bounds and the "over-limit" request/description instead of hardcoded numbers tied together only by a comment -- verified with 3 consecutive clean runs (24/24 checks passing) against a freshly restarted stack
