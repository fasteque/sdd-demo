## Why

Assets can currently be created, retrieved, and listed, but never removed. Without a delete endpoint, callers have no way to clean up test data or retire assets that are no longer valid, and the asset lifecycle is incomplete.

## What Changes

- Add a `DELETE /assets/{id}` endpoint that removes the asset matching the given id
- Return a 404 when the id does not match any asset
- Return a 204 (no body) on successful deletion

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `asset-management`: adds a "Delete Asset by Id" requirement covering the new `DELETE /assets/{id}` endpoint and its found/not-found behavior

## Non-goals

- No bulk/batch delete (deleting multiple assets in one request)
- No soft-delete or archival — deletion is permanent removal from MongoDB
- No cascading deletes or related-resource cleanup (no related resources exist today)
- No authorization/permission checks — out of scope for this sandbox project

## Impact

- `openapi/openapi.yaml`: new `DELETE /assets/{id}` operation
- `AssetController` (or equivalent controller class): new handler implementing the generated delete interface
- Asset repository/service layer: new delete-by-id operation
- Tests: new MockMvc test coverage for found/not-found delete scenarios
