## Context

See `proposal.md` - Why/What Changes for motivation and scope. Relevant current state:

- `compose.app.yaml` today defines two services: `app` (built from the repo's `Dockerfile`, publishes `8080:8080`, talks to `mongodb`) and `mongodb` (not published to host).
- `README.md` documents the containerized-run mode as reachable at `http://localhost:8080`.
- This is a local-dev-only POC stack; there is no production deployment and no existing gateway/proxy pattern in the repo to reuse or deviate from.
- Per `docs/tech-stack.md`, the app's wire contract (`openapi/openapi.yaml`) is unaffected by this change — Kong proxies bytes, it doesn't touch request/response shapes.

## Goals / Non-Goals

**Goals:**
- Add Kong Gateway OSS as the single entrypoint for `compose.app.yaml`, running DB-less with a checked-in declarative config.
- Keep the change minimal and reversible: one Kong service, one catch-all route, zero plugins (per proposal's Non-goals).
- Preserve the existing local-dev URL (`http://localhost:8080`) so `README.md`'s documented URL for this mode doesn't need to change.

**Non-Goals:**
- Anything already listed in `proposal.md` - Non-goals (plugins, path-based routing, Admin API exposure, TLS, changes to `compose.yaml` or the OpenAPI contract).
- Pinning or vetting a specific Kong OSS patch version beyond "a current stable 3.x tag" — the implementer should check Docker Hub for the latest stable `kong:3.x` tag at implementation time rather than using `latest` (reproducibility).

## Decisions

**1. Config file location: `kong/kong.yml`, mounted read-only.**
Namespacing it under `kong/` (rather than dropping `kong.yml` at repo root) keeps gateway-specific files grouped as the stack gains more infra pieces later, and mirrors how `Dockerfile` is app-specific at root while compose files describe orchestration. The file is bind-mounted read-only into the container at `/etc/kong/kong.yml`; Kong is told to use it via `KONG_DATABASE=off` and `KONG_DECLARATIVE_CONFIG=/etc/kong/kong.yml`.

**2. Host port mapping: Kong's proxy port (`8000`) is published as `8080:8000`.**
Alternative considered: publish Kong on a new port (e.g. `8000:8000`) and update the README. Rejected — remapping to the existing `8080` host port keeps `http://localhost:8080` valid with zero doc changes to the *URL* itself (the README still needs a short note that requests now pass through Kong, per proposal's Impact section). The `app` service's own `8080` container port is no longer published to the host at all — it's reachable only from Kong over the compose network (`http://app:8080`), matching the proposal's requirement that the app not be directly reachable.

**3. Kong Admin API is disabled entirely (`KONG_ADMIN_LISTEN=off`), not just unpublished.**
Alternative: leave the Admin API on its default internal listen and simply not publish it to the host. Rejected in favor of turning it off outright — there's no use for it in this DB-less, no-plugins POC, and disabling it removes the surface entirely rather than relying on "we just didn't map the port" as the only safeguard.

**4. Catch-all route via `paths: ["/"]` with `strip_path: false`.**
Kong requires at least one matching rule on a route. `paths: ["/"]` matches every request (all HTTP paths start with `/`), giving the "one route, no path-based rules beyond a catch-all" behavior the proposal calls for. `strip_path: false` is required so the path Kong forwards to the app is unchanged from what the client sent — otherwise Kong's default behavior strips the matched prefix and the app would see a different path than the client requested, silently breaking every route.

**5. `depends_on: [app]` (plain form, no health condition), matching the existing `app: depends_on: [mongodb]` pattern.**
The current file doesn't use `condition: service_healthy` anywhere (no healthchecks are defined on `app` or `mongodb`), so adding one only for Kong would be an inconsistent, unrequested addition. Kong's own retry/connect-on-demand behavior at the proxy layer is sufficient for a local dev stack where both containers start together.

## Risks / Trade-offs

- **[Risk]** A brand-new `kong` service between client and app is one more moving part to debug when something doesn't respond in local dev. → **Mitigation**: DB-less mode plus a single static `kong.yml` keeps the failure surface small (no Admin API, no database, no plugins); `docker compose -f compose.app.yaml logs kong` is the first troubleshooting step, worth a line in the README.
- **[Risk]** Pinning to "whatever is the current stable `kong:3.x` tag" at implementation time means the exact version isn't decided here. → **Mitigation**: acceptable for a POC; the tag will be visible and reviewable in the actual `compose.app.yaml` diff.
- **[Trade-off]** Disabling the Admin API (`KONG_ADMIN_LISTEN=off`) means there's no live introspection of Kong's runtime config if something looks wrong — the only source of truth is the static `kong/kong.yml` file. Acceptable given DB-less mode already makes the file authoritative.

## Migration Plan

No production deployment exists for this POC, so there's no live traffic or rollback plan beyond local dev:
1. Land `kong/kong.yml` and the `compose.app.yaml` changes together (Kong config with no working compose service is untestable, and vice versa).
2. Update the README's containerized-run section to mention traffic now passes through Kong, and to remove the app's now-inaccurate direct-port note.
3. Verify locally with `docker compose -f compose.app.yaml up --build` and a request to `http://localhost:8080` before merging.
Rollback is `git revert` — no data migration, no persisted state in Kong (DB-less).
