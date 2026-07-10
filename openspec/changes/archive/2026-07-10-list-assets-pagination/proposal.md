## Why

Assets can currently only be created or fetched one at a time by id. Clients have no way to browse the full set of assets, so a paginated list endpoint is needed to support basic asset discovery.

## What Changes

- Add a `GET /assets` endpoint that returns a page of assets, ordered by insertion order.
- Support `page` and `size` query parameters to control pagination, with sensible defaults when omitted.
- Return pagination metadata (current page, page size, total elements, total pages) alongside the asset list.
- Reject invalid `page` or `size` values (negative page, non-positive size) with a 400 status.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `asset-management`: Adds a "List Assets" requirement — `GET /assets` returns a paginated page of assets with a 200 status, supporting `page` and `size` query parameters and rejecting invalid values with a 400 status.

## Impact

- `AssetController`: add a new `GET /assets` handler.
- `AssetRepository`: reuse existing `findAll(Pageable)` inherited from `MongoRepository`, no changes needed.
- New response type to carry the page of assets plus pagination metadata (no changes to the `Asset` data model itself).

## Non-goals

- No filtering, searching, or sorting by fields other than the default order.
- No changes to the asset creation or single-asset retrieval endpoints or the `Asset` data model.
- No authentication or authorization.
