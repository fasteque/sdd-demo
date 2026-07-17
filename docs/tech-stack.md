# Tech Stack

Kotlin, Spring Boot 4.1, Spring Data MongoDB, Gradle
API conventions: RESTful endpoints, JSON request/response bodies
API contracts: HTTP endpoints are OpenAPI-spec-first. `openapi/openapi.yaml` is authoritative for wire contracts (request/response shapes, status codes) and currently covers all HTTP endpoints in this repo. Workflow: edit the spec first, run `./gradlew openApiGenerate` to regenerate server interfaces/models (output under `build/generated/openapi`, gitignored — never hand-edited or committed), then implement/update the controller against the generated interface. The implementing class must still carry its own `@RestController` annotation (component-scanning discovers beans via annotations declared on the class itself, not via annotations on implemented interfaces) even though the generated interface also carries `@RestController` (needed separately for correct handler-mapping detection when the bean is wrapped in a dynamic proxy). Don't hand-declare request/response DTOs for OAS-covered endpoints — implement the generated models instead. A schema property that is optional but may still be sent as JSON `null` (e.g. an array field a client might omit or null out) needs `nullable: true`, or the generator emits `@JsonSetter(nulls = Nulls.FAIL)` and silently rejects a previously-accepted `null` value with a 400.
Style: idiomatic Kotlin, constructor injection, no unnecessary abstractions
Testing: JUnit 5 + Spring Boot Test + MockMvc, tests co-located under src/test/kotlin mirroring main package structure
Error responses: use ResponseStatusException with a clear reason message for 4xx errors; no custom error body shape yet (revisit if inconsistency becomes a problem)

This is a personal learning sandbox to experiment with spec-driven development — not a production system.

## Approved Dependencies

The following `groupId:artifactId` combinations are pre-approved and can be added without asking:

- `org.springframework.boot:spring-boot-starter-web`
- `org.springframework.boot:spring-boot-starter-webmvc`
- `org.springframework.boot:spring-boot-starter-validation`
- `org.springframework.boot:spring-boot-starter-data-mongodb`
- `org.springframework.boot:spring-boot-starter-test`
- `org.springframework.boot:spring-boot-starter-data-mongodb-test`
- `org.springframework.boot:spring-boot-starter-webmvc-test`
- `org.jetbrains.kotlin:kotlin-reflect`
- `tools.jackson.module:jackson-module-kotlin`
- `org.jetbrains.kotlin:kotlin-test-junit5`
- `org.junit.platform:junit-platform-launcher`

**Any dependency not on this list requires explicit confirmation before being added** — propose it in the change's
`design.md` first (what it's for, why an existing approved dependency or plain Kotlin/JVM stdlib code can't do the job),
and wait for approval before adding it to `build.gradle.kts`. This applies even for a single method's worth of
functionality — prefer a few lines of hand-written code over a new dependency for narrow, one-off needs.
