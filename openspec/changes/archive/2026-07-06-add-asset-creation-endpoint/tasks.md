## 1. Domain Model

- [x] 1.1 Create `Asset` data class annotated with `@Document(collection = "assets")`, with fields `id: String?`, `name: String`, `type: String`, `tags: List<String>`, `status: String`
- [x] 1.2 Create `AssetRepository : MongoRepository<Asset, String>`

## 2. API Layer

- [x] 2.1 Create `CreateAssetRequest` data class with `name`, `type`, `tags`, `status` fields
- [x] 2.2 Create `AssetController` with constructor-injected `AssetRepository`
- [x] 2.3 Implement `POST /assets` handler: validate `name`, `type`, `status` are non-blank (400 via `ResponseStatusException` if not), default `tags` to empty list if absent, save the asset, return the saved `Asset` with 201 status

## 3. Tests

- [x] 3.1 Add test: successful asset creation returns 201 with generated id and persists to MongoDB
- [x] 3.2 Add test: missing required field (name, type, or status) returns 400 and does not persist
- [x] 3.3 Add test: omitting tags persists asset with empty tag list
