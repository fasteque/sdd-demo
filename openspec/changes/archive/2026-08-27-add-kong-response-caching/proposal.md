## Why

Repeated `GET` requests to the asset endpoints currently hit the app (and MongoDB) every time, even when nothing has changed. Caching short-lived read responses at the gateway lets us demo cache-hit behavior and reduce redundant backend load without touching application code.

## What Changes

- Add Kong's `proxy-cache` plugin to the `assets` route in `kong/kong.yml`, scoped to `GET`/`HEAD` requests only, caching `200` responses for 30 seconds, using the `memory` strategy (in-process, no new datastore).
- Amend the `api-gateway` spec's "No Traffic Policies Applied at the Gateway" requirement again: add `proxy-cache` as a fourth named plugin exception, scoped to the asset routes only.
- Cache hits are visible on the response via Kong's built-in `X-Cache-Status` header (`Hit`/`Miss`/`Bypass`/`Refresh`) -- this is a fixed, non-configurable header name from the stock `proxy-cache` plugin's schema (verified against the plugin source: `response_headers["X-Cache-Status"]` is a boolean on/off toggle, not a renameable field). **This header does not conform to the `api-naming-conventions` store's "no service-specific `X-` prefix" rule** -- unlike the earlier `Kong-Request-ID` conflict, there is no free rename available without adding a second plugin (a `response-transformer` to rewrite it). Decision, made explicitly rather than silently: accept `X-Cache-Status` as a documented, plugin-imposed exception. See design.md's Decisions section for the full rationale.
- Writes (`POST /assets`, `DELETE /assets/{id}`) are never cached (not in the plugin's cacheable-method list) and do **not** invalidate a previously cached `GET` response -- this stack has no cache-purge mechanism available (`KONG_ADMIN_LISTEN` is `off`, no Admin API). Staleness after a write is bounded only by the 30-second TTL. This is an accepted trade-off given the short TTL, not an oversight.
- `/health` is not cached.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `api-gateway`: the "No Traffic Policies Applied at the Gateway" requirement changes again to permit `proxy-cache`, applied only to the asset routes. A new requirement documents the caching behavior itself (scope, TTL, cache-hit visibility, and the no-invalidation-on-write trade-off).

## Impact

- **Config**: `kong/kong.yml` -- new `plugins` entry under the `assets` route.
- **Spec**: `openspec/specs/api-gateway/spec.md` -- requirement text and scenarios updated (this is the third amendment to "No Traffic Policies Applied at the Gateway", following `correlation-id` and `key-auth`/`rate-limiting`).
- **Runtime**: no app code changes; Kong (DB-less mode) picks up the new plugin config on stack restart. `strategy: memory` means cached entries live in the Kong node's own worker memory -- not shared across multiple Kong nodes and cleared on restart, consistent with the existing single-node `compose.app.yaml` topology and the precedent set by the rate-limiting change's `policy: local` choice.
- **Client-visible**: asset API consumers now see an `X-Cache-Status` response header on `GET` requests to the asset endpoints; a cached response may be up to 30 seconds stale relative to the underlying data, including immediately after a write to the same resource.

## Non-goals

- No cache invalidation on write (no purge mechanism available without enabling the Admin API, which is out of scope and would itself need its own security review).
- No caching of `/health`.
- No caching of non-`GET`/`HEAD` methods.
- No shared/distributed cache across multiple Kong nodes (`strategy: redis` or similar) -- out of scope for this single-node demo stack, matching the earlier rate-limiting change's `policy: local` precedent.
- No per-consumer cache variation -- the cache key is not varied by API key, so different consumers requesting the same asset path within the TTL window see the same cached response. Acceptable today since there is a single registered consumer (`asset-client`).
