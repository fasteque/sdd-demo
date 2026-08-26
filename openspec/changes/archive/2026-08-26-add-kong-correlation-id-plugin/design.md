## Context

`kong/kong.yml` is a DB-less declarative Kong config (`_format_version: "3.0"`) with a single service (`app`) and a single catch-all route. See proposal.md - Why for motivation.

The existing `api-gateway` spec has a requirement, "No Traffic Policies Applied at the Gateway," that blanket-bans Kong plugins. This design deviates from that prior decision — see Decisions below for rationale.

## Goals / Non-Goals

**Goals:**
- Attach a correlation ID to every request/response passing through the gateway, regardless of route.
- Keep the deviation from the "no plugins" rule as narrow as possible: only `correlation-id`, nothing else.

**Non-Goals:**
- No other Kong plugins (auth, rate limiting, transformation, logging).
- No app-level changes to read, log, or propagate the header — the app doesn't need to know about it.
- No change to routing, services, or ports in `kong/kong.yml`.

## Decisions

**Global plugin block vs. service/route-scoped plugin.** The proposal specifies global scope. In Kong's declarative config, this means a top-level `plugins:` array (as a sibling of `services:`), not a `plugins:` array nested under the `app` service or its route. A service- or route-scoped plugin would only fire for requests matching that service/route; since the gateway currently has exactly one catch-all route, the practical difference today is small, but global scope is what was asked for and is also more robust if additional services/routes are added later without remembering to re-attach the plugin.

**Header name: `Request-Id`, not the originally requested `Kong-Request-ID`.** The `sdd-demo-api-contracts` store's `api-naming-conventions` spec requires platform-wide cross-cutting headers to use Train-Case with no service-specific prefix, and its own example is `Request-Id`. `Kong-Request-ID` violates both the prefix and casing rules. Confirmed with the user to use `Request-Id` instead. This is a config value (`header_name: Request-Id`), not a code change.

**Deviation from the existing "no plugins" requirement.** The current `api-gateway` spec was written to keep the gateway a pure proxy. This change narrows that requirement rather than removing it outright: the MODIFIED requirement in `specs/api-gateway/spec.md` still bans every other plugin, so the "pure proxy" intent is preserved except for this one, narrowly-scoped exception. Rationale: correlation IDs are cross-cutting infrastructure, not a traffic policy in the sense the original requirement was guarding against (auth, rate limiting, transformation, logging) — but since the letter of the old requirement did cover "or otherwise," it needs an explicit spec change rather than being treated as compatible.

**`echo_downstream: true`.** Required so the client can read the same correlation ID Kong generated (or the one it sent), enabling client-side log correlation without needing the app to echo it. Kong plugin default; alternative would be `false`, which was considered but rejected since the app doesn't handle this header at all and the client would otherwise have no way to learn the generated ID.

## Risks / Trade-offs

- **Narrows a previously strict "no plugins" guarantee** → mitigated by scoping the MODIFIED requirement to name `correlation-id` as the sole exception, so any future plugin addition still requires its own spec change.
- **Global scope affects any future service/route added to `kong/kong.yml`**, not just `app` → this is the intended behavior (per proposal), and is called out as a scenario in the spec so it's tested for, not accidental.
