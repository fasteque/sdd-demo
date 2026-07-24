## Why

Code review on the tags-blank-value validation fix (`POST /assets`) found that error responses in this API carry no message body at all — a client gets a bare `400` with no indication of what was wrong, because Spring Boot's default error handling doesn't serialize the `ResponseStatusException` reason unless explicitly configured. Checking the platform-wide `sdd-demo-api-contracts` store surfaced an existing, already-defined convention (`api-error-responses`) that this API does not yet follow: every 4xx/5xx response must return a structured `error` object (`code`, `message`, `traceId`, plus `details` for validation failures). Adopting it now both fixes the immediate gap and brings this API in line with the shared platform contract before more endpoints are added.

## What Changes

- Introduce a shared error-response mechanism (a Spring `@RestControllerAdvice`) that maps `ResponseStatusException` and Bean Validation failures to a single JSON error envelope: `{ "error": { "code", "message", "traceId", "details"? } }`.
- Generate a `traceId` per request/error response (no existing tracing/correlation-id infrastructure in this repo; a lightweight UUID-per-error approach is proposed — see `design.md`).
- Assign stable, SCREAMING_SNAKE_CASE `error.code` values to each existing error condition in this API (e.g. `ASSET_NOT_FOUND`, `MISSING_REQUIRED_FIELD`, `INVALID_TAGS`, `INVALID_PAGE`, `INVALID_SIZE`).
- Update `openapi/openapi.yaml` with reusable error-response schema components (`Error`, `ErrorDetail`) and reference them from every documented 4xx/404 response across `AssetController`'s endpoints, per this repo's OAS-first convention.
- **BREAKING**: every existing 4xx/404 response body shape changes from empty/undocumented to the structured `error` envelope. No known external consumers exist yet (personal sandbox project), so this is a low-risk break, but it is a wire-contract change to already-shipped endpoints.
- Update `docs/tech-stack.md`'s error-response convention line, which currently says "no custom error body shape yet (revisit if inconsistency becomes a problem)" — this change is that revisit.

## Capabilities

### New Capabilities
- `api-error-responses`: adopts the platform-wide error envelope convention (from the `sdd-demo-api-contracts` store) as a local capability of this API, covering the shared error-response mechanism, `traceId` generation, and per-condition `error.code` assignment.

### Modified Capabilities
- `asset-management`: every existing error scenario (missing required field, asset not found on get/delete, invalid page, invalid size) changes from "returns a 4xx/404 status" to "returns a 4xx/404 status with the structured error envelope body."

## Impact

- **Code**: new `@RestControllerAdvice` (or equivalent) exception-handling class; `AssetController.kt`'s existing `ResponseStatusException` call sites gain stable error codes; no changes to persistence or business logic.
- **API contract**: `openapi/openapi.yaml` gains `Error`/`ErrorDetail` schema components and updated response definitions for all documented 4xx/404 responses; regenerated server models/interfaces follow.
- **Tests**: existing tests asserting only `status()` continue to pass; new/updated tests assert the error envelope shape (`error.code`, `error.message`, `error.traceId` presence, `error.details` for the tags/field-validation cases).
- **Dependencies**: none — implemented with Spring MVC exception handling (already an approved dependency) and `java.util.UUID` (JDK stdlib). No new dependency requires approval.
- **Docs**: `docs/tech-stack.md`'s error-response line is updated to reflect the new standard.

## Non-goals

- No new tracing/correlation-ID infrastructure (e.g. Micrometer Tracing, Sleuth) — `traceId` generation is a minimal, self-contained mechanism scoped to this change, not a full distributed-tracing rollout.
- No adoption of the other two conventions surfaced by the same platform store (`api-naming-conventions`, `api-pagination`) — out of scope for this change; each would need its own proposal if adopted later.
- No change to the `health-check` capability — `GET /health` has no error paths today, so it is unaffected.
- No retroactive backfill of `traceId` correlation across logs/observability tooling — this repo has no centralized logging pipeline to correlate against yet.
