## Context

`AssetRepository` already extends `MongoRepository<Asset, String>`, which in turn extends `PagingAndSortingRepository`, so `findAll(Pageable): Page<Asset>` is available with no repository changes. The controller currently exposes `POST /assets` and `GET /assets/{id}` directly on `Asset`, with validation and not-found handling done inline (no service layer). This change follows the same pattern for a new `GET /assets` handler.

## Goals / Non-Goals

**Goals:**
- Provide `GET /assets` returning a page of assets as JSON with a 200 status.
- Accept `page` and `size` query parameters with defaults when omitted.
- Return pagination metadata (page, size, total elements, total pages) alongside the content.
- Reject negative `page` or non-positive `size` with a 400 status.

**Non-Goals:**
- No filtering, searching, or custom sorting.
- No changes to `Asset`, `AssetRepository`'s existing methods, or the other two endpoints.

## Decisions

- **Repository access**: use `assetRepository.findAll(PageRequest.of(page, size))`, inherited from `MongoRepository` — no new repository method needed, consistent with reusing `findById` in the previous change.
- **Query parameters**: `page` (default `0`) and `size` (default `20`), bound via `@RequestParam` with defaults on the controller method. Validate `page >= 0` and `size >= 1`, throwing `ResponseStatusException(HttpStatus.BAD_REQUEST, ...)` otherwise — mirrors the existing validation style in `createAsset`.
- **Response shape**: introduce a small `AssetPage` response data class (`content: List<Asset>`, `page: Int`, `size: Int`, `totalElements: Long`, `totalPages: Int`) built from the returned `Page<Asset>`, rather than returning Spring Data's `Page`/`PageImpl` directly. Spring Boot warns that serializing `PageImpl` as-is is unstable across versions and recommends an explicit DTO; a plain data class keeps the JSON contract stable and dependency-free without adding a service layer.
- **Ordering**: no explicit sort is specified, so `findAll(Pageable)` uses natural MongoDB document order (effectively insertion order for this collection), matching the proposal's "ordered by insertion order" statement.

## Risks / Trade-offs

- [No upper bound on `size`] → a client could request a very large page (e.g. `size=1000000`) and load the whole collection into memory. Acceptable for this learning sandbox's scale; revisit with a max-size cap if this becomes a real concern.
- [New response DTO diverges from returning domain objects directly, unlike the other two endpoints] → necessary because `Asset` alone can't carry pagination metadata; kept as a single flat data class rather than a generic wrapper to avoid over-engineering.
