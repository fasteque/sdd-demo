## Why

The asset endpoints (`/assets`, `/assets/{id}`) are currently reachable by anyone who can reach the gateway, with no authentication of any kind. Before this API is exposed beyond a trusted local dev loop, it needs a basic access control gate. API key authentication enforced at the Kong gateway is the smallest change that closes this gap without touching application code, and it keeps `/health` open so uptime checks and container healthchecks keep working without credentials.

## What Changes

- Add Kong's `key-auth` plugin, scoped only to the asset routes (`/assets`, `/assets/{id}`) — **BREAKING** for any existing caller of those routes without a key.
- Split the current single catch-all Kong route into two: an unauthenticated `/health` route and an authenticated `assets` route covering `/assets` and `/assets/{id}`.
- Register one Kong consumer with one API key. The key value is never committed: `kong/kong.yml` references it via Kong's declarative-config environment variable substitution, and the real value lives in a new, gitignored `.env` file consumed by `compose.app.yaml`'s `kong` service.
- Amend the `api-gateway` spec's existing "No Traffic Policies Applied at the Gateway" requirement, which currently forbids all Kong plugins except `correlation-id`, to permit `key-auth` scoped to the asset routes.
- Clarify the `api-error-responses` spec's scope: it governs responses the app itself produces, not a 401 the gateway returns when it rejects a request before the app ever sees it. Kong's default 401 body (not the platform error envelope) is accepted for gateway-rejected requests — this is a deliberate, documented deviation, not an oversight.

## Non-goals

- No key rotation, expiry, or multi-tenant/multi-key support — one static key for now.
- No app-side (Spring) authentication or authorization — this is gateway-only.
- No rewriting of Kong's 401 response body to match the platform error envelope.
- No changes to `/health`'s behavior or response shape.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `api-gateway`: the "No Traffic Policies Applied at the Gateway" requirement changes from forbidding all auth plugins to permitting `key-auth` scoped to the asset routes only; `/health` remains proxy-only with no auth.
- `api-error-responses`: scope clarified to app-originated responses; gateway-rejected requests (which never reach the app) are explicitly out of scope and are not required to use the standard error envelope.

## Impact

- `kong/kong.yml`: add `key-auth` plugin, a consumer + credential, and split the catch-all route into an open `/health` route and an authenticated asset route.
- `compose.app.yaml`: wire the `kong` service to a new gitignored `.env` file supplying the real API key value.
- `.gitignore`: add the new `.env` file.
- `openspec/specs/api-gateway/spec.md`, `openspec/specs/api-error-responses/spec.md`: updated per the delta specs.
- No application (Kotlin/Spring) code changes.
