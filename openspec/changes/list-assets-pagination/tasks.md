## 1. Response Model

- [x] 1.1 In `AssetController.kt`, add `AssetPage` data class (`content: List<Asset>`, `page: Int`, `size: Int`, `totalElements: Long`, `totalPages: Int`), placed alongside `CreateAssetRequest` — same file, same precedent for controller-local DTOs

## 2. API Layer

- [x] 2.1 In `AssetController.kt`, add a `GET /assets` handler bound with `@RequestParam(defaultValue = "0") page: Int` and `@RequestParam(defaultValue = "20") size: Int`
- [x] 2.2 Validate `page >= 0` and `size >= 1` at the top of the handler; throw `ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be non-negative")` / `"size must be positive"` otherwise, before calling the repository
- [x] 2.3 Call `assetRepository.findAll(PageRequest.of(page, size))` (import `org.springframework.data.domain.PageRequest`), then construct `AssetPage` from the returned `Page<Asset>`: `content = result.content`, `page = result.number`, `size = result.size`, `totalElements = result.totalElements`, `totalPages = result.totalPages`

## 3. Tests

- [x] 3.1 In `src/test/kotlin/ch/fasteque/sdd_demo/AssetControllerTests.kt`, add test: `GET /assets` without query parameters returns 200 with default pagination (page 0, size 20) — seed a couple of assets first via `assetRepository.save`
- [x] 3.2 Add test: seed 6+ assets, `GET /assets?page=1&size=5` returns the second page (1 remaining item) with `page=1`, `size=5`, and accurate `totalElements`/`totalPages`
- [x] 3.3 Add test: with 0 or few assets seeded, `GET /assets?page=5` (beyond available data) returns 200 with an empty `content` list and accurate metadata
- [x] 3.4 Add test: `GET /assets?page=-1` returns 400
- [x] 3.5 Add test: `GET /assets?size=0` returns 400
