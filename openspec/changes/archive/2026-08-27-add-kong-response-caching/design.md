## Context

`kong/kong.yml` is a DB-less declarative config; `compose.app.yaml` runs exactly one Kong node with `KONG_ADMIN_LISTEN: 'off'` (no Admin API, so no cache-purge endpoint is reachable). The `assets` route already carries `key-auth` and `rate-limiting` plugins. See proposal.md - Why.

## Goals / Non-Goals

**Goals:**
- Cache `GET`/`HEAD` responses from the asset routes for 30 seconds, using Kong's built-in `proxy-cache` plugin.
- Make cache hits observably visible on the response, per the user's explicit ask.
- Keep the change confined to `kong/kong.yml` and the `api-gateway` spec -- no app code changes.

**Non-Goals:**
- Cache invalidation on write (see proposal.md - Non-goals; no Admin API available for a purge call without a separate, out-of-scope decision to enable it).
- A shared/distributed cache (`strategy: redis` or similar) -- see Decisions below.
- Renaming Kong's fixed `X-Cache-Status` header to satisfy the naming-convention store -- see Decisions below.

## Decisions

**Plugin scope: route-level, not global.** Attach `proxy-cache` under the `assets` route's `plugins` list, alongside `key-auth` and `rate-limiting` -- same established pattern as both prior gateway-policy changes. Keeps `/health` uncached without a route-exclusion mechanism.

**`strategy: memory`.** Kong's `proxy-cache` plugin supports `memory` (in-process, per-node) or a database-backed strategy for cross-node sharing. This stack runs a single Kong node (`compose.app.yaml`), so per-node memory is sufficient and avoids a new datastore dependency -- the same reasoning already applied to the rate-limiting change's `policy: local` choice, reused here rather than re-litigated (per this project's rule to reuse an existing pattern unless there's a stated reason not to).

**`cache_ttl: 30`.** User-specified "short TTL" for demo purposes; 30 seconds is long enough to reliably observe a cache hit on a couple of quick repeated requests, short enough that staleness never persists long enough to look like a bug.

**`request_method: [GET, HEAD]`, `response_code: [200]`.** Restricts caching to safe, idempotent reads that succeeded -- matches the plugin's own defaults for method (`GET`, `HEAD`) but narrows the default response-code list (`200, 301, 404`) down to just `200`, since caching a `404` would mean a since-created asset stays invisible for up to 30 seconds after creation, which is a worse and less obvious staleness failure mode than caching only confirmed-successful reads.

**`X-Cache-Status` header naming deviation -- accepted, not renamed.** Verified against the plugin's own schema (`kong/plugins/proxy-cache/schema.lua` inside the `kong:3.9.3` image): the diagnostic header keys (`X-Cache-Status`, `X-Cache-Key`) are literal field names in `response_headers`, boolean-toggled on/off, not renameable. This does not conform to the linked `sdd-demo-api-contracts` store's `api-naming-conventions` spec ("Platform-wide cross-cutting HTTP headers SHALL use standard Train-Case naming without service-specific `X-` prefixes"). Two options were weighed: (a) accept the plugin-fixed name as a documented exception, or (b) add a second plugin (`response-transformer`, also stock/no new dependency) to rewrite it to a Train-Case name post-hoc. Chosen: (a), confirmed explicitly with the user rather than assumed -- adding a second plugin purely to satisfy a naming rule on a demo-scoped feature is disproportionate complexity, and the deviation is fully attributable to adopting a stock, unmodified Kong plugin rather than a header this platform is choosing to design. Recorded here so it doesn't get mistaken for an oversight in a later cross-service naming audit (see `docs/solutions/workflow-issues/catch-cross-store-convention-and-spec-conflicts-in-openspec-proposals.md` for why this class of conflict matters).

**No invalidation on write.** Considered enabling the Kong Admin API to allow a purge call after `POST`/`DELETE`, but that reopens a security surface (an unauthenticated management API) that a prior change (`add-kong-gateway`) deliberately closed, for a benefit (removing up to 30 seconds of read-after-write staleness) that doesn't justify reopening it in a demo stack. Accepted as a Non-Goal instead (see proposal.md).

## Risks / Trade-offs

- **[Risk]** A client can read stale data for up to 30 seconds after creating or deleting an asset, including its own just-written change → **Mitigation:** accepted and documented (see Non-Goals); the TTL is short by design specifically to bound this window.
- **[Risk]** `strategy: memory` means the cache is per-Kong-node and resets on restart, same category of limitation already accepted for `policy: local` in the rate-limiting change → **Mitigation:** consistent, already-precedented trade-off for this single-node demo stack; no new risk class introduced.
- **[Risk]** `X-Cache-Status` violates the platform's header-naming convention → **Mitigation:** explicitly accepted and recorded above, not silently shipped.
- **[Risk]** The cache key is not varied by consumer/API key (`vary_headers` left unset), so if a second consumer is ever registered, both would share cached responses for the same path within the TTL window → **Mitigation:** acceptable today (single registered consumer, `asset-client`); revisit `vary_headers` if a second consumer is added.
