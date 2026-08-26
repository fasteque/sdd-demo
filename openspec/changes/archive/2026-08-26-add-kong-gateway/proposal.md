## Why

The POC currently stops at "browser/client → Spring Boot app → MongoDB." A real deployment of this kind of service normally sits behind an API gateway, so the POC is missing a step that matters for demonstrating a realistic path. Adding Kong Gateway OSS in front of the app, in DB-less mode with a version-controlled declarative config, completes the intended stack for this learning sandbox: browser/client → Kong Gateway → Spring Boot app → MongoDB.

## What Changes

- Add a `kong` service to `compose.app.yaml`, running Kong Gateway OSS in DB-less (declarative) mode.
- Add a versioned `kong.yml` declarative config file, checked into the repo, defining:
  - Exactly one Kong **service** pointing at the existing `app` service.
  - Exactly one Kong **route** that catches all incoming requests and proxies them to that service (no path-based routing rules beyond a single catch-all route).
  - No plugins configured (auth, rate limiting, logging, etc. are explicitly out of scope for this change).
- Kong becomes the entrypoint exposed on the host port; the app's own port is no longer published directly to the host (only reachable from other containers on the compose network).
- No changes to `compose.yaml` (the standalone MongoDB compose file used outside of `compose.app.yaml`), and no changes to the app's own HTTP contracts (`openapi/openapi.yaml`) — the app's request/response shapes are unaffected, Kong only proxies.

## Capabilities

### New Capabilities
- `api-gateway`: Describes the gateway layer (Kong OSS, DB-less mode) that sits in front of the app and proxies all traffic to it.

### Modified Capabilities
- None. No existing HTTP-facing capability's requirements change — the app's endpoints behave identically, just reached through a proxy hop.

## Non-goals

- No Kong plugins (rate limiting, auth, logging, transformations, etc.) — explicitly deferred to a future change.
- No path-based or multi-service routing — a single catch-all route to a single upstream service.
- No Kong Admin API exposure or database-backed (non-DB-less) mode.
- No changes to the standalone `compose.yaml` file.
- No changes to the app's OpenAPI contract or controller behavior.
- No TLS/HTTPS termination at the gateway — plain HTTP, matching the current POC.

## Impact

- **Affected files**: `compose.app.yaml` (add `kong` service, adjust `app` service's published ports), new `kong.yml` (or similarly named declarative config file) at repo root or alongside the compose file.
- **Affected systems**: local Docker Compose dev stack only. No production deployment exists for this POC.
- **Dependencies**: adds the public `kong:*` (OSS) Docker image to the compose stack. No new Gradle/JVM dependencies — `docs/tech-stack.md`'s approved dependency list is unaffected.
- **Docs**: README instructions for running the app locally will need to mention hitting the app through Kong's port instead of the app's port directly.
