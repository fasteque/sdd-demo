## Context

The asset-management capability already exposes `POST /assets`, `GET /assets`, and `GET /assets/{id}` (see `openapi/openapi.yaml`). `GET /assets/{id}` already establishes the found/not-found (200/404) pattern for a single asset by id — this change reuses that pattern for delete rather than introducing a new one.

## Goals / Non-Goals

**Goals:**
- Add `DELETE /assets/{id}` to `openapi/openapi.yaml`, following the existing path-parameter and 404 conventions already used by `getAsset`
- Implement the generated interface in the existing asset controller, delegating to a new delete-by-id repository operation

**Non-Goals:**
- No new architectural pattern, dependency, or data model change — this is a small, additive endpoint on an existing resource
- No soft-delete, cascading delete, or authorization (see proposal's Non-goals)

## Decisions

- **Response shape: 204 No Content on success.** Consistent with standard REST delete semantics; there's no existing delete endpoint in this repo to match against, and the created/retrieved Asset body isn't needed once the resource is gone.
- **404 on missing id, reusing `getAsset`'s pattern.** `GET /assets/{id}` already returns 404 via `ResponseStatusException` when the id doesn't exist (per `docs/tech-stack.md`'s error-response convention). The delete handler checks existence the same way before deleting, so behavior stays consistent across both by-id endpoints rather than introducing a different error shape.
- **No request/response body.** `DELETE /assets/{id}` takes only the path `id`; no request body, and no response schema is needed for a 204.

## Risks / Trade-offs

- [Deleting a non-existent id could be treated as idempotent-success (204) instead of 404] → Mitigated by matching `getAsset`'s existing 404 convention for by-id lookups, keeping both endpoints consistent rather than introducing a new semantics.
