## Why

The asset API currently has no request-rate protection: a client with a valid API key can call the asset endpoints as fast as it likes. Adding a low, easily-triggered rate limit at the gateway lets us demo backpressure/throttling behavior without touching application code.

## What Changes

- Add Kong's `rate-limiting` plugin to the `assets` route in `kong/kong.yml`, configured for 5 requests per minute, `policy: local`, limited per consumer (keyed on the existing `asset-client` consumer established by `key-auth`), with `hide_client_headers: true` so the plugin adds no headers to any response (see design.md's Decisions for why this default needed overriding).
- **BREAKING**: the `assets` route now rejects a consumer's 6th request within a rolling minute with Kong's own `429 Too Many Requests` response, rather than forwarding it to the app. This is a deliberate policy change to the api-gateway capability, not a bug.
- Amend the `api-gateway` spec's "No Traffic Policies Applied at the Gateway" requirement: today it explicitly forbids rate limiting, naming only `correlation-id` and `key-auth` as allowed plugin exceptions. This proposal adds `rate-limiting` as a third named exception, scoped to the asset routes only (`/health` is unaffected).
- `/health` keeps no rate limit — it carries no consumer identity (no `key-auth`), so a per-consumer limiter has nothing to key on.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `api-gateway`: the "No Traffic Policies Applied at the Gateway" requirement changes from forbidding rate limiting outright to permitting `rate-limiting`, applied only to the asset routes, per-consumer, 5 requests/minute, `local` policy. A new requirement documents the rate-limit behavior itself (limit, scope, and the 429 response).

## Impact

- **Config**: `kong/kong.yml` — new `plugins` entry under the `assets` route.
- **Spec**: `openspec/specs/api-gateway/spec.md` — requirement text and scenarios updated.
- **Runtime**: no app code changes; Kong (DB-less mode) picks up the new plugin config on stack restart. `policy: local` means the counter is per Kong node process memory (no Redis) — acceptable for this single-node dev/demo stack, but the limit is not shared/durable across gateway restarts or multiple Kong instances.
- **Client-visible**: asset API consumers can now receive a Kong-originated 429 response; per the existing `api-error-responses` spec, gateway-rejected responses are explicitly out of scope for the app's standard error envelope, so no error-shape work is needed.

## Non-goals

- No rate limiting on `/health`.
- No global (route-independent) rate limiting.
- No durable/shared rate-limit state across multiple Kong nodes (`policy: redis` or similar) — out of scope for this single-node demo stack.
- No app-level rate limiting or quota management — this is a gateway-only concern.
