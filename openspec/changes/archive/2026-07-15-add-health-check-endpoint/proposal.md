## Why

There is no lightweight endpoint to confirm the service is up and responding, which makes it harder to verify a deployment or local run without exercising a real business endpoint.

## What Changes

- Add a `GET /health` endpoint that returns a simple status payload (no auth, no dependencies checked).

## Capabilities

### New Capabilities
- `health-check`: exposes a `GET /health` endpoint returning service status for basic liveness verification.

### Modified Capabilities
(none)

## Non-goals

- Not a full readiness probe (no DB connectivity check, no dependency health aggregation).
- Not wired into any monitoring/alerting system as part of this change.

## Impact

- New controller (and small route) under the existing Spring Boot app; no changes to existing endpoints, data model, or dependencies.
