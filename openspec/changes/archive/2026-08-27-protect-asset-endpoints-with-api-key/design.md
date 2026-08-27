## Context

See proposal.md - Why. Current `kong/kong.yml` (DB-less, `_format_version: "3.0"`) defines one `app` service and one catch-all route matching `/`. Kong runs as the `kong` service in `compose.app.yaml`, with `kong/kong.yml` mounted read-only; the app service's port is not published to the host (only Kong's is). Kong version is `3.9.3`. `openapi/openapi.yaml` defines `/health`, `/assets`, `/assets/{id}` — this change adds no new HTTP paths and does not touch the OpenAPI spec, since authentication is enforced entirely at the gateway layer, in front of the wire contract.

## Goals / Non-Goals

**Goals:**
- Require a valid API key on `/assets` and `/assets/{id}`, all methods.
- Keep `/health` reachable with no key, unchanged.
- Keep the real key value out of git history entirely (not just out of the working tree).

**Non-Goals:**
- No key rotation/expiry mechanism, no multiple keys/consumers.
- No app-level (Spring) auth — enforcement is 100% at Kong.
- No rewriting Kong's 401 body to match the platform error envelope (accepted deviation, see proposal.md - Non-goals).
- No catch-all fallback route for paths outside `/health` and the asset routes — see the routing decision below.

## Decisions

**Split the single catch-all route into two routes, with no fallback catch-all.** Kong plugins are scoped to services/routes/consumers, not to individual paths within a route — to protect only the asset paths while leaving `/health` open, `key-auth` must be attached to a route that covers only `/assets*`. Alternative considered: keep one catch-all route and use Kong's `key-auth` plugin with a `paths` config to conditionally skip auth — Kong's `key-auth` plugin has no such conditional-skip option, so this isn't viable without a bespoke Lua/response-transformer script, which is more complexity for no benefit here. Two declarative routes is the standard Kong pattern for "some paths need policy X, others don't."
  - This drops the previous "true catch-all" behavior: a path matching neither route (there are none today — `/health` and the asset paths are the app's entire surface per `openapi/openapi.yaml`) now gets Kong's own 404 instead of reaching the app for its structured `NoResourceFoundException` 404. Confirmed with the user as an accepted trade-off rather than adding a third, unauthenticated, no-plugin fallback route to preserve full proxy coverage. The `api-error-responses` delta spec's scope clarification covers this case too, not just gateway-rejected 401s.

**One Kong consumer, one key-auth credential.** This is a single-key gate, not a multi-tenant scheme (see proposal.md - Non-goals). A consumer named `asset-client` with one `keyauth_credentials` entry is the minimal Kong shape that satisfies the `key-auth` plugin's requirements.

**Secret handling: startup `sed` substitution against a committed template + gitignored `.env`.** Kong's vault-reference feature (`{vault://env/<NAME>}`) only resolves against schema fields explicitly marked `referenceable = true`, and `key-auth`'s `keyauth_credentials[].key` field (`kong/plugins/key-auth/daos.lua`) is not one of them — confirmed empirically: a `{vault://env/...}` reference in that field is stored and matched as a literal string, not resolved, so it does not satisfy "no hardcoded secret" (the literal reference string itself would work as the API key). Instead: `kong/kong.yml` (committed) holds the placeholder `__KONG_ASSET_API_KEY__` in place of the real key and is mounted into the container as `/etc/kong/kong.yml.template`. The `kong` service's `command` is overridden to run `sed "s#__KONG_ASSET_API_KEY__#$KONG_ASSET_API_KEY#" /etc/kong/kong.yml.template > /etc/kong/kong.yml` before handing off to the image's normal `docker-entrypoint.sh kong docker-start`, rendering the real key into a file that only exists in the container's ephemeral filesystem, never on a mounted/committed path. `$KONG_ASSET_API_KEY` reaches that shell via `env_file: .env` on the `kong` service; `.env` is gitignored. The compose YAML itself escapes the variable as `$$KONG_ASSET_API_KEY` so Compose's own `${VAR}`/`$VAR` interpolation doesn't resolve it at `compose config` time (which would bake the real value into the rendered command) — the container's shell resolves it from its own runtime environment instead.
  - A `.env.example` file (checked in, no real value) documents the required variable name for anyone standing up the stack locally.

**Plugin scope: `key-auth` on the asset route only, not global.** Mirrors the proposal's explicit requirement that `/health` stay open; a global `key-auth` plugin would apply to every route including `/health`.

## Risks / Trade-offs

- **Kong's 401 body doesn't match the platform error envelope** → Mitigated by explicitly scoping `api-error-responses` (this change's delta spec) to app-originated responses only; documented as an accepted, deliberate deviation rather than silently inconsistent behavior.
- **Route split could accidentally leave a path unprotected or double-covered** → Mitigated by the modified `api-gateway` requirement's scenarios, which pin down exactly which routes exist and what each does; manual verification (`curl` without a key against each path) during implementation before marking tasks done.
- **`.env` file could be committed by accident** → Mitigated by adding `.env` to `.gitignore` in the same change, plus checking `git status` before any commit in this change per standard repo practice.

## Migration Plan

This only affects the containerized dev stack (`compose.app.yaml`), which isn't a deployed environment — there's no running instance to migrate. Anyone currently using `docker compose -f compose.app.yaml up` needs to create a local `.env` (from `.env.example`) before asset requests will succeed; this is a one-time local setup step, documented in the relevant "how to run the app" doc.
