## 1. Gateway Configuration

- [x] 1.1 Add the `rate-limiting` plugin to the `assets` route in `kong/kong.yml`, configured with `minute: 5`, `limit_by: consumer`, `policy: local`, and verify the file still passes Kong's declarative-config validation (`kong config parse kong/kong.yml` or equivalent, e.g. via `docker compose -f compose.app.yaml config` plus a stack restart with no startup errors)
- [x] 1.2 Confirm the `health` route's plugin list is unchanged (no rate-limiting attached) by inspecting the route block in `kong/kong.yml`

## 2. Manual Verification

- [x] 2.1 Start the containerized stack (`compose.app.yaml`) and send 5 requests within a minute to an asset endpoint using the `asset-client` API key; verify all 5 are forwarded and return the app's normal response
- [x] 2.2 Send a 6th request within the same minute using the same API key; verify the gateway returns `429` and the app does not receive the request (check app logs/traceId absence)
- [x] 2.3 Send requests to `/health` in a tight loop (more than 5 within a minute); verify none are rejected with 429

## 3. Spec Sync

- [x] 3.1 Confirm `openspec/changes/add-kong-rate-limiting/specs/api-gateway/spec.md` accurately reflects the final `kong/kong.yml` plugin config (limit values, `limit_by`, `policy`) before running `/opsx:sync`

## 4. Code Review Fix -- Response Header Leak

- [x] 4.1 Add `hide_client_headers: true` to the `rate-limiting` plugin config in `kong/kong.yml` (code review found the default `false` injects `RateLimit-*`/`X-RateLimit-*` headers into every response, not just the 429, contradicting the "unmodified response" guarantee) and verify live that an in-limit `GET /assets` returns `200` with no rate-limit headers present
- [x] 4.2 Verify live that an over-limit request still returns `429` with a Kong-generated JSON body, and note that `hide_client_headers: true` also removes `Retry-After`/`RateLimit-*` from the 429 response (documented as an accepted trade-off in design.md)
- [x] 4.3 Update `specs/api-gateway/spec.md`'s delta scenarios and `design.md`/`proposal.md` to reflect the `hide_client_headers: true` config and its 429-header trade-off before running `/opsx:sync`

## 5. Code Review Fix -- Automated Rate-Limit Coverage

- [x] 5.1 Extend `kong/test-gateway.sh` with automated coverage for the rate-limit behavior: 5 authenticated `/assets` requests succeed, the 6th returns `429`, and `/health` stays unaffected under a rapid-request loop -- verified with 3 consecutive clean runs (15/15 checks passing) against a freshly restarted stack
