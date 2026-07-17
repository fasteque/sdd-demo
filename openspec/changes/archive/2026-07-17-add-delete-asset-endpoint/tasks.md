## 1. Wire Contract

- [x] 1.1 Add the `DELETE /assets/{id}` operation (`operationId: deleteAsset`) to `openapi/openapi.yaml` (path param `id`, `204` response with no content schema, `404` when not found), following `getAsset`'s existing path-parameter and 404 conventions
- [x] 1.2 Run `./gradlew openApiGenerate` and read the regenerated `AssetsApi` (`build/generated/openapi/.../generated/api/AssetsApi.kt`) to confirm the generated `deleteAsset` signature — the generator emitted `ResponseEntity<Unit>` (Kotlin-idiomatic), not `ResponseEntity<Void>` as anticipated

## 2. Implementation

- [x] 2.1 Implement `override fun deleteAsset(id: String): ResponseEntity<Unit>` in `AssetController`, checking existence via `assetRepository.existsById(id)` and throwing `ResponseStatusException(HttpStatus.NOT_FOUND, "asset not found")` when absent (matching `getAsset`'s existing pattern)
- [x] 2.2 On found, delete via `assetRepository.deleteById(id)` and return `ResponseEntity.noContent().build()` (204, no body)

## 3. Tests

- [x] 3.1 In `AssetControllerTests`, add `MockMvcRequestBuilders.delete` to the existing per-method imports, then add a test for `DELETE /assets/{id}` on an existing asset, asserting 204 and that the asset is no longer retrievable (e.g. `assetRepository.findById(saved.id)` is empty)
- [x] 3.2 Add a test for `DELETE /assets/{id}` on a non-existent id, asserting 404
