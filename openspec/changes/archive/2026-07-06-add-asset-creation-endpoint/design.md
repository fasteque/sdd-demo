## Context

The project is a minimal Spring Boot + Kotlin + MongoDB skeleton with only a `/health` endpoint. This change adds the first real domain model (`Asset`) and its creation endpoint, establishing the pattern for controller/repository/document layering that later changes can follow.

## Goals / Non-Goals

**Goals:**
- Provide a simple, idiomatic Kotlin REST endpoint to create an asset.
- Persist assets in MongoDB using Spring Data MongoDB's repository abstraction.
- Return a clear 201/400 response contract.

**Non-Goals:**
- No read/update/delete endpoints (covered by future changes).
- No authentication, pagination, or filtering.
- No enum/constrained values for `type` or `status` — plain strings for now.

## Decisions

- **Document model**: `Asset` as a Kotlin `data class` annotated with `@Document(collection = "assets")`, using `@Id` for the id field (`String?`, populated by MongoDB on insert). Rationale: idiomatic Spring Data MongoDB pattern, minimal boilerplate.
- **Repository**: `AssetRepository : MongoRepository<Asset, String>`. Rationale: covers `save()` with zero custom code; no query methods are needed yet.
- **Controller**: `AssetController` with constructor-injected `AssetRepository`, exposing `POST /assets` that accepts a request DTO, validates required fields (`name`, `type`, `status` non-blank), maps to `Asset`, saves it, and returns 201 with the saved asset. Rationale: keeping validation inline in the controller avoids introducing a service layer that isn't needed yet, consistent with "no unnecessary abstractions."
- **Validation approach**: Manual checks in the controller (return 400 via `ResponseStatusException` on blank required fields) rather than pulling in `spring-boot-starter-validation`, since that dependency isn't in `build.gradle.kts` and the validation need is minimal. Alternative considered: bean validation annotations (`@NotBlank`) — rejected for now to avoid adding a new dependency for three field checks; revisit if validation needs grow.
- **Request/response shape**: Reuse the `Asset` document shape directly for the response; use a small separate request DTO (`CreateAssetRequest`) for the incoming JSON so the id field is never client-settable.

## Risks / Trade-offs

- [No validation dependency] → Acceptable for now since checks are simple; revisit if more complex validation is needed later.
- [No service layer] → Controller talks directly to the repository; acceptable at this scale, but if business logic grows, extract a service.
