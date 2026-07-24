## 1. OpenAPI contract

- [x] 1.1 Add `Error` and `ErrorDetail` schema components to `openapi/openapi.yaml` (`Error`: `code`, `message`, `traceId` required strings, `details` optional array of `ErrorDetail`; `ErrorDetail`: `field`, `code`, `message` required strings)
- [x] 1.2 Add a `400` response (schema: `Error`) to `POST /assets` and `GET /assets`, replacing the current description-only `400` entries
- [x] 1.3 Add a `404` response (schema: `Error`) to `GET /assets/{id}` and `DELETE /assets/{id}`, replacing the current description-only `404` entries
- [x] 1.4 Run `./gradlew openApiGenerate` and read the regenerated `Error`/`ErrorDetail` model classes directly to confirm the generated shape matches what the handler in section 3 will construct

## 2. Exception types (confirmed during planning)

Resolved empirically against a running instance (trace logs), not left as an implementation-time guess — see design.md's Decisions section for the full evidence:

- `ResponseStatusException` — hand-thrown 404s (`getAsset`/`deleteAsset`).
- `jakarta.validation.ConstraintViolationException` — `page`/`size` query-parameter `@Min` violations. **Not** `HandlerMethodValidationException`.
- `org.springframework.web.bind.MethodArgumentNotValidException` — a request field is present but fails Bean Validation (e.g. `"name": ""`).
- `org.springframework.http.converter.HttpMessageNotReadableException` — a required field is entirely absent from the JSON body (Jackson fails to construct `CreateAssetRequest` before `@Valid` runs). This is the path the *existing* `rejects request missing required field` test actually exercises.
- The generated `build/generated/openapi/.../generated/api/Exceptions.kt`'s `DefaultExceptionHandler` already has an unordered `@ExceptionHandler(ConstraintViolationException::class)` — the new advice must carry a higher-precedence `@Order` or it may not win.

- [x] 2.1 After Task 1.4's regeneration, re-read `Exceptions.kt` to confirm `DefaultExceptionHandler`'s shape is unchanged from the above (it's gitignored/regenerated, so re-verify rather than assume)

## 3. Shared error-handling mechanism

- [x] 3.1 Add `RequestValidationException(val errors: List<FieldValidationError>) : RuntimeException()` and a `FieldValidationError(field: String, code: String, message: String)` data class (plain Kotlin, no new dependency)
- [x] 3.2 Add a `@RestControllerAdvice` class, annotated `@Order(Ordered.HIGHEST_PRECEDENCE)` so it resolves `ConstraintViolationException` ahead of the generated `DefaultExceptionHandler`, with `@ExceptionHandler` methods mapping: `ResponseStatusException` → `Error` envelope using the exception's status and reason as `code`/`message` (no `details`); `RequestValidationException` → `Error` envelope with `code = "VALIDATION_FAILED"` and `details` built from `errors`; `MethodArgumentNotValidException` → `Error` envelope with `code = "VALIDATION_FAILED"` and `details` built from its `BindingResult` field errors; `ConstraintViolationException` → same `VALIDATION_FAILED` shape with `details` built from its `constraintViolations`; `HttpMessageNotReadableException` → same `VALIDATION_FAILED` shape with a single `details` entry, extracting the failed property name by walking the cause chain (Jackson's `MismatchedInputException`/`InvalidNullException` `path`), falling back to a fieldless generic detail entry if the cause isn't that shape
- [x] 3.3 Generate `traceId` via `UUID.randomUUID().toString()` inside the advice for every mapped response
- [x] 3.4 ~~Assign `error.code = "ASSET_NOT_FOUND"` for the not-found `ResponseStatusException` case specifically~~ — **superseded during code review** (see Section 7): code review found this bakes single-resource domain knowledge into the shared, supposedly resource-agnostic advice. Replaced with a small `ResourceNotFoundException(code, message)` that `getAsset`/`deleteAsset` throw explicitly; the generic `ResponseStatusException` handler now derives `code` purely from `status.name`

## 4. Controller updates

- [x] 4.1 Replace `AssetController.createAsset`'s hand-written blank-tags check (`tags.any { it.isBlank() }` + raw `ResponseStatusException`) with a check that throws `RequestValidationException` carrying a `FieldValidationError(field = "tags", code = "BLANK_VALUE", message = "...")`
- [x] 4.2 Confirm the existing `ResponseStatusException(NOT_FOUND, ...)` call sites in `getAsset` and `deleteAsset` are compatible with the advice's `ASSET_NOT_FOUND` mapping from Task 3.4 without further changes — **superseded**: both call sites now throw `ResourceNotFoundException("ASSET_NOT_FOUND", "asset not found")` instead (see Section 7)

## 5. Tests

- [x] 5.1 Update the existing `rejects blank tag value` test to assert the envelope (`error.code` = `VALIDATION_FAILED`, `error.details` contains a `tags` entry) in addition to the 400 status
- [x] 5.2 Update the existing `rejects request missing required field` test (omitted `name` key → `HttpMessageNotReadableException` path) to assert the envelope and an `error.details` entry, and add a new test for the present-but-empty-string case (`"name": ""` → `MethodArgumentNotValidException` path) asserting the envelope and `error.details` naming `name`
- [x] 5.3 Update the existing `returns 404 when asset id does not exist` and `returns 404 when deleting an asset id that does not exist` tests to assert `error.code` = `ASSET_NOT_FOUND` and the absence of `error.details`
- [x] 5.4 Update the existing `rejects negative page` and `rejects size less than 1` tests to assert the envelope and `error.details` naming the invalid parameter
- [x] 5.5 Run the full test suite against a real MongoDB instance and confirm all pass

## 6. Documentation

- [x] 6.1 Update `docs/tech-stack.md`'s error-response line to describe the adopted envelope shape, replacing "no custom error body shape yet (revisit if inconsistency becomes a problem)"

## 7. Code review fixes

`/ce-code-review` (10 reviewers: correctness, testing, maintainability, project-standards, agent-native, learnings, security, api-contract, reliability, adversarial) found 4 P1s and 6 P2s. All fixed:

- [x] 7.1 **P1** — `Error.details` was optional-but-not-`nullable`, yet the server legitimately emits explicit `null` for non-validation errors (the exact gotcha this repo already documents on the request side). Added `nullable: true`, regenerated.
- [x] 7.2 **P1** — `docs/tech-stack.md` claimed "every 4xx/5xx" gets the envelope, but `MethodArgumentTypeMismatchException` (non-numeric `page`/`size`), wrong HTTP verb, unmapped routes, and genuine 5xx all bypassed it (reproduced live by three independent reviewers). Added `MethodArgumentTypeMismatchException`, `HttpRequestMethodNotSupportedException` (405), `NoResourceFoundException` (404), and a final `Exception` catch-all (500 `INTERNAL_ERROR`) — verified live that each now returns the correctly-enveloped body with its correct status code, not just a 500 catch-all.
- [x] 7.3 **P1** — shared advice hardcoded `ASSET_NOT_FOUND` for any 404, baking single-resource knowledge into cross-cutting infrastructure. Introduced `ResourceNotFoundException(code, message)`; `getAsset`/`deleteAsset` now throw it explicitly with `"ASSET_NOT_FOUND"`, while the generic `ResponseStatusException` handler derives `code` from `status.name` alone.
- [x] 7.4 **P2** — `HttpStatus.valueOf()` could throw for a non-standard status code, crashing the handler itself into an un-enveloped 500. Switched to `HttpStatus.resolve()` with an `INTERNAL_SERVER_ERROR` fallback.
- [x] 7.5 **P2** — 2 of 3 `HttpMessageNotReadableException` sub-branches had zero test coverage. Added tests for a type-mismatched field and for malformed JSON.
- [x] 7.6 **P2** — `ConstraintViolationException`'s `details` ordering was non-deterministic (`Set` iteration order). Sorted by field name; added a test for simultaneous `page`+`size` violations.
- [x] 7.7 **P2** — array-element type mismatches mislabeled `field` as `"request"` instead of the enclosing array field. Fixed by walking `MismatchedInputException.path` backward for the nearest named property; added a test proving `tags` is now correctly attributed.
- [x] 7.8 **P2** — the `MismatchedInputException` branch forwarded Jackson's raw exception message verbatim, leaking internal fully-qualified class names to unauthenticated callers. Replaced with a hardcoded message, matching its sibling branches.
- [x] 7.9 **P3** (advisory) — documented, via a code comment, that Jackson's fail-fast body binding means `HttpMessageNotReadableException` can only ever report one field per response.

Fixing 7.2 initially introduced a regression (a single broad `Exception::class` catch-all reclassified `HttpRequestMethodNotSupportedException`/`NoResourceFoundException` as 500s instead of preserving their real 405/404 status) — caught by re-verifying live against the running app before considering the fix done, then corrected with the two specific handlers.
