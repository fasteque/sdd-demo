---
title: OAS-first codegen with openapi-generator (kotlin-spring) - six gotchas that don't show up until runtime or a real regression
date: 2026-07-15
last_updated: 2026-07-17
category: docs/solutions/tooling-decisions
module: "API layer / HTTP controllers (Kotlin/Spring Boot codegen via org.openapi.generator)"
problem_type: tooling_decision
component: tooling
severity: medium
applies_when:
  - "Adopting or maintaining an OpenAPI-spec-first workflow with org.openapi.generator (kotlin-spring generator, interfaceOnly mode) in a Spring Boot + Kotlin codebase"
  - "A controller class implements a generated *Api interface instead of hand-declaring request/response DTOs"
  - "An OAS schema property is optional but not marked nullable, and callers may send an explicit JSON null for it"
  - "An OAS schema declares minLength/minimum/maximum constraints alongside hand-written validation in the controller"
  - "A Spring Data @Document domain entity shares a name with an OAS-generated response/request model in the same codebase"
related_components: [testing_framework, development_workflow]
tags: [openapi, openapi-generator, kotlin-spring, code-generation, spring-validation, component-scanning, import-aliasing, jackson]
---

# OAS-first codegen with openapi-generator (kotlin-spring): six gotchas that don't show up until runtime or a real regression

## Context

`sdd-demo` (Kotlin, Spring Boot 4.1, Gradle, MongoDB) switched its HTTP endpoints to an OpenAPI-spec-first (OAS-first) workflow. `openapi/openapi.yaml` is now the contract of record. The `org.openapi.generator` Gradle plugin (`kotlin-spring` generator, `interfaceOnly: true`, `useSpringBoot4: true`, `useJackson3: true`, `useTags: true`) generates server interfaces and models into `build/generated/openapi` (gitignored, regenerated on every build via `compileKotlin.dependsOn("openApiGenerate")`). Controllers now implement the generated interface instead of hand-declaring request/response DTOs.

The migration was done first as a trial on `GET /health`, then as a full migration of `AssetController` (`POST /assets`, `GET /assets/{id}`, `GET /assets`), bringing all four real endpoints in the repo under OAS coverage. A later change added `DELETE /assets/{id}` the same way, bringing the total to five OAS-covered endpoints and surfacing a sixth gotcha (below) around bodyless-response return types.

Several of the problems below are not visible by reading the OAS spec or the generator's plugin config — they only surface by actually reading the generated Kotlin source, by exercising the endpoint with a real integration test (MockMvc against a real Spring context), or, in one case, by empirically deleting code and rerunning the suite to check whether it was still doing anything. Each is a distinct, durable trap worth checking for on any future OAS-first migration in this codebase.

## Guidance

### Gotcha 1: `@RestController` on the generated interface does NOT make component-scanning find the implementing class

The generated interface (e.g. `HealthApi`, `AssetsApi`) itself carries `@RestController`. openapi-generator added this deliberately (see [openapi-generator#19156](https://github.com/OpenAPITools/openapi-generator/issues/19156), fixed by PR #19158 in the 7.8.0 milestone) to fix handler-mapping detection for beans wrapped in a dynamic proxy (e.g. under `@Transactional` or security AOP) — Spring's `AnnotatedElementUtils`-based handler-type introspection *does* traverse implemented interfaces via reflection.

BUT Spring's component-scanning (the earlier step that decides whether a class becomes a bean *at all*) is ASM-based bytecode metadata reading, which does NOT look at a class's implemented interfaces for stereotype annotations — only annotations declared directly on the class (or inherited from a superclass) count.

Result: a plain `class HealthController : HealthApi { ... }` with no annotation of its own is never registered as a bean, so `/health` 404s — not with an error, but silently falling through to Spring's static-resource handler (`Handler: Type = ResourceHttpRequestHandler`, `Resolved Exception: NoResourceFoundException`). This was caught in this session only because `HealthControllerTests` is a real `@SpringBootTest` + MockMvc integration test; a slice test mocking the handler layer would not have caught it.

Fix — the implementing class must carry its own `@RestController`, redundant with the interface's but necessary for a different reason (bean registration, not handler-mapping):

```kotlin
@RestController
class HealthController : HealthApi {
    override fun getHealth(): ResponseEntity<HealthStatus> = ResponseEntity.ok(HealthStatus(status = "UP"))
}
```

### Gotcha 2: the generator can rename a Kotlin property away from its OAS/JSON name

An OAS schema property whose name collides with something in the Kotlin/generator template gets renamed in the generated Kotlin data class, while the JSON wire name is preserved via a Jackson annotation. Example: `AssetPage`'s `size` property became the Kotlin field `propertySize`:

```kotlin
data class AssetPage(
    ...
    @get:JsonProperty("size", required = true) val propertySize: kotlin.Int,
    ...
)
```

The JSON body still serializes as `"size": 20` — only the Kotlin constructor parameter/property name changed. This is invisible from the OAS spec itself and only discoverable by actually reading the generated file (`build/generated/openapi/src/main/kotlin/.../AssetPage.kt`) after running `./gradlew openApiGenerate`, before writing the controller code that constructs it.

### Gotcha 3: an optional-but-non-nullable OAS property rejects explicit JSON `null` (a real regression found in this session)

An OAS property that is optional (absent from `required:`) but does NOT have `nullable: true` gets `@field:JsonSetter(nulls = Nulls.FAIL)` from the kotlin-spring generator. This means: omitting the key entirely works fine, but sending the key with an explicit JSON `null` value throws a deserialization error (400) — even though the Kotlin type itself is nullable (`val tags: List<String>? = null`).

Before migration, the hand-written DTO (`val tags: List<String>? = null`, no special Jackson annotation) accepted `"tags": null` and the controller normalized it: `tags = request.tags ?: emptyList()`. After migrating to the generated `CreateAssetRequest` without `nullable: true` on `tags`, the exact same request started returning 400 — a silent regression, since no existing test sent an explicit JSON `null` (only omitted-key and populated-array cases were tested).

Fix: add `nullable: true` to any optional property that a client might legitimately send as explicit `null`:

```yaml
properties:
  tags:
    type: array
    nullable: true   # <-- without this, explicit "tags": null now 400s
    items:
      type: string
```

After regenerating, the `@JsonSetter(nulls = Nulls.FAIL)` annotation disappears and the property tolerates explicit null again.

### Gotcha 4: generated Bean Validation can make hand-written checks unreachable dead code

If the OAS schema declares `minLength: 1` on a body field, or `minimum`/`maximum` on a query parameter, kotlin-spring emits `@Size(min=1)` / `@Min(n)` constraint annotations on the generated model/interface, and the generated interface is `@Validated` with `@Valid` on the request body parameter. Spring's own validation (`MethodArgumentNotValidException` for `@Valid` body fields, method-parameter validation for `@Min`/`@Max`) then fires and returns 400 *before* the controller method body executes at all.

A controller carried over from before the OAS migration, with equivalent hand-written guard clauses (`if (page < 0) throw ResponseStatusException(BAD_REQUEST, "page must be non-negative")`, blank-string checks via `.takeIf { it.isNotBlank() }`), will have those checks silently become dead code — same observable behavior (400), so nothing *looks* wrong, but the code is not doing what it appears to do.

This was confirmed empirically in this session, not just reasoned about: a reviewer temporarily deleted the manual checks from `AssetController.kt`, ran the full test suite (including the specific rejection-path tests: `rejects negative page`, `rejects size less than 1`, `rejects request missing required field`) against a real MongoDB instance, confirmed zero change in outcome, then reverted to continue the review. The dead checks were then removed for real:

```kotlin
// Before (dead code after OAS migration -- @Size(min=1)/@Valid already reject blank/missing before this runs):
override fun createAsset(createAssetRequest: CreateAssetRequest): ResponseEntity<AssetResponse> {
    val name = createAssetRequest.name.takeIf { it.isNotBlank() }
        ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required")
    // ...
}

// After (trust the generated validation, since it demonstrably runs first):
override fun createAsset(createAssetRequest: CreateAssetRequest): ResponseEntity<AssetResponse> {
    val asset = Asset(name = createAssetRequest.name, /* ... */)
    val saved = assetRepository.save(asset)
    return ResponseEntity.status(HttpStatus.CREATED).body(saved.toResponse())
}
```

Caveat worth naming: removing the checks changes the error RESPONSE BODY shape (from a custom reason message via `ResponseStatusException` to Spring's default validation-error body), even though the status code (400) stays the same — confirm no test or consumer depends on the specific error message text before deleting the "dead" code.

### Gotcha 5: domain model and generated API model name collisions need import aliasing

When a persistence domain type (e.g. a Spring Data `@Document`) and its OAS-generated response schema share the same simple name (`Asset` in both cases), and the controller lives in the same package as the domain type (so it resolves unqualified, no import needed), an explicit import of the generated type with the same simple name is a compile error unless aliased:

```kotlin
package ch.fasteque.sdd_demo   // domain Asset also lives here, resolves unqualified

import ch.fasteque.sdd_demo.generated.model.Asset as AssetResponse  // <-- alias required

private fun Asset.toResponse(): AssetResponse =
    AssetResponse(id = checkNotNull(id) { "..." }, name = name, type = type, tags = tags, status = status)
```

The mapping function was kept in the controller file itself (not split into a dedicated mapper class) since there was only one implementor of this pattern at the time — a deliberate "avoid premature abstraction" call, worth revisiting if a third OAS-migrated controller repeats the same shape.

### Gotcha 6: a bodyless 204 response generates `ResponseEntity<Unit>`, not `ResponseEntity<Void>`

For a path operation whose 2xx response declares no `content:` schema (e.g. a `204` with only a `description`, no response body), the kotlin-spring generator emits `ResponseEntity<Unit>` as the interface method's return type — not `ResponseEntity<Void>`, which is the natural assumption to carry over from plain Java/Spring MVC conventions (where a no-body response is typically typed `ResponseEntity<Void>`).

Confirmed by adding `DELETE /assets/{id}` (204 on success, no `content:` schema; 404 when not found) to `openapi/openapi.yaml`, running `./gradlew openApiGenerate`, and reading the generated interface directly instead of assuming:

```kotlin
// build/generated/openapi/src/main/kotlin/.../generated/api/AssetsApi.kt
fun deleteAsset(
    @PathVariable("id") id: kotlin.String
): ResponseEntity<Unit> {
    return ResponseEntity(HttpStatus.NOT_IMPLEMENTED)
}
```

The controller override must match this signature exactly:

```kotlin
override fun deleteAsset(id: String): ResponseEntity<Unit> {
    if (!assetRepository.existsById(id)) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, "asset not found")
    }
    assetRepository.deleteById(id)
    return ResponseEntity.noContent().build()
}
```

Unlike Gotcha 3 (a silent runtime regression), guessing `ResponseEntity<Void>` here fails loudly: a Kotlin `override` must match the interface's declared return type exactly, so the wrong guess is a compile error, not a passing-but-wrong test. `ResponseEntity.noContent().build()` itself would type-check against either `Unit` or `Void` — it's the override's declared return type that catches the mismatch. Still costs a compile-error detour if the generated interface isn't read first, same as Gotcha 2's field-rename trap.

## Why This Matters

Each gotcha fails in a way that looks like success until you look closely:

- Gotcha 1 fails as a silent 404 through the static-resource handler, not a wiring exception — easy to misdiagnose as "wrong URL" rather than "bean never registered." Only a real `@SpringBootTest` + MockMvc test catches it; a controller unit test that instantiates the class directly would never see the missing component-scan registration.
- Gotcha 2 fails at compile time if missed (constructor call with wrong property name), which is at least loud — but only after time is lost hunting for a `size` property that "should" exist.
- Gotcha 3 is the most dangerous: it is a runtime behavior regression with an identical-looking schema, invisible unless a test exercises the exact explicit-`null` case, and it silently narrows the API contract for existing clients that relied on sending `null`.
- Gotcha 4 doesn't break anything today, but leaves misleading code in the repository: a future maintainer who reads the guard clause will believe the controller is responsible for validation, and may either duplicate the check elsewhere or trust it in a place where the generated validation does NOT apply (e.g. a code path that bypasses the generated interface).
- Gotcha 5 is a plain compile error, but confusing the first time it's hit — the fix (import aliasing) isn't obvious to anyone unfamiliar with the pattern, and the decision of where mapping code lives compounds across every future OAS-migrated controller.
- Gotcha 6 is also a plain compile error (loud, unlike Gotcha 3), but the assumption it corrects — "no response body means `ResponseEntity<Void>`" — is a reasonable one carried over from plain Spring MVC, and this generator doesn't follow it. Worth a few seconds reading the generated interface to avoid the detour.

Because `build/generated/openapi` is gitignored and regenerated on every build, none of this is visible from a `git diff` or a code review of committed source alone — the generated code has to be inspected directly, and the runtime behavior has to be tested, to catch these classes of issues.

## When to Apply

- Any time a new controller or endpoint is migrated from a hand-written DTO/controller pattern to an OAS-first `org.openapi.generator` (kotlin-spring) generated interface.
- Any time an OAS schema is authored or edited for a property that is optional and could plausibly be sent by a client as explicit JSON `null` (arrays, nullable objects, optional strings).
- Any time a controller carries hand-written validation (guard clauses, `ResponseStatusException` for bad input) alongside an OAS schema with `minLength`/`minimum`/`maximum`/`required` constraints — check whether the manual check is now unreachable.
- Any time a generated model's Kotlin property name looks unexpected relative to the OAS field name — check the generated source file directly rather than assuming a 1:1 name mapping.
- Any time a domain/persistence type and a generated API model share a simple name in the same package — decide up front whether to alias the import or rename one of the types, and note whether extracting a dedicated mapper is warranted once more than one controller repeats the shape.
- After running `./gradlew openApiGenerate`, before writing controller code against the generated types — read the generated model/interface source at least once for the endpoint being implemented.
- Any time an OAS operation declares a 2xx response with no `content:` schema (a `204`, or a `200`/`201` with an empty body) under this generator config — read the generated interface's return type directly rather than assuming `ResponseEntity<Void>`.

## Examples

See the six inline before/after and annotated code snippets under Guidance above:
1. `HealthController` needing its own `@RestController` despite `HealthApi` already declaring one.
2. `AssetPage.propertySize` vs JSON `"size"`.
3. `tags: nullable: true` fix for `CreateAssetRequest` in `openapi/openapi.yaml`.
4. `AssetController.createAsset` before/after removing dead manual validation.
5. `import ch.fasteque.sdd_demo.generated.model.Asset as AssetResponse` aliasing in the `Asset` domain package.
6. `AssetsApi.deleteAsset` generating `ResponseEntity<Unit>` for a schema-less `204` response, confirmed by reading `build/generated/openapi/src/main/kotlin/ch/fasteque/sdd_demo/generated/api/AssetsApi.kt` directly.

## Related

- `docs/residual-review-findings/c158db1.md` — the origin of the `AssetPage.size` field that this migration's codegen later renamed to `propertySize` (Gotcha 2). That record's two accepted trade-offs (unbounded `size`, `AssetPage` vs. `PagedModel<T>`) were revisited and reaffirmed, not changed, during this migration.
