## Context

The project has no endpoint to confirm the service process is up. Existing endpoints (e.g. `asset-management`) are domain controllers backed by MongoDB; this is the first endpoint with no persistence or domain model involved.

## Goals / Non-Goals

**Goals:**
- Provide a trivial `GET /health` endpoint returning a 200 with a small JSON body indicating the service is running.

**Non-Goals:**
- Checking MongoDB connectivity or any other dependency (no readiness semantics).
- Integrating with an external monitoring/alerting system.

## Decisions

- Implement as a small standalone Kotlin controller, following the same constructor-injection, idiomatic-Kotlin style used by the existing `asset-management` controller — no new pattern introduced since there's nothing to inject (no dependencies needed for this endpoint).
- Response body is a minimal fixed shape, e.g. `{"status": "UP"}`, consistent with the project's "no custom error body shape yet" stance — this is a success-only endpoint so no error format decision is needed here.
- No deviation from prior decisions in `openspec/specs/` — this capability doesn't touch the asset domain model or persistence layer at all.

## Risks / Trade-offs

- [Risk] A caller may mistake this for a full readiness probe (assuming DB is also healthy) → Mitigation: Non-Goals section in proposal explicitly scopes it to liveness only; naming (`/health`, not `/ready`) reinforces this.
