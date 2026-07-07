## 1. API Layer

- [x] 1.1 Implement `GET /assets/{id}` handler in `AssetController`: look up the asset via `assetRepository.findById(id)`, return it with a 200 status if present, or throw `ResponseStatusException(HttpStatus.NOT_FOUND, ...)` if absent

## 2. Tests

- [x] 2.1 Add test: `GET /assets/{id}` for an existing asset returns 200 with the asset body
- [x] 2.2 Add test: `GET /assets/{id}` for a non-existent id returns 404
