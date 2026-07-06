## Why

The service currently exposes only a `/health` endpoint. To start building real functionality, we need a way to create and persist assets (e.g. media or content items) so future features can build on top of stored data.

## What Changes

- Add a `POST /assets` endpoint that accepts a name, type, tags, and status, and persists the asset to MongoDB.
- Add an `Asset` document model with fields: `id`, `name`, `type`, `tags`, `status`.
- Validate required fields (`name`, `type`, `status`) on input and return the created asset with a generated id.

## Capabilities

### New Capabilities
- `asset-management`: Create and store assets with a name, type, tags, and status in MongoDB.

### Modified Capabilities
(none)

## Impact

- New Kotlin classes: `Asset` (document), `AssetController` (REST endpoint), `AssetRepository` (Spring Data MongoDB repository).
- New MongoDB collection: `assets`.
- No existing endpoints or data are affected.

## Non-goals

- No update, delete, or list/search endpoints for assets (create-only for this change).
- No authentication/authorization on the endpoint.
- No tag taxonomy or validation of allowed `type`/`status` values beyond non-empty checks.
