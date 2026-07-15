# Tech Stack

Kotlin, Spring Boot 4.1, Spring Data MongoDB, Gradle
API conventions: RESTful endpoints, JSON request/response bodies
Style: idiomatic Kotlin, constructor injection, no unnecessary abstractions
Testing: JUnit 5 + Spring Boot Test + MockMvc, tests co-located under src/test/kotlin mirroring main package structure
Error responses: use ResponseStatusException with a clear reason message for 4xx errors; no custom error body shape yet (revisit if inconsistency becomes a problem)

This is a personal learning sandbox to experiment with spec-driven development — not a production system.
