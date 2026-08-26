## Why

Requests currently have no correlation identifier attached at the gateway, making it hard to trace a single request across gateway logs, app logs, and client-side reports. Adding Kong's `correlation-id` plugin gives every request passing through the gateway a stable, traceable ID with minimal effort.

## What Changes

- Add the `correlation-id` Kong plugin to `kong/kong.yml`, applied **globally** (not scoped to the `app` service or its route).
- Header name: `Request-Id` (Train-Case, no vendor prefix — see Impact below for why this differs from the originally requested `Kong-Request-ID`).
- `echo_downstream: true`, so the same header/value is also reflected back on the response to the client.
- **BREAKING**: removes the existing "No Traffic Policies Applied at the Gateway" constraint in the `api-gateway` capability — this was a deliberate blanket ban on Kong plugins that this change intentionally narrows to permit the correlation-id plugin specifically. Traffic policies (auth, rate limiting, transformation, etc.) beyond correlation ID remain out of scope for this change.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `api-gateway`: replaces the blanket "no Kong plugins" requirement with one that permits the correlation-id plugin (global scope, `Request-Id` header, generated ID echoed back to the client) while still prohibiting any other plugin.

## Impact

- **Affected file**: `kong/kong.yml` (add a top-level `plugins` entry, not attached to the `app` service, so it applies globally).
- **Header naming deviation from request**: the user originally asked for header name `Kong-Request-ID`. The `sdd-demo-api-contracts` store's `api-naming-conventions` spec requires platform-wide cross-cutting headers to use Train-Case with no service-specific prefix (its own example is `Request-Id`). `Kong-Request-ID` violates both the prefix rule and the casing rule. Per user decision, this proposal uses `Request-Id` instead to comply with the platform convention.
- **No app code changes**: the plugin operates entirely at the gateway; the app is not required to read or set this header (Kong generates it if absent).
- **No new dependencies**: Kong's `correlation-id` plugin ships with Kong Gateway OSS; no build.gradle.kts or tech-stack changes needed.

## Non-goals

- No other Kong plugins (auth, rate limiting, request/response transformation, logging) are introduced by this change — the "no other plugins" constraint is preserved, only narrowed for correlation-id.
- No change to how the app itself logs or propagates request IDs internally.
- No change to the gateway's routing, service, or port configuration.
