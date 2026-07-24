---
title: Spring @ControllerAdvice precedence, real exception types, and catch-all safety in an OAS-first codebase
date: 2026-07-24
category: docs/solutions/tooling-decisions
module: "API layer / HTTP error handling (Spring Boot @RestControllerAdvice, exception-type resolution, org.openapi.generator interaction)"
problem_type: tooling_decision
component: tooling
severity: medium
applies_when:
  - "Introducing a hand-written @RestControllerAdvice/@ExceptionHandler mechanism in a codebase where org.openapi.generator (kotlin-spring) also emits its own @ControllerAdvice (e.g. a generated DefaultExceptionHandler) that may claim the same exception type"
  - "Mapping validation-failure exception types to handlers in a Spring Boot 4.1/Spring Framework 7 + Jackson 3 (tools.jackson.*) + jackson-module-kotlin stack, where the real exception type is easy to assume incorrectly from framework-version documentation alone"
  - "Adding a broad @ExceptionHandler(Exception::class) catch-all alongside more specific Spring MVC exception types (e.g. HttpRequestMethodNotSupportedException, NoResourceFoundException) that must keep their own status codes"
  - "Distinguishing HttpMessageNotReadableException's different .cause types (KotlinInvalidNullException vs MismatchedInputException) to attribute a field-level error correctly, including for errors inside array elements"
related_components: [testing_framework, development_workflow]
tags: [spring-boot, controller-advice, exception-handling, spring-order, jackson, openapi-generator, kotlin-spring, bean-validation]
---

# Spring @ControllerAdvice precedence, real exception types, and catch-all safety in an OAS-first codebase

## Context

Adopting a platform-wide `api-error-responses` convention (a structured `{"error":{"code","message","traceId","details"?}}` envelope for every 4xx/5xx response) required a single shared `@RestControllerAdvice` (`src/main/kotlin/ch/fasteque/sdd_demo/ApiErrorHandling.kt`) across this Kotlin/Spring Boot 4.1 API. Three non-obvious things surfaced while building and code-reviewing it, all confirmed empirically — by running the app and curling it, or by reading decompiled generated code and jar sources — not by reasoning from framework documentation alone.

## Guidance

### 1. A hand-written `@ControllerAdvice` can silently lose to openapi-generator's own generated one without an explicit `@Order`

`build/generated/openapi/src/main/kotlin/ch/fasteque/sdd_demo/generated/api/Exceptions.kt` (gitignored, regenerated on every `./gradlew openApiGenerate`) declares its own `@ControllerAdvice`:

```kotlin
@Configuration("ch.fasteque.sdd_demo.generated.api.DefaultExceptionHandler")
@ControllerAdvice
class DefaultExceptionHandler {
    @ExceptionHandler(value = [ConstraintViolationException::class])
    fun onConstraintViolation(ex: ConstraintViolationException, response: HttpServletResponse): Unit =
        response.sendError(HttpStatus.BAD_REQUEST.value(), ex.constraintViolations.joinToString(", ") { it.message })
}
```

This class carries no `@Order`, so it resolves at Spring's default (lowest) precedence. The hand-written envelope advice also needs to handle `ConstraintViolationException` (see finding 2 below), so both advices register a handler for the same exception type. Without an explicit `@Order`, which one wins is a fact that lives in neither file's diff — it depends on Spring's default `@ControllerAdvice` bean-ordering behavior.

The fix, in `ApiErrorHandling.kt`:

```kotlin
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ApiExceptionHandler {
    @ExceptionHandler(ConstraintViolationException::class)
    fun onConstraintViolation(ex: ConstraintViolationException): ResponseEntity<ErrorResponse> = /* ... */
}
```

Confirmed empirically, not just by reading Spring's ordering contract (`ControllerAdviceBean.findAnnotatedBeans` sorts matching advice beans via `OrderComparator.sort` before picking the first whose `@ExceptionHandler` matches): curling `GET /assets?page=-1` against a live running instance returns the hand-written envelope, not the generated advice's plain `sendError` body. Removing `@Order(Ordered.HIGHEST_PRECEDENCE)` as a deliberate check and re-curling the same request confirmed the response degraded to the generated advice's bare body — this annotation is load-bearing for exactly this one exception type where both advices register a handler.

### 2. The real exception type behind each validation path is not what framework-version assumptions predict

Verified by starting the app with `--logging.level.org.springframework.web=DEBUG` and reading the actual `Resolved [...]` log lines from real curl requests (Spring Boot 4.1, Spring Framework 7, Jackson 3 via the `tools.jackson.*` package family — the Jackson-3-era rename from `com.fasterxml.jackson.*`, reflected in this project's `tools.jackson.core:jackson-databind` / `tools.jackson.module:jackson-module-kotlin` dependencies):

| Request shape | Real exception type | Handler |
|---|---|---|
| `@Min`-annotated query param violation, e.g. `GET /assets?page=-1` | `jakarta.validation.ConstraintViolationException` | `onConstraintViolation` |
| Non-numeric query param, e.g. `GET /assets?page=abc` | `org.springframework.web.method.annotation.MethodArgumentTypeMismatchException` | `onMethodArgumentTypeMismatch` |
| JSON key entirely absent, targeting a non-nullable Kotlin constructor param with no default | `HttpMessageNotReadableException` whose `.cause` is `tools.jackson.module.kotlin.KotlinInvalidNullException` | `onHttpMessageNotReadable`, `is KotlinInvalidNullException` branch |
| JSON field present but failing its own Bean Validation constraint, e.g. `"name": ""` failing `@Size(min=1)` | `org.springframework.web.bind.MethodArgumentNotValidException` | `onMethodArgumentNotValid` |
| JSON value of the wrong structural type (e.g. a nested object where a `String` was expected) | `HttpMessageNotReadableException` whose `.cause` is `tools.jackson.databind.exc.MismatchedInputException` | `onHttpMessageNotReadable`, `is MismatchedInputException` branch |

The `@Min` query-param row was initially assumed to be `HandlerMethodValidationException` (the mechanism Spring Framework 6.1+ introduced for some method-parameter validation paths) — wrong for this codebase's shape (a `@Validated`-annotated generated interface with `@Min`-annotated primitive `Int` params). It was corrected by the debug log line `jakarta.validation.ConstraintViolationException: listAssets.page: must be greater than or equal to 0` from a real request, not by re-reading framework changelog text.

The two `HttpMessageNotReadableException` rows look identical at the top level but are handled by different branches of the same method, distinguished by `.cause`:

```kotlin
@ExceptionHandler(HttpMessageNotReadableException::class)
fun onHttpMessageNotReadable(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
	val detail = when (val cause = ex.cause) {
		is KotlinInvalidNullException ->
			ErrorDetail(field = cause.kotlinPropertyName, code = "REQUIRED", message = "must not be null")
		is MismatchedInputException -> {
			val field = cause.path.asReversed().firstOrNull { it.propertyName != null }?.propertyName ?: "request"
			ErrorDetail(field = field, code = "INVALID_VALUE", message = "invalid value for field '$field'")
		}
		else -> ErrorDetail(field = "request", code = "MALFORMED_REQUEST", message = "malformed request body")
	}
	return validationFailed(listOf(detail))
}
```

`KotlinInvalidNullException` (confirmed by reading its source directly from the `jackson-module-kotlin` dependency's sources jar — class `KotlinInvalidNullException` in package `tools.jackson.module.kotlin`, the Jackson-3-era rename of the Jackson-2-era `com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException`) extends `tools.jackson.databind.exc.InvalidNullException` and exposes `getKotlinPropertyName(): String` directly — no path-walking needed. This fires because Jackson fails to construct the target object entirely, before `@Valid`/Bean Validation ever runs — so a missing `"name"` key and an empty `"name": ""` value hit genuinely different exception types despite both being colloquially "an invalid name."

`MismatchedInputException` required walking `.path` (a `List<tools.jackson.core.JacksonException.Reference>`) whose getter is `getPropertyName()`, **not** `getFieldName()` — an initial wrong assumption caught by reading the `JacksonException` class directly from the `jackson-core` dependency's sources jar (package `tools.jackson.core`). It also required walking that path list **backward** (`.asReversed().firstOrNull { it.propertyName != null }`) rather than taking the last element outright, because an error inside an array element has a null-`propertyName` index-reference as the path's last segment. Naively taking `.lastOrNull()?.propertyName` would mislabel the field as the generic `"request"` fallback instead of the enclosing array's actual field name — e.g. `{"tags":["ok",{"nested":"object"}]}` correctly attributes the error to `"tags"`, not `"request"`, only with the backward walk.

### 3. A broad `@ExceptionHandler(Exception::class)` catch-all is not safe by itself

To make "every exception path gets the structured envelope" actually true — closing a gap where `MethodArgumentTypeMismatchException`, wrong HTTP verbs, and unmapped routes fell through to Spring Boot's undocumented default error body — a broad `@ExceptionHandler(Exception::class)` handler was added as a last resort. Adding it **alone** introduced a regression, caught only by re-curling previously-correct paths against a live running instance:

- `PUT /assets` (wrong HTTP verb) started returning `500 INTERNAL_ERROR` instead of the correct `405 Method Not Allowed`, because `org.springframework.web.HttpRequestMethodNotSupportedException` had no more specific registered handler, and Spring's `ExceptionHandlerExceptionResolver` picks the most specific matching `@ExceptionHandler` across all `@ControllerAdvice` beans — `Exception` is a supertype of everything, so it resolved to the broad catch-all.
- An unmapped route similarly started returning `500` instead of the correct `404 Not Found`, because `org.springframework.web.servlet.resource.NoResourceFoundException` also had no specific handler.

The fix: targeted handlers for both, declared before the generic catch-all:

```kotlin
@ExceptionHandler(HttpRequestMethodNotSupportedException::class)
fun onMethodNotSupported(ex: HttpRequestMethodNotSupportedException): ResponseEntity<ErrorResponse> =
	errorResponse(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.message ?: "method not allowed")

@ExceptionHandler(NoResourceFoundException::class)
fun onNoResourceFound(ex: NoResourceFoundException): ResponseEntity<ErrorResponse> =
	errorResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", "no route matches this request")

@ExceptionHandler(Exception::class)
fun onUnexpectedException(ex: Exception): ResponseEntity<ErrorResponse> =
	errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "an unexpected error occurred")
```

Verified live afterward: `curl -X PUT http://localhost:8080/assets` returns `405` with `{"error":{"code":"METHOD_NOT_ALLOWED",...}}`; an unmapped route returns `404` with `{"error":{"code":"NOT_FOUND",...}}`.

## Why This Matters

- **Finding 1** — a missing `@Order` could silently let the generated advice win depending on Spring's default bean-ordering behavior, not any explicit intent in either file. The hand-written advice looks complete and correct on its own; which body a client actually receives depends on an ordering fact that lives in neither file's diff.
- **Finding 2** — assuming an exception type from framework-version documentation alone leads to a handler registered for the wrong type. That handler then never matches, and the request silently falls through to whatever less-specific handler (or none) does match, with no compile-time or startup-time signal that anything is missing. The two `HttpMessageNotReadableException` sub-cases compound this: a handler that inspects only `ex` and not `ex.cause` would silently conflate "missing required field" and "wrong type for field" into the same generic response.
- **Finding 3** — the instinct "the more exception types I catch, the safer" is actively wrong once a broad `Exception::class` catch-all is involved, because Spring's handler resolution is specificity-based, not registration-order-based. Adding the catch-all can silently steal previously-correct, more-specific status codes (405, 404) and replace them with an incorrect 500, for any exception type that doesn't yet have its own handler. This is the most dangerous of the three findings because it looks like an unqualified improvement while quietly downgrading status-code correctness on paths that were already working.

## When to Apply

- Any time a hand-written `@ControllerAdvice`/`@ExceptionHandler` mechanism is introduced in an OAS-first codebase using this generator config: check the generated `Exceptions.kt` (or equivalent, under `build/generated/openapi/.../generated/api/`) for a competing `@ControllerAdvice`, and add an explicit `@Order` on the hand-written advice if one exists.
- Confirm each target exception's real type by running the app and triggering the path with debug logging (`--logging.level.org.springframework.web=DEBUG`, reading the `Resolved [...]` line) rather than assuming from framework-version documentation or analogy to a similar-looking validation path.
- After adding any broad catch-all handler, re-verify **every** previously-correct status code path live — not just the newly-fixed ones — to catch a specificity-based regression where a more-specific exception type that lacked its own handler starts resolving to the catch-all instead of its natural status code.

## Examples

`ApiErrorHandling.kt`'s `@RestControllerAdvice` + `@Order(Ordered.HIGHEST_PRECEDENCE)`, added because the generated `DefaultExceptionHandler` registers its own unordered `@ExceptionHandler(ConstraintViolationException::class)` — confirmed by curling `GET /assets?page=-1` live and observing the hand-written envelope win only with `@Order` present. Paired with the exception-type table above (each row confirmed via a debug-log `Resolved [...]` line against a live instance, not assumed), and the `HttpRequestMethodNotSupportedException`/`NoResourceFoundException` handlers added ahead of the `Exception::class` catch-all after discovering live that the catch-all alone downgraded `PUT /assets` from a correct `405` and an unmapped route from a correct `404` to an incorrect `500`.

## Related

- `docs/solutions/tooling-decisions/openapi-spec-first-codegen-gotchas.md` — the sibling doc for this same generator/config surface. That doc's seven gotchas are about surprises in the generator's *output* (renamed properties, missing `nullable`, missing per-item validation, wrong return types, import collisions), discovered by reading generated source. This doc is about *runtime* behavior — `@ControllerAdvice` bean precedence and exception-type resolution — a different Spring mechanism (`HandlerExceptionResolver`/advice ordering, not component-scan or Bean Validation annotation emission). Both share the same underlying discipline: read the generated source directly and verify empirically against a running app, since `build/generated/openapi` is gitignored and invisible in a `git diff`.
