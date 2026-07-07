## Why

Assets can currently only be created, not retrieved. Clients need a way to fetch a single asset by its id (e.g., to confirm creation or display details), so a read endpoint is the natural next capability to add.

## What Changes

- Add a `GET /assets/{id}` endpoint that returns the asset matching the given id.
- Return a 404 response when no asset exists with the given id.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `asset-management`: Adds a "Retrieve Asset by Id" requirement — `GET /assets/{id}` returns the matching asset with a 200 status, or a 404 status when the id doesn't exist.

## Impact

- `AssetController`: add a new `GET /assets/{id}` handler.
- `AssetRepository`: reuse existing `findById` from `MongoRepository`, no changes needed.
- No changes to the `Asset` data model.

## Non-goals

- No listing/searching/pagination of assets.
- No changes to the asset creation endpoint or data model.
- No authentication or authorization.
