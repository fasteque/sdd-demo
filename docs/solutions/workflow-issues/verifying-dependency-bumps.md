---
title: Verifying a dependency or toolchain version bump in this repo
date: 2026-11-18
category: docs/solutions/workflow-issues
module: "build & local stack (build.gradle.kts plugins, Gradle wrapper, compose.yaml / compose.app.yaml images)"
problem_type: workflow_issue
component: development_workflow
severity: medium
applies_when:
  - "Bumping any plugin version in build.gradle.kts (Spring Boot, Kotlin, org.openapi.generator, dependency-management) or the Gradle wrapper"
  - "Bumping the mongo or kong image tag in compose.yaml / compose.app.yaml, especially across a major version"
  - "Running ./gradlew build or ./gradlew test and the build sits in the test phase without output or failure"
symptoms:
  - "./gradlew build compiles and assembles the jar, then hangs silently in the test phase for 10+ minutes when Docker / MongoDB isn't running"
root_cause: incomplete_setup
resolution_type: dependency_update
tags: [dependency-update, gradle, mongodb, docker, openapi-generator, kong, test-setup, upgrade-verification]
related_components: [tooling, testing_framework, infrastructure]
---

# Verifying a dependency or toolchain version bump in this repo

## Context

A routine bump (Spring Boot 4.1.1 -> 4.1.2, Kotlin 2.4.20 -> 2.4.21, openapi-generator 7.25.0 -> 7.27.0, Gradle 9.7.1 -> 9.9.1, kong 3.9.3 -> 3.9.4, mongo 8.0 -> 9.0) turned up three things that the code doesn't make obvious.

1. **The test suite needs a real MongoDB on `localhost:27017`, and nothing starts one for it.** All three test classes are plain `@SpringBootTest`, with no Testcontainers and no `@ServiceConnection`. They connect to the URI in `src/main/resources/application.yaml:5` (`mongodb://localhost:27017/sdddemo`). `spring-boot-docker-compose` is `developmentOnly` (`build.gradle.kts:28`), so it starts `compose.yaml` only for `bootRun`, never for tests. Even when the dev stack is up, `compose.yaml:5` publishes Mongo on a *random* host port (`'27017'`, with no host side), so the tests still can't reach it. With Docker down, the build **does not fail**: compilation and `bootJar` succeed, and the test JVM then sits waiting for Mongo. In this session the build ran for more than 10 minutes with no output before it was diagnosed.
2. **The openapi-generator version decides the shape of the controller contract.** The project relies on specific generated output (see the related docs). A green build shows only that the code compiles. It doesn't show that the generated interfaces and models are unchanged.
3. **The mongo image major version has to match the data already in its volumes.** Mongo opens a data directory only when that directory's `featureCompatibilityVersion` (FCV) is the previous major version or newer. The app stack keeps its data in the named volume `sdd-demo-app_mongodb-data`.

## Guidance

Work through these steps in order:

1. **Start a test Mongo** on the fixed port, using the image tag under test:
   ```bash
   docker run -d --rm --name sdd-test-mongo -p 27017:27017 mongo:<tag>
   ```
2. **Get a green baseline on the current versions first**, so that a later failure is clearly caused by the bump:
   ```bash
   ./gradlew clean build
   cp -r build/generated/openapi <scratch>/gen-before   # snapshot before bumping the generator
   ```
3. **Apply the bumps.** For the wrapper, use `./gradlew wrapper --gradle-version <x>` rather than editing `gradle-wrapper.properties` by hand. Library versions are managed by the Spring Boot BOM, so bumping the Boot plugin moves them too.
4. **Before a mongo major bump, check the FCV of each existing volume** by mounting it in the *old* image:
   ```bash
   docker run -d --rm --name fcvcheck -v sdd-demo-app_mongodb-data:/data/db mongo:<old-tag>
   docker exec fcvcheck mongosh --quiet --eval \
     'db.adminCommand({getParameter:1,featureCompatibilityVersion:1}).featureCompatibilityVersion'
   docker stop fcvcheck
   ```
   If the FCV equals the old major, the new image opens the volume. Mongo never raises the FCV by itself, so rolling back to the old image stays possible until someone runs `setFeatureCompatibilityVersion` by hand.
5. **Rebuild against the new Mongo**, with `--warning-mode all` so that deprecations show up, then diff the generated code:
   ```bash
   docker stop sdd-test-mongo && docker run -d --rm --name sdd-test-mongo -p 27017:27017 mongo:<new-tag>
   ./gradlew clean build --warning-mode all
   diff -r <scratch>/gen-before build/generated/openapi
   ```
   Changes to version banners and doc comments are harmless. Changes to annotations (`@RestController`, `@JsonSetter`, validation annotations), property names or nullability need a careful read against the codegen-gotchas doc.
6. **Smoke-test the real stack**, with Kong in front:
   ```bash
   docker compose -f compose.app.yaml up -d --build
   bash kong/test-gateway.sh        # reads KONG_ASSET_API_KEY from .env, hits http://localhost:8080
   docker compose -f compose.app.yaml down   # no -v: keep the data volume
   ```
7. Then run the required Tier 1 `/ce-code-review`.

## Why This Matters

- If you skip step 1, a broken environment looks like a broken bump, or like a build that's simply slow. The hang gives no error to search for.
- If you skip step 2, a test that was already failing gets blamed on the upgrade.
- If you skip the generated-code diff, a generator change such as the `@RestController` on the generated interface, or `nulls = FAIL`, can change the wire contract while every test still passes. That happens wherever no test covers the changed behavior.
- If you skip the FCV check, a mongo major bump can leave the container unable to open an existing volume. Worse, an FCV that was raised carelessly removes the option to roll back.

## When to Apply

- For every plugin, wrapper or image bump in this repo, even a patch release. The procedure costs a few minutes, while the silent hang alone cost more than 10.
- Steps 1 and 2 also apply whenever `./gradlew test` seems to stall: check `docker ps` before anything else.

## Examples

In the 2026-11-18 bump, verified with the procedure above:

- Baseline on 4.1.1 / 2.4.20 / 7.25.0 / Gradle 9.7.1 against mongo 8.0: 24/24 tests green.
- After the bump, against mongo 9.0.8: 24/24 green. The only difference in `build/generated/openapi` was cosmetic (version banners, plus one KDoc `@param` that is now rendered with backticks instead of HTML entities).
- `sdd-demo-app_mongodb-data` was at FCV `8.0`, so mongo 9.0 opened it.
- `kong/test-gateway.sh` passed 24/24 on kong 3.9.4.
- Gradle 9.9.1 with `--warning-mode all` reports a `Configuration.setVisible(boolean)` deprecation, scheduled for removal in Gradle 11. A `--warning-mode fail --stacktrace` run traced it to `OpenApiGeneratorPlugin.apply` inside the openapi-generator plugin, not to this build script. Before moving to Gradle 11, re-check whether a newer generator plugin has fixed it.

## Related

- [openapi-spec-first-codegen-gotchas.md](../tooling-decisions/openapi-spec-first-codegen-gotchas.md): the generated-output behaviors that step 5's diff protects.
- [spring-controller-advice-precedence-and-exception-mapping.md](../tooling-decisions/spring-controller-advice-precedence-and-exception-mapping.md): the generator-emitted `DefaultExceptionHandler`, and exception types that can shift with a Spring Boot or generator bump.
- `docs/tech-stack.md`: the approved dependency list. A version bump adds no coordinates, so it never needs approval, but a bump that changes the Spring Boot minor version should update the stack line there.
