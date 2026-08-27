## Context

`kong/kong.yml` is a DB-less declarative config, and `compose.app.yaml` runs exactly one Kong node (no cluster, no shared cache). The asset route already resolves a consumer identity via `key-auth` (the `asset-client` consumer). See proposal.md - Why.

## Goals / Non-Goals

**Goals:**
- Enforce 5 requests/minute per consumer on the asset routes only, using Kong's built-in `rate-limiting` plugin.
- Keep the change confined to `kong/kong.yml` and the `api-gateway` spec — no app code changes.

**Non-Goals:**
- Rate limiting that survives Kong restarts or is shared across multiple Kong nodes (would require `policy: redis`, an unapproved new infra dependency).
- Rate limiting `/health` or any unauthenticated route.
- Per-IP or global (non-consumer) limiting.

## Decisions

**Plugin scope: route-level, not global.** Attach `rate-limiting` under the `assets` route's `plugins` list (same place `key-auth` already lives), not top-level. Keeps `/health` unaffected without needing a route-exclusion mechanism, and mirrors the existing pattern for `key-auth`.

**`limit_by: consumer`.** The plugin needs a consumer to key on; `key-auth` (already required on this route) resolves one for every request that reaches the plugin. Requests without a valid key are already rejected by `key-auth` before rate-limiting would run (Kong plugins on a route execute in phase order: auth phase before access/traffic-control phase), so there's no unauthenticated path that skips both.

**`policy: local`.** Local keeps the counter in the Kong node's own memory — no new datastore, no new approved-dependency request. Alternative considered: `policy: redis`, which would survive restarts and work across multiple nodes, but this stack runs a single Kong node for local dev/demo purposes, so the durability `redis` buys isn't needed, and it would require adding Redis as a new infra dependency purely for this. `local` is the correct trade-off here.

**Limit shape:** `minute: 5` (Kong's rate-limiting plugin config exposes per-second/minute/hour/day/month windows; only `minute` is set, per the ask).

**`hide_client_headers: true`.** Kong's `rate-limiting` plugin defaults `hide_client_headers` to `false`, which injects `RateLimit-Limit`/`RateLimit-Remaining`/`RateLimit-Reset` (and legacy `X-RateLimit-*-Minute`) headers into *every* response through the route, not only the 429 — caught in code review (live-verified: an in-limit `GET /assets` returned `200` with those headers present) as a violation of the `api-gateway` spec's existing "gateway forwards/returns unmodified" guarantee and this same change's own delta scenario making the same claim. Setting `hide_client_headers: true` restores that guarantee. Trade-off: this also suppresses `Retry-After` and `RateLimit-*` on the 429 response itself (live-verified) — clients get no machine-readable retry-timing hint, only the JSON error body. Accepted for this demo-scoped change since the alternative (leaving headers on) is a bigger, undocumented widening of the response contract than losing a Retry-After hint on a 5-req/min throttle; revisit if a real client needs programmatic backoff timing.

## Risks / Trade-offs

- **[Risk]** `policy: local` means the counter resets on Kong container restart, and each Kong node (if the stack were ever scaled) would count independently rather than sharing a single limit → **Mitigation:** acceptable and expected for a single-node demo stack (see Non-Goals); called out explicitly in the proposal's Impact section so it isn't mistaken for a bug later.
- **[Risk]** Kong's own 429 body doesn't conform to this app's standard error envelope → **Mitigation:** none needed — the existing `api-error-responses` spec already scopes itself to app-originated responses and explicitly excludes gateway-rejected requests.
