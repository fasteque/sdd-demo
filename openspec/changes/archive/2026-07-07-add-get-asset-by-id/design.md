## Context

The `add-asset-creation-endpoint` change established `Asset`, `AssetRepository`, and `AssetController` with a `POST /assets` handler. This change adds a read path on the same controller/repository, following the pattern already in place.

## Goals / Non-Goals

**Goals:**
- Provide `GET /assets/{id}` returning the matching asset as JSON with a 200 status.
- Return a 404 when no asset exists for the given id.

**Non-Goals:**
- No listing/searching/pagination of assets.
- No changes to the `Asset` data model or the creation endpoint.

## Decisions

- **Repository access**: reuse `AssetRepository.findById(id)` (inherited from `MongoRepository`), which already returns an `Optional<Asset>`. No new repository method needed.
- **Controller**: add a `GET /assets/{id}` handler to the existing `AssetController`, consistent with keeping validation/lookup logic inline in the controller (no service layer), matching the precedent set by `createAsset`.
- **Not-found handling**: `findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "asset not found") }`, mirroring the existing use of `ResponseStatusException` for 4xx errors in `createAsset`.
- **Response shape**: return the `Asset` document directly (same as the creation endpoint's response), no separate response DTO.

## Risks / Trade-offs

- [No distinction between "invalid id format" and "id not found"] → MongoDB's `findById` with a malformed ObjectId-like string simply won't match, so it naturally falls into the 404 path; acceptable since ids are plain `String` (not enum/format constrained).
